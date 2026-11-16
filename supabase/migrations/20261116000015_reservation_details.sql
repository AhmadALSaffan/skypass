-- Booking details for stays and cars, kept so My trips can show them later:
--   * the lead guest, arrival time and requests of a hotel reservation,
--   * the driver, age band, flight number and extras of a car rental (extras priced here),
--   * my_flights() for the Flights tab, and cancel_reservation() for free-cancellation bookings.

-- ---------------------------------------------------------------------------------------------
-- Hotel reservations
-- ---------------------------------------------------------------------------------------------

alter table public.hotel_reservations
    add column if not exists guest_name     text check (char_length(guest_name) <= 120),
    add column if not exists guest_email    text check (char_length(guest_email) <= 200),
    add column if not exists guest_phone    text check (char_length(guest_phone) <= 40),
    add column if not exists arrival_window text check (arrival_window in ('before_12', '12_15', '15_18', 'after_18')),
    add column if not exists requests       text[] not null default '{}',
    add column if not exists note           text check (char_length(note) <= 500),
    add column if not exists nightly_usd    numeric(10, 2),
    add column if not exists cancelled_at   timestamptz;

drop function if exists public.reserve_hotel(uuid, text, date, date, int, uuid);

-- Prices the room on the server (nightly rate × room factor × nights) and books it with the lead
-- guest's details. The same idempotency key returns the first reservation instead of booking twice.
create or replace function public.reserve_hotel(
    p_hotel uuid,
    p_room text,
    p_check_in date,
    p_check_out date,
    p_guests int,
    p_idempotency_key uuid,
    p_guest_name text default null,
    p_guest_email text default null,
    p_guest_phone text default null,
    p_arrival text default null,
    p_requests text[] default '{}',
    p_note text default null
)
returns table (reference text, total_usd numeric, nights int, nightly_usd numeric)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user     uuid := auth.uid();
    v_price    numeric;
    v_factor   numeric;
    v_capacity int;
    v_nights   int;
    v_nightly  numeric;
    v_total    numeric;
    v_ref      text;
    v_alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
begin
    if v_user is null then
        raise exception 'not_signed_in' using errcode = '28000';
    end if;

    return query
        select r.reference, r.total_usd, (r.check_out - r.check_in)::int, r.nightly_usd
        from public.hotel_reservations r
        where r.user_id = v_user and r.idempotency_key = p_idempotency_key;
    if found then
        return;
    end if;

    select h.price_usd into v_price from public.hotels h where h.id = p_hotel;
    if v_price is null then
        raise exception 'hotel_not_found' using errcode = 'P0002';
    end if;

    case p_room
        when 'standard' then v_factor := 1.0;  v_capacity := 2;
        when 'deluxe'   then v_factor := 1.35; v_capacity := 3;
        when 'suite'    then v_factor := 1.9;  v_capacity := 4;
        else raise exception 'invalid_room' using errcode = '22023';
    end case;

    if p_guests < 1 or p_guests > v_capacity then
        raise exception 'too_many_guests' using errcode = '22023';
    end if;

    v_nights := p_check_out - p_check_in;
    if p_check_in < current_date or v_nights < 1 or v_nights > 30 then
        raise exception 'invalid_dates' using errcode = '22023';
    end if;

    if p_guest_name is not null and char_length(trim(p_guest_name)) < 2 then
        raise exception 'invalid_guest' using errcode = '22023';
    end if;
    if p_guest_email is not null and p_guest_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' then
        raise exception 'invalid_guest' using errcode = '22023';
    end if;
    if exists (
        select 1 from unnest(coalesce(p_requests, '{}')) q
        where q not in ('high_floor', 'quiet_room', 'early_check_in', 'late_check_out', 'twin_beds', 'baby_cot')
    ) then
        raise exception 'invalid_request' using errcode = '22023';
    end if;

    v_nightly := round(v_price * v_factor);
    v_total := v_nightly * v_nights;

    loop
        v_ref := 'SKH' || (
            select string_agg(substr(v_alphabet, 1 + floor(random() * length(v_alphabet))::int, 1), '')
            from generate_series(1, 3)
        );
        exit when not exists (select 1 from public.hotel_reservations x where x.reference = v_ref);
    end loop;

    insert into public.hotel_reservations (
        user_id, hotel_id, room, check_in, check_out, guests, total_usd, reference, idempotency_key,
        guest_name, guest_email, guest_phone, arrival_window, requests, note, nightly_usd
    )
    values (
        v_user, p_hotel, p_room, p_check_in, p_check_out, p_guests, v_total, v_ref, p_idempotency_key,
        nullif(trim(p_guest_name), ''), nullif(trim(p_guest_email), ''), nullif(trim(p_guest_phone), ''),
        p_arrival, coalesce(p_requests, '{}'), nullif(trim(p_note), ''), v_nightly
    );

    return query select v_ref, v_total, v_nights, v_nightly;
end;
$$;

revoke execute on function public.reserve_hotel(uuid, text, date, date, int, uuid, text, text, text, text, text[], text) from public, anon;
grant execute on function public.reserve_hotel(uuid, text, date, date, int, uuid, text, text, text, text, text[], text) to authenticated;

-- ---------------------------------------------------------------------------------------------
-- Car rentals
-- ---------------------------------------------------------------------------------------------

alter table public.rental_reservations
    add column if not exists driver_name          text check (char_length(driver_name) <= 120),
    add column if not exists driver_email         text check (char_length(driver_email) <= 200),
    add column if not exists driver_phone         text check (char_length(driver_phone) <= 40),
    add column if not exists driver_age           text check (driver_age in ('21_24', '25_69', '70_plus')),
    add column if not exists flight_number        text check (char_length(flight_number) <= 10),
    add column if not exists extras               text[] not null default '{}',
    add column if not exists extras_usd           numeric(10, 2) not null default 0,
    add column if not exists young_driver_fee_usd numeric(10, 2) not null default 0,
    add column if not exists daily_usd            numeric(10, 2),
    add column if not exists cancelled_at         timestamptz;

drop function if exists public.reserve_car(uuid, text, text, timestamptz, timestamptz, uuid);

-- Days are started 24-hour periods (Fri 14:00 → Fri 10:00 a week later is 7 days). Extras and the
-- young driver fee are per day; returning the car elsewhere adds a one-way fee.
create or replace function public.reserve_car(
    p_car uuid,
    p_pickup_location text,
    p_dropoff_location text,
    p_pickup_at timestamptz,
    p_dropoff_at timestamptz,
    p_idempotency_key uuid,
    p_driver_name text default null,
    p_driver_email text default null,
    p_driver_phone text default null,
    p_driver_age text default '25_69',
    p_flight_number text default null,
    p_extras text[] default '{}'
)
returns table (
    reference text, total_usd numeric, days int, one_way_fee_usd numeric,
    extras_usd numeric, young_driver_fee_usd numeric, daily_usd numeric
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user     uuid := auth.uid();
    v_price    numeric;
    v_days     int;
    v_fee      numeric;
    v_extras   numeric;
    v_young    numeric;
    v_total    numeric;
    v_ref      text;
    v_alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
begin
    if v_user is null then
        raise exception 'not_signed_in' using errcode = '28000';
    end if;

    return query
        select r.reference, r.total_usd, r.days, r.one_way_fee_usd, r.extras_usd, r.young_driver_fee_usd,
               coalesce(r.daily_usd, (r.total_usd - r.one_way_fee_usd - r.extras_usd - r.young_driver_fee_usd) / greatest(r.days, 1))
        from public.rental_reservations r
        where r.user_id = v_user and r.idempotency_key = p_idempotency_key;
    if found then
        return;
    end if;

    select c.price_per_day_usd into v_price from public.cars c where c.id = p_car;
    if v_price is null then
        raise exception 'car_not_found' using errcode = 'P0002';
    end if;

    -- Times are the airport's local wall clock sent as UTC, so allow a day of slack for time zones.
    if p_pickup_at < now() - interval '1 day' or p_dropoff_at <= p_pickup_at then
        raise exception 'invalid_dates' using errcode = '22023';
    end if;
    v_days := ceil(extract(epoch from (p_dropoff_at - p_pickup_at)) / 86400.0)::int;
    if v_days < 1 or v_days > 30 then
        raise exception 'invalid_dates' using errcode = '22023';
    end if;
    if coalesce(trim(p_pickup_location), '') = '' or coalesce(trim(p_dropoff_location), '') = '' then
        raise exception 'invalid_location' using errcode = '22023';
    end if;

    if p_driver_name is not null and char_length(trim(p_driver_name)) < 2 then
        raise exception 'invalid_driver' using errcode = '22023';
    end if;
    if p_driver_email is not null and p_driver_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' then
        raise exception 'invalid_driver' using errcode = '22023';
    end if;
    if coalesce(p_driver_age, '25_69') not in ('21_24', '25_69', '70_plus') then
        raise exception 'invalid_driver' using errcode = '22023';
    end if;
    if exists (
        select 1 from unnest(coalesce(p_extras, '{}')) e
        where e not in ('full_cover', 'child_seat', 'gps', 'extra_driver')
    ) then
        raise exception 'invalid_extra' using errcode = '22023';
    end if;

    v_fee := case when p_dropoff_location = p_pickup_location then 0 else 49 end;
    v_extras := v_days * coalesce((
        select sum(case e when 'full_cover' then 18 when 'child_seat' then 8 when 'gps' then 6 when 'extra_driver' then 10 end)
        from (select distinct unnest(coalesce(p_extras, '{}')) as e) x
    ), 0);
    v_young := case when p_driver_age = '21_24' then 15 * v_days else 0 end;
    v_total := v_price * v_days + v_extras + v_young + v_fee;

    loop
        v_ref := 'SKC' || (
            select string_agg(substr(v_alphabet, 1 + floor(random() * length(v_alphabet))::int, 1), '')
            from generate_series(1, 3)
        );
        exit when not exists (select 1 from public.rental_reservations x where x.reference = v_ref);
    end loop;

    insert into public.rental_reservations (
        user_id, car_id, pickup_location, dropoff_location, pickup_at, dropoff_at, total_usd, reference,
        idempotency_key, days, one_way_fee_usd, driver_name, driver_email, driver_phone, driver_age,
        flight_number, extras, extras_usd, young_driver_fee_usd, daily_usd
    )
    values (
        v_user, p_car, p_pickup_location, p_dropoff_location, p_pickup_at, p_dropoff_at, v_total, v_ref,
        p_idempotency_key, v_days, v_fee, nullif(trim(p_driver_name), ''), nullif(trim(p_driver_email), ''),
        nullif(trim(p_driver_phone), ''), coalesce(p_driver_age, '25_69'), nullif(upper(trim(p_flight_number)), ''),
        (select coalesce(array_agg(distinct e), '{}') from unnest(coalesce(p_extras, '{}')) e), v_extras, v_young, v_price
    );

    return query select v_ref, v_total, v_days, v_fee, v_extras, v_young, v_price;
end;
$$;

revoke execute on function public.reserve_car(uuid, text, text, timestamptz, timestamptz, uuid, text, text, text, text, text, text[]) from public, anon;
grant execute on function public.reserve_car(uuid, text, text, timestamptz, timestamptz, uuid, text, text, text, text, text, text[]) to authenticated;

-- ---------------------------------------------------------------------------------------------
-- My trips
-- ---------------------------------------------------------------------------------------------

-- Every confirmed flight, past and upcoming, in the same shape as next_trip() plus what was paid.
create or replace function public.my_flights()
returns table (
    booking_id uuid, pnr text, seat text, cabin text, instance_id uuid,
    airline_code text, airline_name text, airline_color text, airline_logo text, flight_number text,
    origin text, origin_city text, destination text, destination_city text,
    departure_local timestamp, duration_minutes int, gate text, boarding_time time, status text, delay_minutes int,
    total_usd numeric, passengers int
)
language sql
stable
set search_path = ''
as $$
    select
        b.id, b.pnr, b.seat, b.cabin, fi.id,
        a.code, a.name, a.brand_color, a.logo_url, f.flight_number,
        f.origin::text, o.city, f.destination::text, d.city,
        fi.departure_date + f.departure_time,
        f.duration_minutes, fi.gate, fi.boarding_time, fi.status, fi.delay_minutes,
        b.total_usd, b.passengers
    from public.bookings b
    join public.flight_instances fi on fi.id = b.instance_id
    join public.flights f on f.id = fi.flight_id
    join public.airlines a on a.code = f.airline_code
    join public.airports o on o.code = f.origin
    join public.airports d on d.code = f.destination
    where b.user_id = (select auth.uid())
      and b.status = 'confirmed'
    order by fi.departure_date + f.departure_time;
$$;
revoke execute on function public.my_flights() from public, anon;
grant execute on function public.my_flights() to authenticated;

-- Cancels a stay (SKH…) or a car (SKC…) when it was booked with free cancellation: a stay until
-- the day before check-in, a car up to 48 hours before pick-up.
create or replace function public.cancel_reservation(p_reference text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
begin
    if v_user is null then
        raise exception 'not_signed_in' using errcode = '28000';
    end if;

    if p_reference like 'SKH%' then
        update public.hotel_reservations r
        set status = 'cancelled', cancelled_at = now()
        from public.hotels h
        where h.id = r.hotel_id
          and r.reference = p_reference
          and r.user_id = v_user
          and r.status = 'confirmed'
          and h.free_cancel
          and current_date < r.check_in;
    elsif p_reference like 'SKC%' then
        update public.rental_reservations r
        set status = 'cancelled', cancelled_at = now()
        from public.cars c
        where c.id = r.car_id
          and r.reference = p_reference
          and r.user_id = v_user
          and r.status = 'reserved'
          and c.free_cancel
          and now() < r.pickup_at - interval '48 hours';
    end if;

    if not found then
        raise exception 'not_cancellable' using errcode = '22023';
    end if;
end;
$$;
revoke execute on function public.cancel_reservation(text) from public, anon;
grant execute on function public.cancel_reservation(text) to authenticated;
