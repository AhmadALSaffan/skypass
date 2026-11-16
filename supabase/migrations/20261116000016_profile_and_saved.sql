-- Profile and Saved:
--   * profile_stats(): trips, countries and miles from the traveller's own bookings, and the tier
--     those miles earn (Blue, Silver 10k, Gold 25k, Platinum 50k).
--   * saved_flights(), saved_hotels(), saved_destinations(p_origin): what the Saved tab lists.
--   * Travellers may only change their name and home airport, never their tier or miles.

revoke update on public.profiles from authenticated;
grant update (full_name, home_airport) on public.profiles to authenticated;

alter table public.profiles drop constraint if exists profiles_tier_check;
alter table public.profiles add constraint profiles_tier_check check (tier in ('Blue', 'Silver', 'Gold', 'Platinum'));

-- Great-circle distance in statute miles.
create or replace function public.miles_between(a text, b text)
returns int
language sql
immutable
set search_path = ''
as $$
    select coalesce(round(3958.8 * 2 * asin(sqrt(
        power(sin(radians(y.lat - x.lat) / 2), 2) +
        cos(radians(x.lat)) * cos(radians(y.lat)) * power(sin(radians(y.lon - x.lon) / 2), 2)
    )))::int, 0)
    from public.airports x, public.airports y
    where x.code = upper(a) and y.code = upper(b);
$$;

create or replace function public.profile_stats()
returns table (trips int, countries int, miles int, tier text, next_tier text, miles_to_next int)
language sql
stable
set search_path = ''
as $$
    with me as (select (select auth.uid()) as id),
    flights as (
        select f.origin, f.destination
        from public.bookings b
        join public.flight_instances fi on fi.id = b.instance_id
        join public.flights f on f.id = fi.flight_id
        where b.user_id = (select id from me) and b.status = 'confirmed'
    ),
    stays as (
        select h.city
        from public.hotel_reservations r join public.hotels h on h.id = r.hotel_id
        where r.user_id = (select id from me) and r.status = 'confirmed'
    ),
    cars as (
        select c.location_code
        from public.rental_reservations r join public.cars c on c.id = r.car_id
        where r.user_id = (select id from me) and r.status = 'reserved'
    ),
    visited as (
        select a.country from flights f join public.airports a on a.code = f.destination
        union
        select a.country from stays s join public.airports a on a.city = s.city
        union
        select a.country from cars c join public.airports a on a.code = c.location_code
    ),
    totals as (
        select
            ((select count(*) from flights) + (select count(*) from stays) + (select count(*) from cars))::int as trips,
            (select count(distinct country) from visited)::int as countries,
            coalesce((select sum(public.miles_between(origin, destination)) from flights), 0)::int as miles
    )
    select
        t.trips, t.countries, t.miles,
        case when t.miles >= 50000 then 'Platinum' when t.miles >= 25000 then 'Gold' when t.miles >= 10000 then 'Silver' else 'Blue' end,
        case when t.miles >= 50000 then null when t.miles >= 25000 then 'Platinum' when t.miles >= 10000 then 'Gold' else 'Silver' end,
        case when t.miles >= 50000 then 0 when t.miles >= 25000 then 50000 - t.miles when t.miles >= 10000 then 25000 - t.miles else 10000 - t.miles end
    from totals t;
$$;
revoke execute on function public.profile_stats() from public, anon;
grant execute on function public.profile_stats() to authenticated;

-- Saved departures, soonest first, with what the card needs and today's economy fare.
create or replace function public.saved_flights()
returns table (
    instance_id uuid, saved_at timestamptz,
    airline_code text, airline_name text, airline_color text, airline_logo text, flight_number text,
    origin text, origin_city text, destination text, destination_city text,
    departure_local timestamp, duration_minutes int, stops int, fare_usd numeric, status text
)
language sql
stable
set search_path = ''
as $$
    select
        fi.id, s.created_at,
        a.code, a.name, a.brand_color, a.logo_url, f.flight_number,
        f.origin::text, o.city, f.destination::text, d.city,
        fi.departure_date + f.departure_time, f.duration_minutes, f.stops,
        round(f.base_fare_usd * fi.date_factor), fi.status
    from public.saved_items s
    join public.flight_instances fi on fi.id = s.item_id
    join public.flights f on f.id = fi.flight_id
    join public.airlines a on a.code = f.airline_code
    join public.airports o on o.code = f.origin
    join public.airports d on d.code = f.destination
    where s.user_id = (select auth.uid()) and s.kind = 'flight'
    order by fi.departure_date + f.departure_time;
$$;
revoke execute on function public.saved_flights() from public, anon;
grant execute on function public.saved_flights() to authenticated;

create or replace function public.saved_hotels()
returns table (
    id uuid, city text, name text, area text, distance_km numeric, stars int, rating numeric,
    review_count int, price_usd numeric, free_cancel boolean, amenities text[], image_url text,
    saved_at timestamptz
)
language sql
stable
set search_path = ''
as $$
    select h.id, h.city, h.name, h.area, h.distance_km, h.stars, h.rating, h.review_count,
        h.price_usd, h.free_cancel, h.amenities, h.image_url, s.created_at
    from public.saved_items s
    join public.hotels h on h.id = s.item_id
    where s.user_id = (select auth.uid()) and s.kind = 'hotel'
    order by s.created_at desc;
$$;
revoke execute on function public.saved_hotels() from public, anon;
grant execute on function public.saved_hotels() to authenticated;

-- Saved destinations, priced from the traveller's airport like Explore.
create or replace function public.saved_destinations(p_origin text)
returns table (
    id uuid, city text, country text, airport_code text, category text, from_price_usd numeric,
    tone text, image_url text, from_home boolean, saved_at timestamptz
)
language sql
stable
set search_path = ''
as $$
    select d.id, d.city, d.country, d.airport_code::text, d.category,
        coalesce(f.fare, d.from_price_usd), d.tone, d.image_url, f.fare is not null, s.created_at
    from public.saved_items s
    join public.destinations d on d.id = s.item_id
    left join lateral (
        select min(fl.base_fare_usd) as fare from public.flights fl
        where fl.origin = upper(p_origin) and fl.destination = d.airport_code
    ) f on true
    where s.user_id = (select auth.uid()) and s.kind = 'destination'
    order by s.created_at desc;
$$;
revoke execute on function public.saved_destinations(text) from public, anon;
grant execute on function public.saved_destinations(text) to authenticated;
