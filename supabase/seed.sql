-- Skypass catalog seed. Flight, hotel, car and destination data is taken from the design board;
-- extra routes give Air New Zealand's network (domestic, trans-Tasman, Pacific) something to search.

insert into public.airports (code, city, country, name, tz, lat, lon) values
    ('JFK', 'New York',      'United States',    'John F. Kennedy International',    'America/New_York',     40.6413, -73.7781),
    ('LAX', 'Los Angeles',   'United States',    'Los Angeles International',        'America/Los_Angeles',  33.9416, -118.4085),
    ('SFO', 'San Francisco', 'United States',    'San Francisco International',      'America/Los_Angeles',  37.6213, -122.3790),
    ('DEN', 'Denver',        'United States',    'Denver International',             'America/Denver',       39.8561, -104.6737),
    ('SEA', 'Seattle',       'United States',    'Seattle–Tacoma International',     'America/Los_Angeles',  47.4502, -122.3088),
    ('YYC', 'Calgary',       'Canada',           'Calgary International',            'America/Edmonton',     51.1215, -114.0076),
    ('MEX', 'Mexico City',   'Mexico',           'Benito Juárez International',      'America/Mexico_City',  19.4361, -99.0719),
    ('TQO', 'Tulum',         'Mexico',           'Felipe Carrillo Puerto International', 'America/Cancun',   20.0503, -87.6211),
    ('LHR', 'London',        'United Kingdom',   'Heathrow',                         'Europe/London',        51.4700, -0.4543),
    ('CDG', 'Paris',         'France',           'Charles de Gaulle',                'Europe/Paris',         49.0097, 2.5479),
    ('LIS', 'Lisbon',        'Portugal',         'Humberto Delgado',                 'Europe/Lisbon',        38.7742, -9.1342),
    ('KEF', 'Reykjavík',     'Iceland',          'Keflavík International',           'Atlantic/Reykjavik',   63.9850, -22.6056),
    ('DXB', 'Dubai',         'United Arab Emirates', 'Dubai International',          'Asia/Dubai',           25.2532, 55.3657),
    ('HND', 'Tokyo',         'Japan',            'Haneda',                           'Asia/Tokyo',           35.5494, 139.7798),
    ('KIX', 'Osaka',         'Japan',            'Kansai International',             'Asia/Tokyo',           34.4320, 135.2304),
    ('SYD', 'Sydney',        'Australia',        'Kingsford Smith',                  'Australia/Sydney',     -33.9399, 151.1753),
    ('MEL', 'Melbourne',     'Australia',        'Tullamarine',                      'Australia/Melbourne',  -37.6690, 144.8410),
    ('AKL', 'Auckland',      'New Zealand',      'Auckland Airport',                 'Pacific/Auckland',     -37.0082, 174.7850),
    ('WLG', 'Wellington',    'New Zealand',      'Wellington Airport',               'Pacific/Auckland',     -41.3272, 174.8053),
    ('CHC', 'Christchurch',  'New Zealand',      'Christchurch Airport',             'Pacific/Auckland',     -43.4894, 172.5320),
    ('ZQN', 'Queenstown',    'New Zealand',      'Queenstown Airport',               'Pacific/Auckland',     -45.0211, 168.7392),
    ('BOB', 'Bora Bora',     'French Polynesia', 'Motu Mute',                        'Pacific/Tahiti',       -16.4444, -151.7514);

insert into public.airlines (code, name, brand_color) values
    ('NZ', 'Air New Zealand',   '#000000'),
    ('UA', 'United Airlines',   '#005DAA'),
    ('AS', 'Alaska Airlines',   '#01426A'),
    ('B6', 'JetBlue',           '#003876'),
    ('AA', 'American Airlines', '#0078D2'),
    ('AF', 'Air France',        '#002157'),
    ('DL', 'Delta',             '#003366'),
    ('BA', 'British Airways',   '#075AAA'),
    ('TP', 'TAP Air Portugal',  '#00A651'),
    ('FI', 'Icelandair',        '#003A7D'),
    ('AM', 'Aeromexico',        '#0B2343'),
    ('JL', 'Japan Airlines',    '#C8102E'),
    ('EK', 'Emirates',          '#D71921'),
    ('QF', 'Qantas',            '#E40000');

insert into public.flights
    (airline_code, flight_number, origin, destination, departure_time, duration_minutes, stops, stop_airport, aircraft, base_fare_usd, rating, perks, default_gate)
values
    -- JFK → LAX: the five results on the board.
    ('NZ', 'NZ 5',    'JFK', 'LAX', '07:15', 385, 0, null,  '787-9',      209, 4.6, '{Carry-on,Wi-Fi,Seat select}',        'B22'),
    ('UA', 'UA 2270', 'JFK', 'LAX', '06:00', 515, 1, 'DEN', '737 MAX 9',  214, 4.2, '{Personal item,Wi-Fi}',               'C14'),
    ('AS', 'AS 1235', 'JFK', 'LAX', '15:45', 500, 1, 'SEA', '737-900',    236, 4.3, '{Carry-on,Wi-Fi}',                    'A3'),
    ('B6', 'B6 23',   'JFK', 'LAX', '09:00', 372, 0, null,  'A321',       248, 4.5, '{Carry-on,Free Wi-Fi,Snacks}',        '5'),
    ('AA', 'AA 1',    'JFK', 'LAX', '12:30', 382, 0, null,  'A321T',      312, 4.4, '{Carry-on,Meal,Lie-flat option}',     '8'),
    -- LAX → JFK
    ('NZ', 'NZ 6',    'LAX', 'JFK', '13:00', 330, 0, null,  '787-9',      219, 4.6, '{Carry-on,Wi-Fi,Seat select}',        'B9'),
    ('B6', 'B6 24',   'LAX', 'JFK', '22:30', 315, 0, null,  'A321',       199, 4.5, '{Carry-on,Free Wi-Fi,Snacks}',        '4'),
    ('AA', 'AA 2',    'LAX', 'JFK', '08:00', 325, 0, null,  'A321T',      289, 4.4, '{Carry-on,Meal,Lie-flat option}',     '41'),
    ('UA', 'UA 2271', 'LAX', 'JFK', '06:30', 470, 1, 'DEN', '737 MAX 9',  204, 4.2, '{Personal item,Wi-Fi}',               '72'),
    -- Air New Zealand long haul (the board's "Upcoming trip" and "Fly to Aotearoa" deal).
    ('NZ', 'NZ 2',    'JFK', 'AKL', '19:00', 1070, 0, null, '787-9',      899, 4.7, '{Carry-on,Meal,Skycouch option,Wi-Fi}', 'B22'),
    ('NZ', 'NZ 1',    'AKL', 'JFK', '19:30', 975, 0, null,  '787-9',      919, 4.7, '{Carry-on,Meal,Skycouch option,Wi-Fi}', '8'),
    ('NZ', 'NZ 9',    'LAX', 'AKL', '21:30', 785, 0, null,  '777-300ER',  829, 4.6, '{Carry-on,Meal,Wi-Fi}',               'B3'),
    ('NZ', 'NZ 8',    'AKL', 'SFO', '20:15', 725, 0, null,  '787-9',      849, 4.6, '{Carry-on,Meal,Wi-Fi}',               '6'),
    ('NZ', 'NZ 99',   'AKL', 'HND', '09:45', 660, 0, null,  '787-9',      689, 4.6, '{Carry-on,Meal,Wi-Fi}',               '10'),
    -- Trans-Tasman
    ('NZ', 'NZ 103',  'AKL', 'SYD', '10:00', 220, 0, null,  'A321neo',    249, 4.5, '{Carry-on,Snack,Wi-Fi}',              '7'),
    ('NZ', 'NZ 104',  'SYD', 'AKL', '15:00', 185, 0, null,  'A321neo',    249, 4.5, '{Carry-on,Snack,Wi-Fi}',              '52'),
    ('NZ', 'NZ 123',  'AKL', 'MEL', '11:00', 240, 0, null,  'A321neo',    259, 4.5, '{Carry-on,Snack,Wi-Fi}',              '9'),
    ('QF', 'QF 144',  'AKL', 'SYD', '07:00', 215, 0, null,  '737-800',    239, 4.3, '{Carry-on,Meal}',                     '5'),
    -- Domestic
    ('NZ', 'NZ 401',  'AKL', 'WLG', '07:00', 65,  0, null,  'A320',       89,  4.5, '{Carry-on,Free Wi-Fi}',               '28'),
    ('NZ', 'NZ 402',  'WLG', 'AKL', '08:30', 65,  0, null,  'A320',       89,  4.5, '{Carry-on,Free Wi-Fi}',               '14'),
    ('NZ', 'NZ 521',  'AKL', 'CHC', '08:00', 80,  0, null,  'A320',       99,  4.5, '{Carry-on,Free Wi-Fi}',               '30'),
    ('NZ', 'NZ 522',  'CHC', 'AKL', '10:00', 80,  0, null,  'A320',       99,  4.5, '{Carry-on,Free Wi-Fi}',               '18'),
    ('NZ', 'NZ 615',  'AKL', 'ZQN', '09:30', 110, 0, null,  'A320',       129, 4.6, '{Carry-on,Free Wi-Fi}',               '31'),
    ('NZ', 'NZ 616',  'ZQN', 'AKL', '12:00', 105, 0, null,  'A320',       129, 4.6, '{Carry-on,Free Wi-Fi}',               '3'),
    -- From New York (the board's "Popular from New York")
    ('AF', 'AF 7',    'JFK', 'CDG', '18:30', 430, 0, null,  'A350-900',   512, 4.4, '{Carry-on,Meal,Wi-Fi}',               '1'),
    ('DL', 'DL 264',  'JFK', 'CDG', '19:30', 435, 0, null,  'A330-900',   548, 4.3, '{Carry-on,Meal,Wi-Fi}',               'B30'),
    ('BA', 'BA 178',  'JFK', 'LHR', '19:00', 415, 0, null,  'A380',       498, 4.3, '{Carry-on,Meal}',                     '7'),
    ('BA', 'BA 117',  'LHR', 'JFK', '08:30', 480, 0, null,  'A380',       520, 4.3, '{Carry-on,Meal}',                     'B42'),
    ('TP', 'TP 210',  'JFK', 'LIS', '20:30', 405, 0, null,  'A330neo',    412, 4.2, '{Carry-on,Meal}',                     '6'),
    ('FI', 'FI 614',  'JFK', 'KEF', '20:40', 345, 0, null,  '757-200',    389, 4.2, '{Carry-on,Wi-Fi}',                    '7'),
    ('AM', 'AM 409',  'JFK', 'MEX', '08:00', 300, 0, null,  '737 MAX 8',  298, 4.1, '{Carry-on,Snack}',                    '10'),
    ('JL', 'JL 5',    'JFK', 'HND', '13:25', 860, 0, null,  '777-300ER',  845, 4.7, '{Carry-on,Meal,Wi-Fi}',               '9'),
    -- Onboarding routes
    ('EK', 'EK 312',  'DXB', 'HND', '02:40', 580, 0, null,  'A380',       845, 4.6, '{Carry-on,Meal,Wi-Fi}',               'B22'),
    ('EK', 'EK 2',    'LHR', 'DXB', '14:00', 420, 0, null,  'A380',       560, 4.6, '{Carry-on,Meal,Wi-Fi}',               'A10');

-- 90 days of departures from today. The board's dates (Wed 18 – Sun 22 Nov 2026) get its exact
-- price factors; other days vary a little, with Fridays and Sundays dearer.
with days as (
    select
        d::date as day,
        case d::date
            when date '2026-11-18' then 1.12
            when date '2026-11-19' then 0.94
            when date '2026-11-20' then 1.00
            when date '2026-11-21' then 1.08
            when date '2026-11-22' then 1.21
            else round((0.92
                + (abs(hashtext(d::date::text)::bigint) % 22) / 100.0
                + case when extract(isodow from d) in (5, 7) then 0.06 else 0 end)::numeric, 2)
        end as factor
    from generate_series(current_date, current_date + 89, interval '1 day') as d
)
insert into public.flight_instances (flight_id, departure_date, gate, boarding_time, date_factor)
select
    f.id,
    days.day,
    coalesce(
        f.default_gate,
        chr(65 + (abs(hashtext(f.flight_number)::bigint) % 4)::int) || (1 + abs(hashtext(f.flight_number)::bigint) % 30)::text
    ),
    f.departure_time - interval '40 minutes',
    days.factor
from public.flights f
cross join days;

insert into public.destinations (city, country, airport_code, category, from_price_usd, tone, popular_from, sort_order) values
    -- Explore grid
    ('Queenstown',  'New Zealand',      'ZQN', 'Mountains', 689,  '#CDEBE6', null, 1),
    ('Lisbon',      'Portugal',         'LIS', 'Cities',    412,  '#F7EBC4', null, 2),
    ('Bora Bora',   'French Polynesia', 'BOB', 'Islands',   1240, '#CFE0F2', null, 3),
    ('Tulum',       'Mexico',           'TQO', 'Beaches',   356,  '#F9DCD2', null, 4),
    ('Kyoto',       'Japan',            'KIX', 'Cities',    845,  '#DEDCF6', null, 5),
    ('Banff',       'Canada',           'YYC', 'Mountains', 298,  '#E6F4F1', null, 6),
    -- Home: "Popular from New York"
    ('Lisbon',      'Portugal',         'LIS', 'Cities',    412,  '#CDEBE6', 'JFK', 1),
    ('Reykjavík',   'Iceland',          'KEF', 'Cities',    389,  '#DEDCF6', 'JFK', 2),
    ('Mexico City', 'Mexico',           'MEX', 'Cities',    298,  '#F9DCD2', 'JFK', 3),
    ('Tokyo',       'Japan',            'HND', 'Cities',    845,  '#F7EBC4', 'JFK', 4);

insert into public.deals (title, airline_code, origin, destination, nonstop, from_price_usd, tone) values
    ('Fly to Aotearoa this summer', 'NZ', 'JFK', 'AKL', true, 899, '#CFE0F2');

insert into public.hotels (city, name, area, distance_km, stars, rating, review_count, price_usd, free_cancel, amenities, image_url) values
    ('Auckland', 'Kōwhai Airport Inn', 'Auckland Airport', 0.4, 4, 8.6, 2140, 189, true,  '{Free Wi-Fi,Shuttle,Breakfast}',
        'https://images.unsplash.com/photo-1590490360182-c33d57733427?w=700&h=400&q=75&auto=format&fit=crop'),
    ('Auckland', 'Waitematā House',    'Wynyard Quarter',  21,  5, 9.3, 1408, 412, true,  '{Pool,Spa,Harbour view}',
        'https://images.unsplash.com/photo-1542314831-068cd1dbfeeb?w=700&h=400&q=75&auto=format&fit=crop'),
    ('Auckland', 'Harbourline Hotel',  'Viaduct Harbour',  20,  4, 8.9, 986,  268, false, '{Rooftop bar,Gym}',
        'https://images.unsplash.com/photo-1551882547-ff40c63fe5fa?w=700&h=400&q=75&auto=format&fit=crop'),
    ('Auckland', 'Pōhutukawa Lodge',   'Devonport',        24,  5, 9.1, 612,  236, true,  '{Pool,Beach access,Breakfast}',
        'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=700&h=400&q=75&auto=format&fit=crop');

insert into public.cars (location_code, name, type, seats, bags, price_per_day_usd, supplier, stage_color, image_path, sort_order) values
    ('AKL', 'Kia Cerato',       'Compact',  5, 2, 41,  'Kiwi Rentals',     '#F9DCD2', 'cars/kia-cerato.png',       1),
    ('AKL', 'Nissan Note',      'Compact',  5, 1, 36,  'Harbour Car Hire', '#E6E8F2', 'cars/nissan-note.png',      2),
    ('AKL', 'Jeep Wrangler',    'SUV',      4, 2, 92,  'Southern Drive',   '#CFE0F2', 'cars/jeep-wrangler.png',    1),
    ('AKL', 'Nissan Juke',      'SUV',      5, 2, 58,  'Kiwi Rentals',     '#F7EBC4', 'cars/nissan-juke.png',      2),
    ('AKL', 'Tesla Model 3',    'Electric', 5, 2, 84,  'Volt NZ',          '#CFE0F2', 'cars/tesla-model-3.png',    1),
    ('AKL', 'Tesla Model S',    'Electric', 5, 3, 112, 'Volt NZ',          '#F9DCD2', 'cars/tesla-model-s.png',    2),
    ('AKL', 'Ford Mustang',     'Premium',  4, 2, 129, 'Harbour Car Hire', '#DEDCF6', 'cars/ford-mustang.png',     1),
    ('AKL', 'Chevrolet Camaro', 'Premium',  4, 2, 119, 'Kiwi Rentals',     '#F7EBC4', 'cars/chevrolet-camaro.png', 2);
