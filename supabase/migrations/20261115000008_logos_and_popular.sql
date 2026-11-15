-- Search and Home now carry airline logos; Home gets "Popular from …" lists for Oceania, the Gulf,
-- Egypt, Saudi Arabia, Belarus and Russia, priced from the cheapest real flight on each route.

-- ---------------------------------------------------------------------------------------------
-- search_flights + next_trip: add airline_logo (and colour for the trip card).
-- Return types change, so the functions are dropped and recreated.
-- ---------------------------------------------------------------------------------------------
drop function if exists public.search_flights(text, text, date, text, int);
create function public.search_flights(
    p_origin text,
    p_destination text,
    p_date date,
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
    with results as (
        select
            fi.id as instance_id, f.id as flight_id, a.code as airline_code, a.name as airline_name,
            a.brand_color as airline_color, a.logo_url as airline_logo, f.flight_number, f.aircraft,
            f.origin::text as origin, o.city as origin_city, f.destination::text as destination, d.city as destination_city,
            (fi.departure_date + f.departure_time) as departure_local,
            ((((fi.departure_date + f.departure_time) at time zone o.tz)
                + make_interval(mins => f.duration_minutes)) at time zone d.tz) as arrival_local,
            f.duration_minutes, f.stops, f.stop_airport::text as stop_airport, fi.gate, fi.boarding_time,
            fi.status, fi.delay_minutes, f.rating, f.perks,
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
        r.instance_id, r.flight_id, r.airline_code, r.airline_name, r.airline_color, r.airline_logo,
        r.flight_number, r.aircraft, r.origin, r.origin_city, r.destination, r.destination_city,
        r.departure_local, r.arrival_local, r.duration_minutes, r.stops, r.stop_airport,
        r.gate, r.boarding_time, r.status, r.delay_minutes, r.rating, r.perks,
        r.price_usd,
        r.price_usd * greatest(p_passengers, 1),
        coalesce(r.stops = 0 and r.price_usd = min(r.price_usd) filter (where r.stops = 0) over (), false)
    from results r
    order by r.price_usd, r.departure_local;
$$;

drop function if exists public.next_trip();
create function public.next_trip()
returns table (
    booking_id uuid, pnr text, seat text, cabin text, instance_id uuid,
    airline_code text, airline_name text, airline_color text, airline_logo text, flight_number text,
    origin text, origin_city text, destination text, destination_city text,
    departure_local timestamp, duration_minutes int, gate text, boarding_time time, status text, delay_minutes int
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
        f.duration_minutes, fi.gate, fi.boarding_time, fi.status, fi.delay_minutes
    from public.bookings b
    join public.flight_instances fi on fi.id = b.instance_id
    join public.flights f on f.id = fi.flight_id
    join public.airlines a on a.code = f.airline_code
    join public.airports o on o.code = f.origin
    join public.airports d on d.code = f.destination
    where b.user_id = (select auth.uid())
      and b.status = 'confirmed'
      and ((fi.departure_date + f.departure_time) at time zone o.tz) > now() - interval '3 hours'
    order by ((fi.departure_date + f.departure_time) at time zone o.tz)
    limit 1;
$$;
revoke execute on function public.next_trip() from public, anon;
grant execute on function public.next_trip() to authenticated;

-- ---------------------------------------------------------------------------------------------
-- "Popular from …" for more home airports. Photos: Wikimedia Commons 500px thumbnails.
-- ---------------------------------------------------------------------------------------------
with photos(code, url) as (values
    ('AER', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/47/RosaSki3.jpg/500px-RosaSki3.jpg'),
    ('AKL', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/f6/Sky_Tower%2C_Auckland_-_0206_%28cropped%29.jpg/500px-Sky_Tower%2C_Auckland_-_0206_%28cropped%29.jpg'),
    ('AMM', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e8/Al_Deir_Petra.JPG/500px-Al_Deir_Petra.JPG'),
    ('AYT', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/75/Antalya_kalei%C3%A7i_2.jpg/500px-Antalya_kalei%C3%A7i_2.jpg'),
    ('BKK', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/2/2a/%E0%B9%80%E0%B8%88%E0%B8%94%E0%B8%B5%E0%B8%A2%E0%B9%8C%E0%B8%9B%E0%B8%A3%E0%B8%B0%E0%B8%98%E0%B8%B2%E0%B8%99%E0%B8%97%E0%B8%A3%E0%B8%87%E0%B8%9B%E0%B8%A3%E0%B8%B2%E0%B8%87%E0%B8%84%E0%B9%8C%E0%B8%A7%E0%B8%B1%E0%B8%94%E0%B8%AD%E0%B8%A3%E0%B8%B8%E0%B8%932.jpg/500px-%E0%B9%80%E0%B8%88%E0%B8%94%E0%B8%B5%E0%B8%A2%E0%B9%8C%E0%B8%9B%E0%B8%A3%E0%B8%B0%E0%B8%98%E0%B8%B2%E0%B8%99%E0%B8%97%E0%B8%A3%E0%B8%87%E0%B8%9B%E0%B8%A3%E0%B8%B2%E0%B8%87%E0%B8%84%E0%B9%8C%E0%B8%A7%E0%B8%B1%E0%B8%94%E0%B8%AD%E0%B8%A3%E0%B8%B8%E0%B8%932.jpg'),
    ('CAI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/9/96/Pyramids_of_the_Giza_Necropolis.jpg/500px-Pyramids_of_the_Giza_Necropolis.jpg'),
    ('DME', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/18/Saint_Basil%27s_Cathedral_in_Moscow.jpg/500px-Saint_Basil%27s_Cathedral_in_Moscow.jpg'),
    ('DXB', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/9/90/Burj_Khalifa_%28worlds_tallest_building%29_and_the_Dubai_skyline_%2825781049892%29.jpg/500px-Burj_Khalifa_%28worlds_tallest_building%29_and_the_Dubai_skyline_%2825781049892%29.jpg'),
    ('HND', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/88/Shibuya_Crossing%2C_Aerial.jpg/500px-Shibuya_Crossing%2C_Aerial.jpg'),
    ('HNL', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/fd/City_of_Waikiki_view.jpg/500px-City_of_Waikiki_view.jpg'),
    ('HRG', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d6/Hurghada_Hotels_R03.jpg/500px-Hurghada_Hotels_R03.jpg'),
    ('IST', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/4a/Hagia_Sophia_%28228968325%29.jpeg/500px-Hagia_Sophia_%28228968325%29.jpeg'),
    ('LED', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/18/Auferstehungskirche_%28Sankt_Petersburg%29.JPG/500px-Auferstehungskirche_%28Sankt_Petersburg%29.JPG'),
    ('LHR', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/59/Tower_Bridge_at_Dawn.jpg/500px-Tower_Bridge_at_Dawn.jpg'),
    ('MSQ', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/40/View_of_Minsk_%28180819%29_09.jpg/500px-View_of_Minsk_%28180819%29_09.jpg'),
    ('NAN', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/31/Mamanuca.jpg/500px-Mamanuca.jpg'),
    ('NRT', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/43/Sensoji_2023.jpg/500px-Sensoji_2023.jpg'),
    ('OOL', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/1f/Surfers_Paradise%2C_QLD_skyline.jpg/500px-Surfers_Paradise%2C_QLD_skyline.jpg'),
    ('SIN', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c7/Marina_Bay_Sands_%28I%29.jpg/500px-Marina_Bay_Sands_%28I%29.jpg'),
    ('SVO', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/18/Saint_Basil%27s_Cathedral_in_Moscow.jpg/500px-Saint_Basil%27s_Cathedral_in_Moscow.jpg'),
    ('SYD', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e1/Sydney_Opera_House_2026.jpg/500px-Sydney_Opera_House_2026.jpg'),
    ('ZQN', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c9/Queenstown_1_%288168013172%29.jpg/500px-Queenstown_1_%288168013172%29.jpg')
),
picks(home, dest, category, sort_order) as (values
    ('AKL', 'SYD', 'Cities', 1), ('AKL', 'RAR', 'Islands', 2), ('AKL', 'NAN', 'Islands', 3), ('AKL', 'ZQN', 'Mountains', 4), ('AKL', 'HNL', 'Beaches', 5), ('AKL', 'NRT', 'Cities', 6),
    ('SYD', 'AKL', 'Cities', 1), ('SYD', 'ZQN', 'Mountains', 2), ('SYD', 'NAN', 'Islands', 3), ('SYD', 'SIN', 'Cities', 4), ('SYD', 'HND', 'Cities', 5), ('SYD', 'HNL', 'Beaches', 6),
    ('MEL', 'AKL', 'Cities', 1), ('MEL', 'OOL', 'Beaches', 2), ('MEL', 'SIN', 'Cities', 3), ('MEL', 'ZQN', 'Mountains', 4), ('MEL', 'BKK', 'Cities', 5),
    ('MSQ', 'IST', 'Cities', 1), ('MSQ', 'DXB', 'Cities', 2), ('MSQ', 'AER', 'Mountains', 3), ('MSQ', 'AYT', 'Beaches', 4), ('MSQ', 'HRG', 'Beaches', 5), ('MSQ', 'LED', 'Cities', 6),
    ('SVO', 'DXB', 'Cities', 1), ('SVO', 'IST', 'Cities', 2), ('SVO', 'AER', 'Mountains', 3), ('SVO', 'HRG', 'Beaches', 4), ('SVO', 'MSQ', 'Cities', 5), ('SVO', 'LED', 'Cities', 6),
    ('DXB', 'LHR', 'Cities', 1), ('DXB', 'IST', 'Cities', 2), ('DXB', 'CAI', 'Cities', 3), ('DXB', 'BKK', 'Cities', 4), ('DXB', 'DME', 'Cities', 5), ('DXB', 'SYD', 'Cities', 6),
    ('DOH', 'LHR', 'Cities', 1), ('DOH', 'BKK', 'Cities', 2), ('DOH', 'IST', 'Cities', 3), ('DOH', 'CAI', 'Cities', 4), ('DOH', 'SYD', 'Cities', 5),
    ('RUH', 'DXB', 'Cities', 1), ('RUH', 'CAI', 'Cities', 2), ('RUH', 'IST', 'Cities', 3), ('RUH', 'AMM', 'Cities', 4), ('RUH', 'LHR', 'Cities', 5),
    ('JED', 'CAI', 'Cities', 1), ('JED', 'DXB', 'Cities', 2), ('JED', 'IST', 'Cities', 3), ('JED', 'AMM', 'Cities', 4), ('JED', 'LHR', 'Cities', 5),
    ('CAI', 'DXB', 'Cities', 1), ('CAI', 'IST', 'Cities', 2), ('CAI', 'AMM', 'Cities', 3), ('CAI', 'LHR', 'Cities', 4), ('CAI', 'SVO', 'Cities', 5)
),
tones(i, tone) as (values (0, '#CDEBE6'), (1, '#DEDCF6'), (2, '#F9DCD2'), (3, '#F7EBC4'), (4, '#CFE0F2'), (5, '#E6F4F1')),
priced as (
    select p.*, a.city, a.country,
        (select min(f.base_fare_usd) from public.flights f where f.origin = p.home and f.destination = p.dest) as fare
    from picks p join public.airports a on a.code = p.dest
)
insert into public.destinations (city, country, airport_code, category, from_price_usd, tone, image_url, popular_from, sort_order)
select pr.city, pr.country, pr.dest, pr.category, round(pr.fare), t.tone, ph.url, pr.home, pr.sort_order
from priced pr
join tones t on t.i = (pr.sort_order - 1) % 6
left join photos ph on ph.code = pr.dest
where pr.fare is not null
  and not exists (select 1 from public.destinations x where x.popular_from = pr.home and x.airport_code = pr.dest);
