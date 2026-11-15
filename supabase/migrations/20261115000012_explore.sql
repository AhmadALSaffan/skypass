-- Explore: a bigger featured grid with photos, the deal photo, and destination prices from the
-- traveller's own airport.

with photos(code, url) as (values
    ('SYD', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e1/Sydney_Opera_House_2026.jpg/500px-Sydney_Opera_House_2026.jpg'),
    ('NAN', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/31/Mamanuca.jpg/500px-Mamanuca.jpg'),
    ('HNL', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/fd/City_of_Waikiki_view.jpg/500px-City_of_Waikiki_view.jpg'),
    ('OOL', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/1f/Surfers_Paradise%2C_QLD_skyline.jpg/500px-Surfers_Paradise%2C_QLD_skyline.jpg'),
    ('DXB', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/9/90/Burj_Khalifa_%28worlds_tallest_building%29_and_the_Dubai_skyline_%2825781049892%29.jpg/500px-Burj_Khalifa_%28worlds_tallest_building%29_and_the_Dubai_skyline_%2825781049892%29.jpg'),
    ('IST', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/4a/Hagia_Sophia_%28228968325%29.jpeg/500px-Hagia_Sophia_%28228968325%29.jpeg'),
    ('AMM', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e8/Al_Deir_Petra.JPG/500px-Al_Deir_Petra.JPG'),
    ('HRG', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d6/Hurghada_Hotels_R03.jpg/500px-Hurghada_Hotels_R03.jpg'),
    ('AYT', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/75/Antalya_kalei%C3%A7i_2.jpg/500px-Antalya_kalei%C3%A7i_2.jpg'),
    ('AER', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/47/RosaSki3.jpg/500px-RosaSki3.jpg'),
    ('LED', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/18/Auferstehungskirche_%28Sankt_Petersburg%29.JPG/500px-Auferstehungskirche_%28Sankt_Petersburg%29.JPG'),
    ('SIN', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c7/Marina_Bay_Sands_%28I%29.jpg/500px-Marina_Bay_Sands_%28I%29.jpg'),
    ('HND', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/88/Shibuya_Crossing%2C_Aerial.jpg/500px-Shibuya_Crossing%2C_Aerial.jpg'),
    ('LHR', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/59/Tower_Bridge_at_Dawn.jpg/500px-Tower_Bridge_at_Dawn.jpg'),
    ('CAI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/9/96/Pyramids_of_the_Giza_Necropolis.jpg/500px-Pyramids_of_the_Giza_Necropolis.jpg'),
    ('MSQ', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/40/View_of_Minsk_%28180819%29_09.jpg/500px-View_of_Minsk_%28180819%29_09.jpg')
),
picks(city, country, code, category, sort_order, tone) as (values
    ('Sydney', 'Australia', 'SYD', 'Cities', 7, '#CFE0F2'),
    ('Nadi', 'Fiji', 'NAN', 'Islands', 8, '#CDEBE6'),
    ('Honolulu', 'United States', 'HNL', 'Beaches', 9, '#F7EBC4'),
    ('Gold Coast', 'Australia', 'OOL', 'Beaches', 10, '#F9DCD2'),
    ('Dubai', 'United Arab Emirates', 'DXB', 'Cities', 11, '#F7EBC4'),
    ('Istanbul', 'Türkiye', 'IST', 'Cities', 12, '#DEDCF6'),
    ('Petra', 'Jordan', 'AMM', 'Mountains', 13, '#F9DCD2'),
    ('Hurghada', 'Egypt', 'HRG', 'Beaches', 14, '#CFE0F2'),
    ('Antalya', 'Türkiye', 'AYT', 'Beaches', 15, '#CDEBE6'),
    ('Sochi', 'Russia', 'AER', 'Mountains', 16, '#E6F4F1'),
    ('Saint Petersburg', 'Russia', 'LED', 'Cities', 17, '#DEDCF6'),
    ('Singapore', 'Singapore', 'SIN', 'Cities', 18, '#CDEBE6'),
    ('Tokyo', 'Japan', 'HND', 'Cities', 19, '#F9DCD2'),
    ('London', 'United Kingdom', 'LHR', 'Cities', 20, '#CFE0F2'),
    ('Cairo', 'Egypt', 'CAI', 'Cities', 21, '#F7EBC4'),
    ('Minsk', 'Belarus', 'MSQ', 'Cities', 22, '#E6F4F1')
)
insert into public.destinations (city, country, airport_code, category, from_price_usd, tone, image_url, popular_from, sort_order)
select p.city, p.country, p.code, p.category,
    coalesce((select min(f.base_fare_usd) from public.flights f where f.destination = p.code), 499),
    p.tone, ph.url, null, p.sort_order
from picks p left join photos ph on ph.code = p.code
where not exists (select 1 from public.destinations d where d.popular_from is null and d.airport_code = p.code);

update public.deals
set image_url = 'https://thumb.wikimedia.org/wikipedia/commons/thumb/b/bc/Auckland_Harbour_Bridge_-_August_2024.jpg/500px-Auckland_Harbour_Bridge_-_August_2024.jpg'
where destination = 'AKL' and image_url is null;

-- Featured destinations priced from the traveller's airport (nonstop or one-stop on Skypass);
-- where there's no flight from there, the catalogue "from" price stays and from_home is false.
create or replace function public.explore_destinations(p_origin text)
returns table (
    id uuid, city text, country text, airport_code text, category text, from_price_usd numeric,
    tone text, image_url text, from_home boolean
)
language sql
stable
set search_path = ''
as $$
    select d.id, d.city, d.country, d.airport_code::text, d.category,
        coalesce(f.fare, d.from_price_usd),
        d.tone, d.image_url, f.fare is not null
    from public.destinations d
    left join lateral (
        select min(fl.base_fare_usd) as fare from public.flights fl
        where fl.origin = upper(p_origin) and fl.destination = d.airport_code
    ) f on true
    where d.popular_from is null and d.airport_code <> upper(p_origin)
    order by d.sort_order;
$$;
