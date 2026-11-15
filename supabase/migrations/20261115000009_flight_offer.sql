-- One departure, priced like a search result. The booking screen opens from an instance id.
create or replace function public.flight_offer(
    p_instance_id uuid,
    p_cabin text default 'economy',
    p_passengers int default 1
)
returns table (
    instance_id uuid, flight_id uuid, airline_code text, airline_name text, airline_color text, airline_logo text,
    flight_number text, aircraft text, origin text, origin_city text, destination text, destination_city text,
    departure_local timestamp, arrival_local timestamp, duration_minutes int, stops int, stop_airport text,
    gate text, boarding_time time, status text, delay_minutes int, rating numeric, perks text[],
    price_usd numeric, total_usd numeric, is_best_value boolean
)
language sql
stable
set search_path = ''
as $$
    select
        fi.id, f.id, a.code, a.name, a.brand_color, a.logo_url, f.flight_number, f.aircraft,
        f.origin::text, o.city, f.destination::text, d.city,
        fi.departure_date + f.departure_time,
        ((((fi.departure_date + f.departure_time) at time zone o.tz)
            + make_interval(mins => f.duration_minutes)) at time zone d.tz),
        f.duration_minutes, f.stops, f.stop_airport::text, fi.gate, fi.boarding_time, fi.status, fi.delay_minutes,
        f.rating, f.perks,
        round(f.base_fare_usd * fi.date_factor * public.cabin_factor(p_cabin)),
        round(f.base_fare_usd * fi.date_factor * public.cabin_factor(p_cabin)) * greatest(p_passengers, 1),
        false
    from public.flight_instances fi
    join public.flights f on f.id = fi.flight_id
    join public.airlines a on a.code = f.airline_code
    join public.airports o on o.code = f.origin
    join public.airports d on d.code = f.destination
    where fi.id = p_instance_id;
$$;
