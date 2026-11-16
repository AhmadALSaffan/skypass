-- Car rental: the board's eight cars at every airport, priced for the city, and a reservation
-- function that prices the rental on the server.

-- ---------------------------------------------------------------------------------------------
-- Catalogue
-- ---------------------------------------------------------------------------------------------
-- Auckland keeps the board's fleet and suppliers. Every other airport gets the same eight models
-- at a city price (0.75–1.35 × the board's rate, from the airport code), from local-sounding
-- suppliers. New Zealand airports keep the board's New Zealand suppliers.

with models (name, type, seats, bags, base_price, nz_supplier, supplier, stage_color, image_path, sort_order) as (values
    ('Kia Cerato',       'Compact',  5, 2, 41,  'Kiwi Rentals',     'City Car Hire',    '#F9DCD2', 'cars/kia-cerato.png',       1),
    ('Nissan Note',      'Compact',  5, 1, 36,  'Harbour Car Hire', 'Airport Drive',    '#E6E8F2', 'cars/nissan-note.png',      2),
    ('Jeep Wrangler',    'SUV',      4, 2, 92,  'Southern Drive',   'Open Road Rentals','#CFE0F2', 'cars/jeep-wrangler.png',    1),
    ('Nissan Juke',      'SUV',      5, 2, 58,  'Kiwi Rentals',     'City Car Hire',    '#F7EBC4', 'cars/nissan-juke.png',      2),
    ('Tesla Model 3',    'Electric', 5, 2, 84,  'Volt NZ',          'Volt Rentals',     '#CFE0F2', 'cars/tesla-model-3.png',    1),
    ('Tesla Model S',    'Electric', 5, 3, 112, 'Volt NZ',          'Volt Rentals',     '#F9DCD2', 'cars/tesla-model-s.png',    2),
    ('Ford Mustang',     'Premium',  4, 2, 129, 'Harbour Car Hire', 'Prestige Motors',  '#DEDCF6', 'cars/ford-mustang.png',     1),
    ('Chevrolet Camaro', 'Premium',  4, 2, 119, 'Kiwi Rentals',     'Prestige Motors',  '#F7EBC4', 'cars/chevrolet-camaro.png', 2)
)
insert into public.cars (location_code, name, type, seats, bags, price_per_day_usd, supplier, stage_color, image_path, sort_order)
select
    a.code,
    m.name,
    m.type,
    m.seats,
    m.bags,
    round(m.base_price * (0.75 + (abs(hashtext(a.code)) % 61) / 100.0)),
    case when a.country = 'New Zealand' then m.nz_supplier else m.supplier end,
    m.stage_color,
    m.image_path,
    m.sort_order
from public.airports a cross join models m
where a.code <> 'AKL'
  and not exists (select 1 from public.cars c where c.location_code = a.code and c.name = m.name);

-- ---------------------------------------------------------------------------------------------
-- Reservations
-- ---------------------------------------------------------------------------------------------
-- Rentals are booked only through reserve_car (which prices them); travellers can read their own.

alter table public.rental_reservations add column if not exists reference text unique;
alter table public.rental_reservations add column if not exists idempotency_key uuid;
alter table public.rental_reservations add column if not exists days int;
alter table public.rental_reservations add column if not exists one_way_fee_usd numeric(10, 2) not null default 0;
create unique index if not exists rental_reservations_attempt_idx on public.rental_reservations (user_id, idempotency_key);

drop policy if exists "own rentals" on public.rental_reservations;
create policy "own rentals" on public.rental_reservations
    for select to authenticated using ((select auth.uid()) = user_id);

-- Days are started 24-hour periods (Fri 14:00 → Fri 10:00 a week later is 7 days). Dropping the
-- car somewhere else adds a one-way fee. The same idempotency key returns the first reservation.
create or replace function public.reserve_car(
    p_car uuid,
    p_pickup_location text,
    p_dropoff_location text,
    p_pickup_at timestamptz,
    p_dropoff_at timestamptz,
    p_idempotency_key uuid
)
returns table (reference text, total_usd numeric, days int, one_way_fee_usd numeric)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user     uuid := auth.uid();
    v_price    numeric;
    v_days     int;
    v_fee      numeric;
    v_total    numeric;
    v_ref      text;
    v_alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
begin
    if v_user is null then
        raise exception 'not_signed_in' using errcode = '28000';
    end if;

    return query
        select r.reference, r.total_usd, r.days, r.one_way_fee_usd
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

    v_fee := case when p_dropoff_location = p_pickup_location then 0 else 49 end;
    v_total := v_price * v_days + v_fee;

    loop
        v_ref := 'SKC' || (
            select string_agg(substr(v_alphabet, 1 + floor(random() * length(v_alphabet))::int, 1), '')
            from generate_series(1, 3)
        );
        exit when not exists (select 1 from public.rental_reservations x where x.reference = v_ref);
    end loop;

    insert into public.rental_reservations
        (user_id, car_id, pickup_location, dropoff_location, pickup_at, dropoff_at, total_usd, reference, idempotency_key, days, one_way_fee_usd)
    values
        (v_user, p_car, p_pickup_location, p_dropoff_location, p_pickup_at, p_dropoff_at, v_total, v_ref, p_idempotency_key, v_days, v_fee);

    return query select v_ref, v_total, v_days, v_fee;
end;
$$;

revoke execute on function public.reserve_car(uuid, text, text, timestamptz, timestamptz, uuid) from public, anon;
grant execute on function public.reserve_car(uuid, text, text, timestamptz, timestamptz, uuid) to authenticated;
