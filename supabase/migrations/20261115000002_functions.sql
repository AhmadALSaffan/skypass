-- Server-side logic. Prices, seat holds and PNRs are always computed here, never trusted from the app.

-- Fare multiplier per cabin (board: Economy 1, Premium 1.65, Business 3.4, First 5.8).
create or replace function public.cabin_factor(p_cabin text)
returns numeric
language sql
immutable
set search_path = ''
as $$
    select case p_cabin
        when 'economy' then 1.0
        when 'premium' then 1.65
        when 'business' then 3.4
        when 'first' then 5.8
    end::numeric;
$$;

-- Seats sold to other travellers on every departure (the board's seat map).
create or replace function public.preassigned_seats()
returns text[]
language sql
immutable
set search_path = ''
as $$
    select array['12A', '12F', '13B', '13C', '14A', '14E', '15D', '15F', '16B', '16C', '16E'];
$$;

-- ---------------------------------------------------------------------------------------------
-- Search
-- ---------------------------------------------------------------------------------------------

-- Flights on a route and day, priced for the cabin. Local departure/arrival times are in each
-- airport's own time zone. "Best value" is the cheapest nonstop.
create or replace function public.search_flights(
    p_origin text,
    p_destination text,
    p_date date,
    p_cabin text default 'economy',
    p_passengers int default 1
)
returns table (
    instance_id         uuid,
    flight_id           uuid,
    airline_code        text,
    airline_name        text,
    airline_color       text,
    flight_number       text,
    aircraft            text,
    origin              text,
    origin_city         text,
    destination         text,
    destination_city    text,
    departure_local     timestamp,
    arrival_local       timestamp,
    duration_minutes    int,
    stops               int,
    stop_airport        text,
    gate                text,
    boarding_time       time,
    status              text,
    delay_minutes       int,
    rating              numeric,
    perks               text[],
    price_usd           numeric,
    total_usd           numeric,
    is_best_value       boolean
)
language sql
stable
set search_path = ''
as $$
    with results as (
        select
            fi.id as instance_id,
            f.id as flight_id,
            a.code as airline_code,
            a.name as airline_name,
            a.brand_color as airline_color,
            f.flight_number,
            f.aircraft,
            f.origin::text as origin,
            o.city as origin_city,
            f.destination::text as destination,
            d.city as destination_city,
            (fi.departure_date + f.departure_time) as departure_local,
            ((((fi.departure_date + f.departure_time) at time zone o.tz)
                + make_interval(mins => f.duration_minutes)) at time zone d.tz) as arrival_local,
            f.duration_minutes,
            f.stops,
            f.stop_airport::text as stop_airport,
            fi.gate,
            fi.boarding_time,
            fi.status,
            fi.delay_minutes,
            f.rating,
            f.perks,
            round(f.base_fare_usd * fi.date_factor * public.cabin_factor(p_cabin)) as price_usd
        from public.flights f
        join public.flight_instances fi on fi.flight_id = f.id
        join public.airlines a on a.code = f.airline_code
        join public.airports o on o.code = f.origin
        join public.airports d on d.code = f.destination
        where f.origin = upper(p_origin)
          and f.destination = upper(p_destination)
          and fi.departure_date = p_date
          and fi.status <> 'cancelled'
    )
    select
        r.instance_id, r.flight_id, r.airline_code, r.airline_name, r.airline_color,
        r.flight_number, r.aircraft, r.origin, r.origin_city, r.destination, r.destination_city,
        r.departure_local, r.arrival_local, r.duration_minutes, r.stops, r.stop_airport,
        r.gate, r.boarding_time, r.status, r.delay_minutes, r.rating, r.perks,
        r.price_usd,
        r.price_usd * greatest(p_passengers, 1),
        coalesce(r.stops = 0 and r.price_usd = min(r.price_usd) filter (where r.stops = 0) over (), false)
    from results r
    order by r.price_usd, r.departure_local;
$$;

-- Cheapest fare for the two days either side of [p_center] (results date strip).
create or replace function public.date_price_strip(
    p_origin text,
    p_destination text,
    p_center date,
    p_cabin text default 'economy'
)
returns table (day date, min_price_usd numeric)
language sql
stable
set search_path = ''
as $$
    select
        g.day::date,
        (
            select min(round(f.base_fare_usd * fi.date_factor * public.cabin_factor(p_cabin)))
            from public.flights f
            join public.flight_instances fi on fi.flight_id = f.id
            where f.origin = upper(p_origin)
              and f.destination = upper(p_destination)
              and fi.departure_date = g.day::date
              and fi.status <> 'cancelled'
        )
    from generate_series(p_center - 2, p_center + 2, interval '1 day') as g(day)
    order by 1;
$$;

-- ---------------------------------------------------------------------------------------------
-- Seats
-- ---------------------------------------------------------------------------------------------

-- Rows 12–16, A–F. Row 12 is extra legroom (+$39). A seat is taken if it was sold to someone
-- else or held by any live booking. Security definer so other people's bookings count as taken
-- (only a boolean leaves the function).
create or replace function public.get_seat_map(p_instance_id uuid)
returns table (seat text, row_num int, letter text, seat_type text, extra_fee_usd numeric, taken boolean)
language sql
stable
security definer
set search_path = ''
as $$
    select
        r::text || l,
        r,
        l,
        case when r = 12 then 'legroom' else 'standard' end,
        case when r = 12 then 39 else 0 end::numeric,
        (r::text || l) = any (public.preassigned_seats())
            or exists (
                select 1 from public.bookings b
                where b.instance_id = p_instance_id
                  and b.seat = r::text || l
                  and b.status <> 'cancelled'
            )
    from generate_series(12, 16) as r
    cross join unnest(array['A', 'B', 'C', 'D', 'E', 'F']) as l
    order by r, l;
$$;

-- ---------------------------------------------------------------------------------------------
-- Booking and payment
-- ---------------------------------------------------------------------------------------------

-- Creates a pending booking and holds the seat. Idempotent per (user, key): retrying after a
-- dropped connection returns the same booking instead of a second one.
-- Total = fare × passengers + 12% tax + $39 legroom seat + $35 per checked bag.
create or replace function public.create_booking(
    p_instance_id uuid,
    p_cabin text,
    p_passengers int,
    p_seat text,
    p_bags int,
    p_idempotency_key uuid
)
returns public.bookings
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user      uuid := auth.uid();
    v_booking   public.bookings;
    v_price     numeric;
    v_fare      numeric;
    v_taxes     numeric;
    v_seat_fee  numeric;
    v_bags      numeric;
begin
    if v_user is null then
        raise exception 'not_authenticated' using errcode = '28000';
    end if;

    select * into v_booking from public.bookings
    where user_id = v_user and idempotency_key = p_idempotency_key;
    if found then
        return v_booking;
    end if;

    if p_cabin not in ('economy', 'premium', 'business', 'first') then
        raise exception 'invalid_cabin' using errcode = '22023';
    end if;
    if p_passengers not between 1 and 9 or coalesce(p_bags, 0) not between 0 and 3 then
        raise exception 'invalid_quantity' using errcode = '22023';
    end if;
    if p_seat !~ '^1[2-6][A-F]$' then
        raise exception 'invalid_seat' using errcode = '22023';
    end if;
    if p_seat = any (public.preassigned_seats()) then
        raise exception 'seat_taken' using errcode = '23505';
    end if;

    select round(f.base_fare_usd * fi.date_factor * public.cabin_factor(p_cabin))
    into v_price
    from public.flight_instances fi
    join public.flights f on f.id = fi.flight_id
    where fi.id = p_instance_id and fi.status <> 'cancelled';
    if v_price is null then
        raise exception 'flight_not_found' using errcode = 'P0002';
    end if;

    v_fare := v_price * p_passengers;
    v_taxes := round(v_fare * 0.12);
    v_seat_fee := case when left(p_seat, 2) = '12' then 39 else 0 end;
    v_bags := 35 * coalesce(p_bags, 0);

    begin
        insert into public.bookings (
            user_id, instance_id, cabin, passengers, seat, bags,
            fare_usd, taxes_usd, seat_fee_usd, bags_usd, total_usd, idempotency_key
        ) values (
            v_user, p_instance_id, p_cabin, p_passengers, p_seat, coalesce(p_bags, 0),
            v_fare, v_taxes, v_seat_fee, v_bags, v_fare + v_taxes + v_seat_fee + v_bags, p_idempotency_key
        )
        returning * into v_booking;
    exception when unique_violation then
        raise exception 'seat_taken' using errcode = '23505';
    end;

    return v_booking;
end;
$$;

-- Mock payment processor: confirms the booking and issues a PNR such as SKP7Q2.
-- Paying twice returns the confirmed booking unchanged.
create or replace function public.pay_booking(p_booking_id uuid, p_method text)
returns public.bookings
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_booking   public.bookings;
    v_pnr       text;
    v_alphabet  constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
begin
    select * into v_booking from public.bookings
    where id = p_booking_id and user_id = auth.uid()
    for update;
    if not found then
        raise exception 'booking_not_found' using errcode = 'P0002';
    end if;
    if v_booking.status = 'confirmed' then
        return v_booking;
    end if;
    if v_booking.status = 'cancelled' then
        raise exception 'booking_cancelled' using errcode = '22023';
    end if;
    if p_method not in ('visa', 'mastercard', 'google_pay') then
        raise exception 'invalid_payment_method' using errcode = '22023';
    end if;

    loop
        v_pnr := 'SKP' || (
            select string_agg(substr(v_alphabet, 1 + floor(random() * length(v_alphabet))::int, 1), '')
            from generate_series(1, 3)
        );
        exit when not exists (select 1 from public.bookings where pnr = v_pnr);
    end loop;

    update public.bookings
    set status = 'confirmed', pnr = v_pnr, payment_method = p_method, paid_at = now()
    where id = p_booking_id
    returning * into v_booking;

    return v_booking;
end;
$$;

-- Only signed-in users (including anonymous guests) can book or pay.
revoke execute on function public.create_booking(uuid, text, int, text, int, uuid) from public, anon;
revoke execute on function public.pay_booking(uuid, text) from public, anon;
grant execute on function public.create_booking(uuid, text, int, text, int, uuid) to authenticated;
grant execute on function public.pay_booking(uuid, text) to authenticated;
