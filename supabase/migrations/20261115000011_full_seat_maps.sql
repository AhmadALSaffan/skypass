-- Full aircraft seat maps: the real cabin layout for each aircraft family and cabin, exit rows,
-- extra-legroom rows, realistic occupancy per departure, and one seat per traveller.

-- ---------------------------------------------------------------------------------------------
-- Cabin layouts. "ABC-DEF-HJK" = seat letters with aisles at the dashes (no letter I, as airlines do).
-- ---------------------------------------------------------------------------------------------
create table if not exists public.cabin_layouts (
    family          text not null,
    cabin           text not null check (cabin in ('economy', 'premium', 'business', 'first')),
    layout          text not null,
    first_row       int not null,
    last_row        int not null check (last_row >= first_row),
    legroom_rows    int[] not null default '{}',
    exit_rows       int[] not null default '{}',
    primary key (family, cabin)
);
alter table public.cabin_layouts enable row level security;
create policy "catalog is public" on public.cabin_layouts for select to anon, authenticated using (true);

insert into public.cabin_layouts (family, cabin, layout, first_row, last_row, legroom_rows, exit_rows) values
    ('turboprop',   'economy',  'AC-DF',        1,  17, '{1}',      '{1}'),
    ('e-jet',       'business', 'AC-DF',        1,  3,  '{}',       '{}'),
    ('e-jet',       'economy',  'AC-DF',        5,  30, '{5,14}',   '{14}'),
    ('a220',        'business', 'AC-DF',        1,  3,  '{}',       '{}'),
    ('a220',        'economy',  'AC-DEF',       8,  30, '{8,14}',   '{14}'),
    ('narrow',      'business', 'AC-DF',        1,  3,  '{}',       '{}'),
    ('narrow',      'premium',  'ABC-DEF',      4,  7,  '{}',       '{}'),
    ('narrow',      'economy',  'ABC-DEF',      8,  31, '{8,12,13}', '{12,13}'),
    ('narrow-long', 'business', 'AC-DF',        1,  4,  '{}',       '{}'),
    ('narrow-long', 'premium',  'ABC-DEF',      5,  8,  '{}',       '{}'),
    ('narrow-long', 'economy',  'ABC-DEF',      9,  38, '{9,15,26}', '{15,26}'),
    ('787',         'business', 'A-DG-K',       1,  8,  '{}',       '{}'),
    ('787',         'premium',  'AC-DEF-HK',    20, 24, '{}',       '{}'),
    ('787',         'economy',  'ABC-DEF-HJK',  30, 55, '{30,41}',  '{41}'),
    ('777',         'first',    'A-E-K',        1,  2,  '{}',       '{}'),
    ('777',         'business', 'A-EF-K',       5,  14, '{}',       '{}'),
    ('777',         'premium',  'ABC-DEF-HJK',  20, 25, '{}',       '{}'),
    ('777',         'economy',  'ABC-DEFG-HJK', 30, 60, '{30,45}',  '{45}'),
    ('a330',        'business', 'A-EF-K',       1,  7,  '{}',       '{}'),
    ('a330',        'premium',  'AC-DEF-HK',    15, 19, '{}',       '{}'),
    ('a330',        'economy',  'AC-DEFG-HK',   20, 45, '{20,33}',  '{33}'),
    ('a350',        'business', 'A-DG-K',       1,  9,  '{}',       '{}'),
    ('a350',        'premium',  'AC-DEF-HK',    15, 19, '{}',       '{}'),
    ('a350',        'economy',  'ABC-DEF-HJK',  20, 52, '{20,36}',  '{36}'),
    ('a380',        'first',    'A-E-K',        1,  4,  '{}',       '{}'),
    ('a380',        'business', 'A-EF-K',       6,  20, '{}',       '{}'),
    ('a380',        'premium',  'ABC-DEF-HJK',  22, 27, '{}',       '{}'),
    ('a380',        'economy',  'ABC-DEFG-HJK', 30, 75, '{30,52}',  '{52}')
on conflict (family, cabin) do update set
    layout = excluded.layout, first_row = excluded.first_row, last_row = excluded.last_row,
    legroom_rows = excluded.legroom_rows, exit_rows = excluded.exit_rows;

create or replace function public.aircraft_family(p_aircraft text)
returns text
language sql
immutable
set search_path = ''
as $$
    select case
        when p_aircraft like 'ATR%' or p_aircraft like 'Q%' then 'turboprop'
        when p_aircraft like 'E1%' then 'e-jet'
        when p_aircraft like 'A220%' then 'a220'
        when p_aircraft like '787%' then '787'
        when p_aircraft like '777%' or p_aircraft like '747%' then '777'
        when p_aircraft like 'A330%' then 'a330'
        when p_aircraft like 'A350%' then 'a350'
        when p_aircraft like 'A380%' then 'a380'
        when p_aircraft ~ '(A321|737-900|737 MAX 9|757)' then 'narrow-long'
        else 'narrow'
    end;
$$;

-- ---------------------------------------------------------------------------------------------
-- Seats held by bookings, one per traveller. The primary key is the seat lock.
-- ---------------------------------------------------------------------------------------------
create table if not exists public.booking_seats (
    instance_id     uuid not null references public.flight_instances (id),
    seat            text not null,
    booking_id      uuid not null references public.bookings (id) on delete cascade,
    passenger_id    uuid references public.passengers (id),
    primary key (instance_id, seat)
);
create index if not exists booking_seats_booking_idx on public.booking_seats (booking_id);
create index if not exists booking_seats_passenger_idx on public.booking_seats (passenger_id);
alter table public.booking_seats enable row level security;
create policy "own booking seats" on public.booking_seats for select to authenticated
    using (exists (select 1 from public.bookings b where b.id = booking_id and b.user_id = (select auth.uid())));

-- Existing bookings keep their seat in the new table; the old single-seat lock goes.
insert into public.booking_seats (instance_id, seat, booking_id)
select instance_id, seat, id from public.bookings where status <> 'cancelled'
on conflict do nothing;
drop index if exists public.bookings_seat_lock;

-- ---------------------------------------------------------------------------------------------
-- The seat map for a departure and cabin. 30–74% of seats are sold to other travellers
-- (stable per departure), plus every seat held by a Skypass booking.
-- Falls back First → Business → Economy when an aircraft has no such cabin.
-- ---------------------------------------------------------------------------------------------
drop function if exists public.get_seat_map(uuid);
create function public.get_seat_map(p_instance_id uuid, p_cabin text default 'economy')
returns table (
    seat text, row_num int, letter text, seat_type text, extra_fee_usd numeric, taken boolean,
    layout text, exit_row boolean, cabin text
)
language sql
stable
security definer
set search_path = ''
as $$
    with inst as (
        select fi.id, public.aircraft_family(f.aircraft) as family
        from public.flight_instances fi join public.flights f on f.id = fi.flight_id
        where fi.id = p_instance_id
    ),
    lay as (
        select l.*
        from inst
        join public.cabin_layouts l on l.family = inst.family
        where l.cabin in (p_cabin, 'business', 'economy')
        order by case when l.cabin = p_cabin then 0 when l.cabin = 'business' and p_cabin = 'first' then 1 when l.cabin = 'economy' then 2 else 3 end
        limit 1
    ),
    letters as (
        select lay.*, l.letter, l.ord
        from lay, regexp_split_to_table(replace(lay.layout, '-', ''), '') with ordinality as l(letter, ord)
    ),
    seats as (
        select letters.*, r as row_num, r::text || letters.letter as code
        from letters, generate_series(letters.first_row, letters.last_row) as r
    )
    select
        s.code,
        s.row_num,
        s.letter,
        case when s.cabin <> 'economy' then s.cabin when s.row_num = any (s.legroom_rows) then 'legroom' else 'standard' end,
        case when s.cabin = 'economy' and s.row_num = any (s.legroom_rows) then 39 else 0 end::numeric,
        (abs(hashtext(p_instance_id::text || s.code)::bigint) % 100) < 30 + (abs(hashtext(p_instance_id::text)::bigint) % 45)
            or exists (select 1 from public.booking_seats bs where bs.instance_id = p_instance_id and bs.seat = s.code),
        s.layout,
        s.row_num = any (s.exit_rows),
        s.cabin
    from seats s
    order by s.row_num, s.ord;
$$;
revoke execute on function public.get_seat_map(uuid, text) from public, anon;
grant execute on function public.get_seat_map(uuid, text) to authenticated;

-- ---------------------------------------------------------------------------------------------
-- create_booking v3: a seat per traveller (p_seats[i] belongs to p_passenger_ids[i]).
-- Seat fees come from the seat map; seats are locked in booking_seats.
-- ---------------------------------------------------------------------------------------------
drop function if exists public.create_booking(uuid, text, int, text, int, uuid, uuid[], text);
create function public.create_booking(
    p_instance_id uuid,
    p_cabin text,
    p_seats text[],
    p_bags int,
    p_idempotency_key uuid,
    p_passenger_ids uuid[],
    p_promo text default null
)
returns public.bookings
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user      uuid := auth.uid();
    v_booking   public.bookings;
    v_count     int := coalesce(array_length(p_passenger_ids, 1), 0);
    v_price     numeric;
    v_fare      numeric;
    v_discount  numeric := 0;
    v_promo     text;
    v_percent   int;
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
    if v_count not between 1 and 9 or coalesce(array_length(p_seats, 1), 0) <> v_count
        or coalesce(p_bags, 0) not between 0 and 3 then
        raise exception 'invalid_quantity' using errcode = '22023';
    end if;
    if (select count(distinct s) from unnest(p_seats) s) <> v_count then
        raise exception 'duplicate_seat' using errcode = '22023';
    end if;
    if exists (
        select 1 from unnest(p_passenger_ids) as pid
        where not exists (select 1 from public.passengers p where p.id = pid and p.user_id = v_user)
    ) then
        raise exception 'invalid_passenger' using errcode = '22023';
    end if;

    -- Every seat must exist on this departure's map and be free.
    select sum(m.extra_fee_usd) into v_seat_fee
    from public.get_seat_map(p_instance_id, p_cabin) m
    where m.seat = any (p_seats) and not m.taken;
    if (select count(*) from public.get_seat_map(p_instance_id, p_cabin) m where m.seat = any (p_seats) and not m.taken) <> v_count then
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

    if p_promo is not null and trim(p_promo) <> '' then
        select code, percent_off into v_promo, v_percent from public.check_promo(p_promo);
        if v_promo is null then
            raise exception 'invalid_promo' using errcode = '22023';
        end if;
    end if;

    v_fare := v_price * v_count;
    v_discount := case when v_percent is null then 0 else round(v_fare * v_percent / 100.0) end;
    v_taxes := round((v_fare - v_discount) * 0.12);
    v_bags := 35 * coalesce(p_bags, 0);

    insert into public.bookings (
        user_id, instance_id, cabin, passengers, seat, bags,
        fare_usd, taxes_usd, seat_fee_usd, bags_usd, total_usd, idempotency_key, promo_code, discount_usd
    ) values (
        v_user, p_instance_id, p_cabin, v_count, p_seats[1], coalesce(p_bags, 0),
        v_fare, v_taxes, coalesce(v_seat_fee, 0), v_bags, v_fare - v_discount + v_taxes + coalesce(v_seat_fee, 0) + v_bags,
        p_idempotency_key, v_promo, v_discount
    )
    returning * into v_booking;

    begin
        insert into public.booking_seats (instance_id, seat, booking_id, passenger_id)
        select p_instance_id, s.seat, v_booking.id, s.pid
        from unnest(p_seats, p_passenger_ids) as s(seat, pid);
    exception when unique_violation then
        raise exception 'seat_taken' using errcode = '23505';
    end;

    insert into public.booking_passengers (booking_id, passenger_id)
    select v_booking.id, pid from unnest(p_passenger_ids) as pid
    on conflict do nothing;

    return v_booking;
end;
$$;
revoke execute on function public.create_booking(uuid, text, text[], int, uuid, uuid[], text) from public, anon;
grant execute on function public.create_booking(uuid, text, text[], int, uuid, uuid[], text) to authenticated;

-- ---------------------------------------------------------------------------------------------
-- Boarding pass: one per traveller (names and seats in the same order).
-- ---------------------------------------------------------------------------------------------
drop function if exists public.boarding_pass(uuid);
create function public.boarding_pass(p_booking_id uuid)
returns table (
    booking_id uuid, pnr text, status text, seat text, cabin text, boarding_zone text, bags int, total_usd numeric,
    airline_code text, airline_name text, airline_color text, airline_logo text, flight_number text, aircraft text,
    origin text, origin_city text, destination text, destination_city text,
    departure_local timestamp, arrival_local timestamp, duration_minutes int, stops int, stop_airport text,
    gate text, boarding_time time, flight_status text, passenger_names text[], passenger_seats text[]
)
language sql
stable
set search_path = ''
as $$
    with travellers as (
        select bs.booking_id,
            array_agg(coalesce(p.full_name, 'Traveller') order by p.is_primary desc nulls last, p.created_at, bs.seat) as names,
            array_agg(bs.seat order by p.is_primary desc nulls last, p.created_at, bs.seat) as seats
        from public.booking_seats bs
        left join public.passengers p on p.id = bs.passenger_id
        where bs.booking_id = p_booking_id
        group by bs.booking_id
    )
    select
        b.id, b.pnr, b.status, b.seat, b.cabin,
        case when b.cabin in ('business', 'first') then 'Priority' when b.cabin = 'premium' then 'Group 1' else 'Group 2' end,
        b.bags, b.total_usd,
        a.code, a.name, a.brand_color, a.logo_url, f.flight_number, f.aircraft,
        f.origin::text, o.city, f.destination::text, d.city,
        fi.departure_date + f.departure_time,
        ((((fi.departure_date + f.departure_time) at time zone o.tz)
            + make_interval(mins => f.duration_minutes)) at time zone d.tz),
        f.duration_minutes, f.stops, f.stop_airport::text, fi.gate, fi.boarding_time, fi.status,
        coalesce(t.names, '{}'), coalesce(t.seats, array[b.seat])
    from public.bookings b
    join public.flight_instances fi on fi.id = b.instance_id
    join public.flights f on f.id = fi.flight_id
    join public.airlines a on a.code = f.airline_code
    join public.airports o on o.code = f.origin
    join public.airports d on d.code = f.destination
    left join travellers t on t.booking_id = b.id
    where b.id = p_booking_id and b.user_id = (select auth.uid());
$$;
revoke execute on function public.boarding_pass(uuid) from public, anon;
grant execute on function public.boarding_pass(uuid) to authenticated;

drop function if exists public.preassigned_seats();
