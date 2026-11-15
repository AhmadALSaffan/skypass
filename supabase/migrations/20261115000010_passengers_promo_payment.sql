-- Phase 6: full passenger details, promo codes, travellers linked to bookings, and the boarding pass.

-- ---------------------------------------------------------------------------------------------
-- Passengers: everything an airline needs (APIS-style). Columns are nullable so a traveller can be
-- saved half-way; the app requires every field before payment. full_name and passport_last4 are
-- derived so older code and the boarding pass keep working.
-- ---------------------------------------------------------------------------------------------
alter table public.passengers
    add column if not exists first_name text,
    add column if not exists last_name text,
    add column if not exists passport_type text check (passport_type in ('ordinary', 'diplomatic', 'service', 'official')),
    add column if not exists passport_number text check (passport_number ~ '^[A-Z0-9]{6,9}$'),
    add column if not exists passport_issue_date date,
    add column if not exists passport_expiry_date date,
    add column if not exists gender text check (gender in ('female', 'male', 'unspecified')),
    add column if not exists phone text check (phone ~ '^\+?[0-9 ()-]{7,20}$'),
    add column if not exists email text check (email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$');

alter table public.passengers drop constraint if exists passport_dates;
alter table public.passengers add constraint passport_dates
    check (passport_issue_date is null or passport_expiry_date is null or passport_expiry_date > passport_issue_date);

-- Split existing names, then replace the stored columns with derived ones.
update public.passengers
set first_name = split_part(full_name, ' ', 1),
    last_name = nullif(trim(substr(full_name, length(split_part(full_name, ' ', 1)) + 1)), '')
where first_name is null;

alter table public.passengers drop column if exists passport_last4;
alter table public.passengers drop column if exists full_name;
alter table public.passengers alter column first_name set not null;
alter table public.passengers
    add column full_name text generated always as (trim(first_name || ' ' || coalesce(last_name, ''))) stored,
    add column passport_last4 text generated always as (right(passport_number, 4)) stored;

-- ---------------------------------------------------------------------------------------------
-- Promo codes: not readable by clients (codes can't be listed); checked through check_promo().
-- ---------------------------------------------------------------------------------------------
create table if not exists public.promo_codes (
    code            text primary key check (code = upper(code)),
    percent_off     int not null check (percent_off between 1 and 50),
    description     text not null,
    active          boolean not null default true,
    valid_until     date
);
alter table public.promo_codes enable row level security;

insert into public.promo_codes (code, percent_off, description) values
    ('KIAORA10', 10, '10% off your fare'),
    ('AOTEAROA15', 15, '15% off flights to and from New Zealand'),
    ('SKYPASS20', 20, 'Welcome to Skypass: 20% off your first fare')
on conflict (code) do nothing;

create or replace function public.check_promo(p_code text)
returns table (code text, percent_off int, description text)
language sql
stable
security definer
set search_path = ''
as $$
    select p.code, p.percent_off, p.description
    from public.promo_codes p
    where p.code = upper(trim(p_code))
      and p.active
      and (p.valid_until is null or p.valid_until >= current_date);
$$;
revoke execute on function public.check_promo(text) from public, anon;
grant execute on function public.check_promo(text) to authenticated;

-- ---------------------------------------------------------------------------------------------
-- Who flies on each booking, plus promo on the booking.
-- ---------------------------------------------------------------------------------------------
alter table public.bookings
    add column if not exists promo_code text,
    add column if not exists discount_usd numeric(10, 2) not null default 0;

create table if not exists public.booking_passengers (
    booking_id      uuid not null references public.bookings (id) on delete cascade,
    passenger_id    uuid not null references public.passengers (id),
    primary key (booking_id, passenger_id)
);
alter table public.booking_passengers enable row level security;
create policy "own booking passengers" on public.booking_passengers for select to authenticated
    using (exists (select 1 from public.bookings b where b.id = booking_id and b.user_id = (select auth.uid())));
create index if not exists booking_passengers_passenger_idx on public.booking_passengers (passenger_id);

-- ---------------------------------------------------------------------------------------------
-- create_booking v2: travellers + promo. Total = fare − promo + 12% tax on the discounted fare
-- + legroom seat + $35 per bag. Idempotent per (user, key).
-- ---------------------------------------------------------------------------------------------
drop function if exists public.create_booking(uuid, text, int, text, int, uuid);
create function public.create_booking(
    p_instance_id uuid,
    p_cabin text,
    p_passengers int,
    p_seat text,
    p_bags int,
    p_idempotency_key uuid,
    p_passenger_ids uuid[] default '{}',
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
    if p_passengers not between 1 and 9 or coalesce(p_bags, 0) not between 0 and 3 then
        raise exception 'invalid_quantity' using errcode = '22023';
    end if;
    if p_seat !~ '^1[2-6][A-F]$' then
        raise exception 'invalid_seat' using errcode = '22023';
    end if;
    if p_seat = any (public.preassigned_seats()) then
        raise exception 'seat_taken' using errcode = '23505';
    end if;
    if exists (
        select 1 from unnest(coalesce(p_passenger_ids, '{}')) as pid
        where not exists (select 1 from public.passengers p where p.id = pid and p.user_id = v_user)
    ) then
        raise exception 'invalid_passenger' using errcode = '22023';
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

    v_fare := v_price * p_passengers;
    v_discount := case when v_percent is null then 0 else round(v_fare * v_percent / 100.0) end;
    v_taxes := round((v_fare - v_discount) * 0.12);
    v_seat_fee := case when left(p_seat, 2) = '12' then 39 else 0 end;
    v_bags := 35 * coalesce(p_bags, 0);

    begin
        insert into public.bookings (
            user_id, instance_id, cabin, passengers, seat, bags,
            fare_usd, taxes_usd, seat_fee_usd, bags_usd, total_usd, idempotency_key, promo_code, discount_usd
        ) values (
            v_user, p_instance_id, p_cabin, p_passengers, p_seat, coalesce(p_bags, 0),
            v_fare, v_taxes, v_seat_fee, v_bags, v_fare - v_discount + v_taxes + v_seat_fee + v_bags,
            p_idempotency_key, v_promo, v_discount
        )
        returning * into v_booking;
    exception when unique_violation then
        raise exception 'seat_taken' using errcode = '23505';
    end;

    insert into public.booking_passengers (booking_id, passenger_id)
    select v_booking.id, pid from unnest(coalesce(p_passenger_ids, '{}')) as pid
    on conflict do nothing;

    return v_booking;
end;
$$;
revoke execute on function public.create_booking(uuid, text, int, text, int, uuid, uuid[], text) from public, anon;
grant execute on function public.create_booking(uuid, text, int, text, int, uuid, uuid[], text) to authenticated;

-- ---------------------------------------------------------------------------------------------
-- Boarding pass for one of the user's bookings (security invoker: RLS keeps it to the owner).
-- ---------------------------------------------------------------------------------------------
create or replace function public.boarding_pass(p_booking_id uuid)
returns table (
    booking_id uuid, pnr text, status text, seat text, cabin text, boarding_zone text, bags int, total_usd numeric,
    airline_code text, airline_name text, airline_color text, airline_logo text, flight_number text, aircraft text,
    origin text, origin_city text, destination text, destination_city text,
    departure_local timestamp, arrival_local timestamp, duration_minutes int, stops int, stop_airport text,
    gate text, boarding_time time, flight_status text, passenger_names text[]
)
language sql
stable
set search_path = ''
as $$
    select
        b.id, b.pnr, b.status, b.seat, b.cabin,
        case
            when b.cabin in ('business', 'first') then 'Priority'
            when left(b.seat, 2)::int <= 14 then 'Group 2'
            else 'Group 3'
        end,
        b.bags, b.total_usd,
        a.code, a.name, a.brand_color, a.logo_url, f.flight_number, f.aircraft,
        f.origin::text, o.city, f.destination::text, d.city,
        fi.departure_date + f.departure_time,
        ((((fi.departure_date + f.departure_time) at time zone o.tz)
            + make_interval(mins => f.duration_minutes)) at time zone d.tz),
        f.duration_minutes, f.stops, f.stop_airport::text, fi.gate, fi.boarding_time, fi.status,
        coalesce(
            (select array_agg(p.full_name order by p.is_primary desc, p.created_at)
             from public.booking_passengers bp join public.passengers p on p.id = bp.passenger_id
             where bp.booking_id = b.id),
            '{}'
        )
    from public.bookings b
    join public.flight_instances fi on fi.id = b.instance_id
    join public.flights f on f.id = fi.flight_id
    join public.airlines a on a.code = f.airline_code
    join public.airports o on o.code = f.origin
    join public.airports d on d.code = f.destination
    where b.id = p_booking_id and b.user_id = (select auth.uid());
$$;
revoke execute on function public.boarding_pass(uuid) from public, anon;
grant execute on function public.boarding_pass(uuid) to authenticated;
