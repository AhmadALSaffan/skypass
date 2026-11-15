-- Global network: Oceania (NZ + Australia + Pacific), the Gulf and Arab world, Russia and Belarus,
-- Europe, Asia and North America. Routes are declared compactly; flights are derived from them
-- (great-circle distance → block time, aircraft from each airline's fleet, fares, departure banks,
-- weekly patterns). Departures for a full year are generated separately (see the next migration).

-- ---------------------------------------------------------------------------------------------
-- Schema additions
-- ---------------------------------------------------------------------------------------------
alter table public.airlines
    add column if not exists logo_url text,
    add column if not exists tier text not null default 'full' check (tier in ('premium', 'full', 'low')),
    add column if not exists rating numeric(2, 1) not null default 4.2,
    -- Aircraft by distance band: [short < 1,500 km, medium < 5,000 km, long].
    add column if not exists fleet text[] not null default '{A320neo,A321neo,787-9}';

alter table public.flights
    add column if not exists days_of_week smallint[] not null default '{1,2,3,4,5,6,7}';

-- Searches go through (flight_id, departure_date); the date-only index is never used and costs space.
drop index if exists public.flight_instances_date_idx;

create table if not exists public.routes (
    airline_code    text not null references public.airlines (code),
    origin          char(3) not null references public.airports (code),
    destination     char(3) not null references public.airports (code),
    daily           smallint not null check (daily between 1 and 6),
    days_per_week   smallint not null default 7 check (days_per_week between 1 and 7),
    primary key (airline_code, origin, destination)
);
alter table public.routes enable row level security;
create policy "catalog is public" on public.routes for select to anon, authenticated using (true);

-- ---------------------------------------------------------------------------------------------
-- Airports
-- ---------------------------------------------------------------------------------------------
insert into public.airports (code, city, country, name, tz, lat, lon) values
    -- Belarus & Russia
    ('MSQ', 'Minsk', 'Belarus', 'Minsk National', 'Europe/Minsk', 53.8825, 28.0307),
    ('SVO', 'Moscow', 'Russia', 'Sheremetyevo', 'Europe/Moscow', 55.9726, 37.4146),
    ('DME', 'Moscow', 'Russia', 'Domodedovo', 'Europe/Moscow', 55.4088, 37.9063),
    ('VKO', 'Moscow', 'Russia', 'Vnukovo', 'Europe/Moscow', 55.5915, 37.2615),
    ('LED', 'Saint Petersburg', 'Russia', 'Pulkovo', 'Europe/Moscow', 59.8003, 30.2625),
    ('AER', 'Sochi', 'Russia', 'Sochi International', 'Europe/Moscow', 43.4499, 39.9566),
    ('KZN', 'Kazan', 'Russia', 'Kazan International', 'Europe/Moscow', 55.6062, 49.2787),
    ('SVX', 'Yekaterinburg', 'Russia', 'Koltsovo', 'Asia/Yekaterinburg', 56.7431, 60.8027),
    ('OVB', 'Novosibirsk', 'Russia', 'Tolmachevo', 'Asia/Novosibirsk', 55.0126, 82.6507),
    -- Gulf & Arab world
    ('AUH', 'Abu Dhabi', 'United Arab Emirates', 'Zayed International', 'Asia/Dubai', 24.4330, 54.6511),
    ('SHJ', 'Sharjah', 'United Arab Emirates', 'Sharjah International', 'Asia/Dubai', 25.3286, 55.5172),
    ('DOH', 'Doha', 'Qatar', 'Hamad International', 'Asia/Qatar', 25.2731, 51.6081),
    ('RUH', 'Riyadh', 'Saudi Arabia', 'King Khalid International', 'Asia/Riyadh', 24.9576, 46.6988),
    ('JED', 'Jeddah', 'Saudi Arabia', 'King Abdulaziz International', 'Asia/Riyadh', 21.6796, 39.1565),
    ('MED', 'Medina', 'Saudi Arabia', 'Prince Mohammad bin Abdulaziz', 'Asia/Riyadh', 24.5534, 39.7051),
    ('DMM', 'Dammam', 'Saudi Arabia', 'King Fahd International', 'Asia/Riyadh', 26.4712, 49.7979),
    ('KWI', 'Kuwait City', 'Kuwait', 'Kuwait International', 'Asia/Kuwait', 29.2266, 47.9689),
    ('BAH', 'Manama', 'Bahrain', 'Bahrain International', 'Asia/Bahrain', 26.2708, 50.6336),
    ('MCT', 'Muscat', 'Oman', 'Muscat International', 'Asia/Muscat', 23.5933, 58.2844),
    ('AMM', 'Amman', 'Jordan', 'Queen Alia International', 'Asia/Amman', 31.7226, 35.9932),
    ('BEY', 'Beirut', 'Lebanon', 'Rafic Hariri International', 'Asia/Beirut', 33.8209, 35.4884),
    ('CAI', 'Cairo', 'Egypt', 'Cairo International', 'Africa/Cairo', 30.1219, 31.4056),
    ('HRG', 'Hurghada', 'Egypt', 'Hurghada International', 'Africa/Cairo', 27.1783, 33.7994),
    ('SSH', 'Sharm El Sheikh', 'Egypt', 'Sharm El Sheikh International', 'Africa/Cairo', 27.9773, 34.3950),
    ('LXR', 'Luxor', 'Egypt', 'Luxor International', 'Africa/Cairo', 25.6710, 32.7066),
    ('CMN', 'Casablanca', 'Morocco', 'Mohammed V International', 'Africa/Casablanca', 33.3675, -7.5898),
    ('RAK', 'Marrakesh', 'Morocco', 'Marrakesh Menara', 'Africa/Casablanca', 31.6069, -8.0363),
    ('TUN', 'Tunis', 'Tunisia', 'Tunis–Carthage', 'Africa/Tunis', 36.8510, 10.2272),
    ('ALG', 'Algiers', 'Algeria', 'Houari Boumediene', 'Africa/Algiers', 36.6910, 3.2154),
    ('BGW', 'Baghdad', 'Iraq', 'Baghdad International', 'Asia/Baghdad', 33.2625, 44.2346),
    ('EBL', 'Erbil', 'Iraq', 'Erbil International', 'Asia/Baghdad', 36.2376, 43.9632),
    -- New Zealand
    ('DUD', 'Dunedin', 'New Zealand', 'Dunedin Airport', 'Pacific/Auckland', -45.9281, 170.1984),
    ('NSN', 'Nelson', 'New Zealand', 'Nelson Airport', 'Pacific/Auckland', -41.2983, 173.2211),
    ('NPE', 'Napier', 'New Zealand', 'Hawke''s Bay Airport', 'Pacific/Auckland', -39.4658, 176.8700),
    ('ROT', 'Rotorua', 'New Zealand', 'Rotorua Airport', 'Pacific/Auckland', -38.1092, 176.3172),
    ('TRG', 'Tauranga', 'New Zealand', 'Tauranga Airport', 'Pacific/Auckland', -37.6719, 176.1961),
    ('HLZ', 'Hamilton', 'New Zealand', 'Hamilton Airport', 'Pacific/Auckland', -37.8667, 175.3320),
    ('PMR', 'Palmerston North', 'New Zealand', 'Palmerston North Airport', 'Pacific/Auckland', -40.3206, 175.6169),
    ('IVC', 'Invercargill', 'New Zealand', 'Invercargill Airport', 'Pacific/Auckland', -46.4124, 168.3130),
    ('NPL', 'New Plymouth', 'New Zealand', 'New Plymouth Airport', 'Pacific/Auckland', -39.0086, 174.1792),
    ('GIS', 'Gisborne', 'New Zealand', 'Gisborne Airport', 'Pacific/Auckland', -38.6633, 177.9783),
    ('KKE', 'Kerikeri', 'New Zealand', 'Bay of Islands Airport', 'Pacific/Auckland', -35.2628, 173.9119),
    ('WRE', 'Whangārei', 'New Zealand', 'Whangārei Airport', 'Pacific/Auckland', -35.7683, 174.3650),
    ('BHE', 'Blenheim', 'New Zealand', 'Marlborough Airport', 'Pacific/Auckland', -41.5183, 173.8703),
    -- Australia
    ('BNE', 'Brisbane', 'Australia', 'Brisbane Airport', 'Australia/Brisbane', -27.3842, 153.1175),
    ('PER', 'Perth', 'Australia', 'Perth Airport', 'Australia/Perth', -31.9403, 115.9669),
    ('ADL', 'Adelaide', 'Australia', 'Adelaide Airport', 'Australia/Adelaide', -34.9450, 138.5306),
    ('CBR', 'Canberra', 'Australia', 'Canberra Airport', 'Australia/Sydney', -35.3069, 149.1950),
    ('OOL', 'Gold Coast', 'Australia', 'Gold Coast Airport', 'Australia/Brisbane', -28.1644, 153.5047),
    ('CNS', 'Cairns', 'Australia', 'Cairns Airport', 'Australia/Brisbane', -16.8858, 145.7552),
    ('HBA', 'Hobart', 'Australia', 'Hobart Airport', 'Australia/Hobart', -42.8361, 147.5103),
    ('DRW', 'Darwin', 'Australia', 'Darwin International', 'Australia/Darwin', -12.4147, 130.8767),
    ('TSV', 'Townsville', 'Australia', 'Townsville Airport', 'Australia/Brisbane', -19.2525, 146.7656),
    ('LST', 'Launceston', 'Australia', 'Launceston Airport', 'Australia/Hobart', -41.5453, 147.2142),
    -- Pacific
    ('NAN', 'Nadi', 'Fiji', 'Nadi International', 'Pacific/Fiji', -17.7554, 177.4434),
    ('RAR', 'Rarotonga', 'Cook Islands', 'Rarotonga International', 'Pacific/Rarotonga', -21.2027, -159.8059),
    ('APW', 'Apia', 'Samoa', 'Faleolo International', 'Pacific/Apia', -13.8300, -172.0083),
    ('TBU', 'Nuku''alofa', 'Tonga', 'Fua''amotu International', 'Pacific/Tongatapu', -21.2412, -175.1496),
    ('NOU', 'Nouméa', 'New Caledonia', 'La Tontouta International', 'Pacific/Noumea', -22.0146, 166.2129),
    ('PPT', 'Papeete', 'French Polynesia', 'Faa''a International', 'Pacific/Tahiti', -17.5537, -149.6070),
    ('HNL', 'Honolulu', 'United States', 'Daniel K. Inouye International', 'Pacific/Honolulu', 21.3187, -157.9225),
    -- Asia & Türkiye
    ('SIN', 'Singapore', 'Singapore', 'Changi', 'Asia/Singapore', 1.3644, 103.9915),
    ('HKG', 'Hong Kong', 'Hong Kong', 'Hong Kong International', 'Asia/Hong_Kong', 22.3080, 113.9185),
    ('BKK', 'Bangkok', 'Thailand', 'Suvarnabhumi', 'Asia/Bangkok', 13.6900, 100.7501),
    ('KUL', 'Kuala Lumpur', 'Malaysia', 'Kuala Lumpur International', 'Asia/Kuala_Lumpur', 2.7456, 101.7099),
    ('DEL', 'Delhi', 'India', 'Indira Gandhi International', 'Asia/Kolkata', 28.5562, 77.1000),
    ('BOM', 'Mumbai', 'India', 'Chhatrapati Shivaji Maharaj International', 'Asia/Kolkata', 19.0896, 72.8656),
    ('NRT', 'Tokyo', 'Japan', 'Narita', 'Asia/Tokyo', 35.7720, 140.3929),
    ('ICN', 'Seoul', 'South Korea', 'Incheon International', 'Asia/Seoul', 37.4602, 126.4407),
    ('PEK', 'Beijing', 'China', 'Beijing Capital', 'Asia/Shanghai', 40.0799, 116.6031),
    ('PVG', 'Shanghai', 'China', 'Pudong', 'Asia/Shanghai', 31.1443, 121.8083),
    ('TPE', 'Taipei', 'Taiwan', 'Taoyuan International', 'Asia/Taipei', 25.0797, 121.2342),
    ('IST', 'Istanbul', 'Türkiye', 'Istanbul Airport', 'Europe/Istanbul', 41.2753, 28.7519),
    ('AYT', 'Antalya', 'Türkiye', 'Antalya Airport', 'Europe/Istanbul', 36.8987, 30.8005),
    -- Europe
    ('FRA', 'Frankfurt', 'Germany', 'Frankfurt Airport', 'Europe/Berlin', 50.0379, 8.5622),
    ('MUC', 'Munich', 'Germany', 'Munich Airport', 'Europe/Berlin', 48.3537, 11.7750),
    ('AMS', 'Amsterdam', 'Netherlands', 'Schiphol', 'Europe/Amsterdam', 52.3105, 4.7683),
    ('MAD', 'Madrid', 'Spain', 'Adolfo Suárez Madrid–Barajas', 'Europe/Madrid', 40.4983, -3.5676),
    ('BCN', 'Barcelona', 'Spain', 'Josep Tarradellas Barcelona–El Prat', 'Europe/Madrid', 41.2974, 2.0833),
    ('FCO', 'Rome', 'Italy', 'Fiumicino', 'Europe/Rome', 41.8003, 12.2389),
    ('ZRH', 'Zurich', 'Switzerland', 'Zurich Airport', 'Europe/Zurich', 47.4582, 8.5555),
    ('VIE', 'Vienna', 'Austria', 'Vienna International', 'Europe/Vienna', 48.1103, 16.5697),
    ('ATH', 'Athens', 'Greece', 'Athens International', 'Europe/Athens', 37.9364, 23.9445),
    -- North America
    ('ORD', 'Chicago', 'United States', 'O''Hare International', 'America/Chicago', 41.9742, -87.9073),
    ('DFW', 'Dallas', 'United States', 'Dallas Fort Worth International', 'America/Chicago', 32.8998, -97.0403),
    ('IAH', 'Houston', 'United States', 'George Bush Intercontinental', 'America/Chicago', 29.9902, -95.3368),
    ('YVR', 'Vancouver', 'Canada', 'Vancouver International', 'America/Vancouver', 49.1967, -123.1815),
    ('YYZ', 'Toronto', 'Canada', 'Toronto Pearson', 'America/Toronto', 43.6777, -79.6248)
on conflict (code) do nothing;

-- ---------------------------------------------------------------------------------------------
-- Airlines (tier drives fares and perks; fleet = short / medium / long-haul aircraft)
-- ---------------------------------------------------------------------------------------------
insert into public.airlines (code, name, brand_color, tier, rating, fleet) values
    ('NZ', 'Air New Zealand', '#000000', 'premium', 4.6, '{A320neo,A321neo,787-9}'),
    ('QF', 'Qantas', '#E40000', 'premium', 4.4, '{737-800,A330-300,787-9}'),
    ('VA', 'Virgin Australia', '#E1001A', 'full', 4.2, '{737-800,737 MAX 8,737 MAX 8}'),
    ('JQ', 'Jetstar', '#FF5115', 'low', 3.8, '{A320neo,A321LR,787-8}'),
    ('FJ', 'Fiji Airways', '#0E2A47', 'full', 4.3, '{737 MAX 8,A330-200,A350-900}'),
    ('EK', 'Emirates', '#D71921', 'premium', 4.6, '{777-300ER,777-300ER,A380}'),
    ('QR', 'Qatar Airways', '#5C0632', 'premium', 4.7, '{A320,787-9,A350-1000}'),
    ('EY', 'Etihad Airways', '#BD8B13', 'premium', 4.5, '{A320,787-9,787-10}'),
    ('SV', 'Saudia', '#006C35', 'full', 4.1, '{A320neo,A330-300,787-9}'),
    ('XY', 'flynas', '#009AA6', 'low', 3.8, '{A320neo,A320neo,A330neo}'),
    ('MS', 'EgyptAir', '#002F6C', 'full', 3.8, '{A220-300,737-800,787-9}'),
    ('RJ', 'Royal Jordanian', '#2B2B2B', 'full', 4.0, '{A320,A320,787-8}'),
    ('ME', 'Middle East Airlines', '#00843D', 'full', 4.0, '{A320neo,A321neo,A330-200}'),
    ('KU', 'Kuwait Airways', '#0F4C81', 'full', 3.9, '{A320neo,A330-800,777-300ER}'),
    ('GF', 'Gulf Air', '#A6823C', 'full', 4.1, '{A320neo,A321neo,787-9}'),
    ('WY', 'Oman Air', '#00737A', 'full', 4.2, '{737 MAX 8,737 MAX 8,787-9}'),
    ('FZ', 'flydubai', '#F26E22', 'low', 4.0, '{737 MAX 8,737 MAX 8,737 MAX 8}'),
    ('G9', 'Air Arabia', '#E2231A', 'low', 3.9, '{A320,A321neo,A321neo}'),
    ('AT', 'Royal Air Maroc', '#C8102E', 'full', 3.8, '{737-800,737 MAX 8,787-9}'),
    ('TU', 'Tunisair', '#E30613', 'full', 3.6, '{A320,A320neo,A330-200}'),
    ('AH', 'Air Algérie', '#D2232A', 'full', 3.5, '{737-800,737-800,A330-200}'),
    ('IA', 'Iraqi Airways', '#006A4E', 'full', 3.4, '{737-800,737 MAX 8,787-8}'),
    ('SU', 'Aeroflot', '#003D7C', 'full', 4.1, '{A320,A321,777-300ER}'),
    ('S7', 'S7 Airlines', '#8CBF3F', 'full', 4.0, '{A320neo,737-800,A321neo}'),
    ('DP', 'Pobeda', '#0072CE', 'low', 3.7, '{737-800,737-800,737-800}'),
    ('B2', 'Belavia', '#1B4BA0', 'full', 3.9, '{E195-E2,737 MAX 8,737-800}'),
    ('TK', 'Turkish Airlines', '#C70A0C', 'premium', 4.3, '{A321neo,737 MAX 9,A350-900}'),
    ('SQ', 'Singapore Airlines', '#1A2B5F', 'premium', 4.7, '{737 MAX 8,A350-900,A350-900ULR}'),
    ('CX', 'Cathay Pacific', '#006564', 'premium', 4.5, '{A321neo,A330-300,A350-1000}'),
    ('LH', 'Lufthansa', '#05164D', 'full', 4.2, '{A320neo,A321neo,747-8}'),
    ('KL', 'KLM', '#00A1DE', 'full', 4.2, '{737-800,737-900,787-10}'),
    ('TG', 'Thai Airways', '#4B286D', 'full', 4.2, '{A320,A350-900,A350-900}'),
    ('MH', 'Malaysia Airlines', '#00205B', 'full', 4.1, '{737-800,A330-300,A350-900}'),
    ('NH', 'ANA', '#13448F', 'premium', 4.5, '{787-8,787-9,777-300ER}'),
    ('JL', 'Japan Airlines', '#C8102E', 'premium', 4.6, '{737-800,787-9,A350-1000}'),
    ('KE', 'Korean Air', '#1B75BB', 'premium', 4.4, '{737-900,A330-300,777-300ER}'),
    ('AI', 'Air India', '#DA251C', 'full', 3.8, '{A320neo,A321neo,777-300ER}'),
    ('CA', 'Air China', '#E30613', 'full', 3.9, '{A321,A330-300,777-300ER}'),
    ('UA', 'United Airlines', '#005DAA', 'full', 4.0, '{737 MAX 9,757-200,787-9}'),
    ('AA', 'American Airlines', '#0078D2', 'full', 4.0, '{A321,737-800,787-9}'),
    ('DL', 'Delta', '#003366', 'full', 4.2, '{A321neo,A321neo,A350-900}'),
    ('B6', 'JetBlue', '#003876', 'full', 4.3, '{A220-300,A321neo,A321LR}'),
    ('AS', 'Alaska Airlines', '#01426A', 'full', 4.3, '{737-900,737 MAX 9,737 MAX 9}'),
    ('BA', 'British Airways', '#075AAA', 'full', 4.1, '{A320neo,A321neo,777-300ER}'),
    ('AF', 'Air France', '#002157', 'full', 4.1, '{A220-300,A320,777-300ER}'),
    ('TP', 'TAP Air Portugal', '#00A651', 'full', 4.0, '{A320neo,A321LR,A330neo}'),
    ('FI', 'Icelandair', '#003A7D', 'full', 4.1, '{737 MAX 8,737 MAX 9,757-200}'),
    ('AM', 'Aeromexico', '#0B2343', 'full', 4.0, '{737 MAX 8,737 MAX 9,787-9}')
on conflict (code) do update set
    name = excluded.name, brand_color = excluded.brand_color, tier = excluded.tier,
    rating = excluded.rating, fleet = excluded.fleet;

-- Square logos from Kiwi.com's public airline-logo CDN, by IATA code.
update public.airlines set logo_url = 'https://images.kiwi.com/airlines/128x128/' || code || '.png';

-- ---------------------------------------------------------------------------------------------
-- Routes: "<airline> <hub>: <DEST><daily> | <DEST><n>w". Daily frequency is capped at 2 to keep a
-- full year of departures within the free tier. The reverse direction is added automatically.
-- ---------------------------------------------------------------------------------------------
with lines as (
    select trim(line) as line from regexp_split_to_table($routes$
NZ AKL: WLG6 CHC6 ZQN3 DUD3 NSN4 NPE4 ROT3 TRG4 PMR3 IVC2 NPL3 GIS2 KKE2 WRE3 SYD4 MEL3 BNE3 PER3w ADL4w OOL1 CNS3w NAN2 RAR1 APW1 TBU1 NOU5w PPT3w HNL1 LAX1 IAH1 ORD4w YVR3w SIN1 NRT1 TPE4w PVG5w
NZ WLG: CHC5 ZQN2 DUD2 NSN4 NPE2 ROT1 TRG2 HLZ2 IVC1 BHE2 SYD2 MEL1 BNE1 NAN3w
NZ CHC: ZQN2 DUD2 NSN2 ROT1 HLZ1 PMR1 SYD2 MEL1 BNE1
NZ ZQN: SYD1 MEL1 BNE5w
QF SYD: MEL12 BNE8 PER4 ADL5 CBR6 OOL4 CNS3 HBA3 DRW2 TSV1 LAX2 DFW1 SFO1 HNL1 SIN2 HND1 HKG1 NAN1 WLG1 CHC1 ZQN1 NOU2w
QF MEL: BNE6 PER4 ADL5 HBA4 CBR4 OOL3 CNS2 DRW1 LST3 LAX1 SIN1 AKL1 DFW3w
QF BNE: PER2 ADL2 CNS4 TSV3 DRW2 CBR3 LAX1 AKL1 NOU2w
QF PER: ADL2 DRW1 LHR1 SIN1
VA SYD: MEL10 BNE7 PER2 ADL3 OOL3 CBR3 CNS1 NAN1 ZQN4w
VA MEL: BNE5 PER2 ADL3 OOL2 HBA3 CBR2
VA BNE: CNS2 PER1 AKL1 NAN1 APW2w
JQ SYD: MEL8 OOL4 BNE3 CNS2 AKL2 HNL4w
JQ MEL: OOL4 PER2 HBA3 ADL2 BNE3 AKL2 CHC1
JQ AKL: WLG4 CHC4 ZQN2 OOL1 RAR3w
JQ WLG: CHC2
FJ NAN: AKL2 SYD2 MEL1 BNE1 CHC4w WLG3w LAX1 SFO3w HNL3w APW2w TBU3w SIN3w
EK DXB: LHR6 JFK3 LAX1 SFO1 ORD1 IAH1 YYZ1 SYD3 MEL2 BNE1 PER1 AKL1 SIN4 BKK4 HKG2 NRT1 CDG3 FRA3 MUC2 AMS2 MAD2 BCN2 FCO2 ZRH2 VIE1 ATH1 IST3 DME3 CAI3 AMM2 BEY2 RUH3 JED3 MED1 DMM2 KWI4 BAH3 MCT3 DOH3 CMN1 TUN1 ALG1 BGW2 DEL4 BOM4 KUL2 ICN1 PEK1
QR DOH: LHR6 JFK2 LAX1 ORD1 CDG2 FRA2 MUC1 AMS2 MAD2 BCN1 FCO2 ZRH1 ATH2 IST3 DME1 DXB5 RUH4 JED3 MED1 DMM3 KWI4 BAH4 MCT3 AMM2 BEY2 CAI3 CMN1 TUN1 ALG1 BGW2 EBL1 SYD2 MEL1 PER1 BNE1 AKL1 SIN3 BKK3 HKG2 NRT1 DEL4 BOM3 KUL2
EY AUH: LHR4 JFK1 CDG1 FRA1 MUC1 AMS1 FCO1 MAD1 IST1 DME1 SYD1 MEL1 SIN1 BKK1 DEL3 BOM2 RUH2 JED2 DMM1 KWI1 BAH2 MCT2 AMM2 BEY2 CAI2 CMN1 KUL1
SV RUH: JED10 DMM6 MED3 DXB4 CAI4 AMM2 BEY1 KWI2 BAH3 DOH3 MCT1 IST2 LHR2 CDG1 FRA1 DEL1 JFK1
SV JED: DMM3 MED2 DXB3 CAI5 AMM2 DOH2 IST2 LHR1 CMN1 TUN1 ALG1 KUL1
XY RUH: JED8 DMM4 MED2 DXB2 CAI2 KWI1 IST1
XY JED: DMM2 CAI2
MS CAI: JED4 RUH3 DMM2 MED2 DXB3 AUH2 DOH2 KWI3 BAH1 MCT1 AMM3 BEY2 TUN1 ALG1 CMN1 IST2 LHR2 CDG2 FRA2 FCO1 MAD1 ATH2 JFK1 SVO1 HRG4 SSH3 LXR2
RJ AMM: DXB3 DOH2 RUH2 JED2 KWI2 BAH1 BEY2 CAI3 IST2 LHR2 CDG1 FRA1 JFK1 ATH1 BGW2 EBL1 DMM1
ME BEY: DXB3 DOH2 RUH2 JED2 KWI2 CAI2 AMM2 IST2 CDG3 LHR2 FRA1 ATH1 FCO1 BGW1 EBL1
KU KWI: DXB4 DOH3 BAH2 RUH2 JED2 CAI3 BEY2 AMM1 IST2 LHR1 CDG1 FRA1 JFK3w BOM1 DEL1
GF BAH: DXB4 DOH3 RUH3 JED2 KWI2 MCT1 CAI2 AMM1 BEY1 LHR2 CDG1 IST1 BOM1 DEL1
WY MCT: DXB4 DOH3 BAH1 KWI1 RUH1 JED1 CAI1 LHR1 IST1 BOM2 DEL1 BKK1 KUL1
FZ DXB: MSQ1 VKO2 KWI3 BAH2 DOH3 MCT3 AMM2 BEY2 CAI2 RUH3 JED2 MED1 DMM2 BGW2 EBL1 IST2 BOM1
G9 SHJ: CAI3 AMM2 BEY1 RUH2 JED2 DMM1 KWI2 BAH2 MCT2 DOH2 IST1 DME1 BOM2 DEL2
AT CMN: RAK4 CDG3 MAD2 LHR1 JFK1 IST1 CAI1 JED1 DXB1 TUN1 ALG1 FRA1 AMS1 BCN1 FCO1
TU TUN: CDG3 FCO1 FRA1 IST1 CAI1 JED1 ALG1 CMN1
AH ALG: CDG3 FCO1 IST1 CAI1 JED1 TUN1 DXB1 FRA1 BCN1
IA BGW: DXB2 AMM2 BEY1 IST2 CAI1 EBL2
SU SVO: LED10 AER6 KZN4 SVX3 OVB3 MSQ4 DXB4 AUH1 DOH1 IST3 AYT2 CAI1 HRG2 SSH2 BKK1 PEK1 DEL1
S7 DME: LED5 AER4 OVB4 KZN2 MSQ2 DXB1 IST1 AYT1
DP VKO: LED6 AER4 KZN2 MSQ1 IST1 DXB1 AYT1
B2 MSQ: SVO4 LED2 AER1 KZN1 IST2 DXB1 AYT1 HRG4w SSH3w
TK IST: LHR6 CDG4 FRA4 AMS3 MAD2 BCN2 FCO3 MUC3 ZRH2 VIE2 ATH3 JFK3 LAX1 ORD1 SFO1 IAH1 YYZ1 DXB4 DOH3 AUH2 RUH3 JED3 MED1 DMM1 KWI2 BAH1 MCT1 AMM2 BEY2 CAI3 CMN1 TUN1 ALG1 BGW2 EBL1 SVO3 VKO1 MSQ2 LED2 AER1 KZN1 SIN1 BKK1 HKG1 NRT1 ICN1 PEK1 DEL1 BOM1 AYT8 MEL1
SQ SIN: SYD4 MEL3 BNE2 PER3 ADL1 CBR1 AKL2 CHC1 LHR3 FRA1 CDG1 JFK1 LAX1 SFO2 HND2 NRT1 HKG3 BKK4 KUL4 DEL2 BOM2 DXB1 IST1 ICN2
CX HKG: SYD3 MEL2 BNE1 PER1 AKL1 LHR3 JFK1 LAX1 SFO1 YVR1 SIN3 BKK3 HND2 NRT1 ICN2 DEL1 DXB1 TPE4
LH FRA: LHR4 CDG3 MAD2 BCN2 FCO3 ATH2 IST2 DXB1 CAI1 AMM1 BEY1 JFK2 LAX1 ORD1 SFO1 SIN1 BKK1 HND1 DEL1 BOM1 MUC8 ZRH3 VIE3 AMS3
BA LHR: CDG5 FRA3 AMS4 MAD3 BCN3 FCO2 MUC2 ZRH2 VIE2 ATH2 IST2 LAX1 SFO1 ORD1 YYZ1 YVR1 DXB2 DOH2 RUH1 JED1 AMM1 CAI1 BAH1 KWI1 MCT1 SIN1 HKG1 HND1 DEL2 BOM1 AYT1 CMN1
AF CDG: LHR4 MAD2 BCN2 FCO2 AMS3 ATH1 IST1 LAX1 SFO1 ORD1 YYZ1 DXB1 RUH1 BEY2 CAI1 AMM1 CMN2 TUN2 ALG2 RAK1 HND1 SIN1 BKK1 DEL1
KL AMS: LHR4 JFK2 LAX1 DXB1 SIN1 CAI1 IST1 BCN2 MAD2 FCO2
UA SFO: SYD1 MEL1 AKL1 LHR1 HND1 SIN1 JFK6 ORD5 LAX8 SEA4
UA IAH: SYD1 LHR1
AA DFW: SYD1 BNE4w LHR2 LAX6 JFK5 ORD6
AA LAX: AKL1 ORD4
DL LAX: SYD1 AKL1 LHR1 SEA4
B6 JFK: SFO3 ORD2
AS SEA: SFO5 LAX6 ORD2 JFK2
TG BKK: SYD2 MEL1 PER1 LHR1 FRA1 DXB1 HKG2 SIN3 NRT2 DEL2
MH KUL: SYD2 MEL1 PER2 AKL4w LHR1 DXB1 SIN4 BKK3 DEL1
NH HND: SYD1 PER4w LHR1 JFK1 LAX1 SFO1 SIN1 BKK1
JL HND: SYD1 LHR1 LAX1 SFO1 SIN1 BKK1 MEL1
KE ICN: SYD1 AKL3w LAX1 JFK1 SFO1 LHR1 SIN1 BKK1 HKG2 NRT3
AI DEL: LHR2 JFK1 SYD1 MEL1 DXB2 SIN1 BOM8 SFO1
CA PEK: LHR1 FRA1 SYD1 MEL1 AKL3w SVO1 DXB1 SIN1 BKK1 PVG8
$routes$, E'\n') as line
),
heads as (
    select split_part(line, ' ', 1) as airline, substr(split_part(line, ':', 1), 4, 3) as hub, trim(split_part(line, ':', 2)) as dests
    from lines where line ~ '^[A-Z0-9]{2} [A-Z]{3}:'
),
tokens as (
    select airline, hub, regexp_match(tok, '^([A-Z]{3})(\d+)(w?)$') as m
    from heads, regexp_split_to_table(dests, '\s+') as tok
),
spec as (
    select airline, hub as origin, m[1] as destination,
        case when m[3] = 'w' then 1 else least(m[2]::int, 2) end as daily,
        case when m[3] = 'w' then least(m[2]::int, 7) else 7 end as days
    from tokens where m is not null
)
insert into public.routes (airline_code, origin, destination, daily, days_per_week)
select airline, origin, destination, daily, days from spec
union all
select airline, destination, origin, daily, days from spec
on conflict (airline_code, origin, destination) do update
    set daily = greatest(public.routes.daily, excluded.daily),
        days_per_week = greatest(public.routes.days_per_week, excluded.days_per_week);

-- ---------------------------------------------------------------------------------------------
-- Flights derived from routes. Where flights were seeded by hand (the board's), only the missing
-- frequencies are added, so JFK–LAX keeps exactly the board's five departures.
-- ---------------------------------------------------------------------------------------------
with r as (
    select rt.*, a.tier, a.fleet, a.rating,
        (select count(*) from public.flights f
         where f.airline_code = rt.airline_code and f.origin = rt.origin and f.destination = rt.destination) as seeded,
        2 * 6371 * asin(sqrt(
            power(sin(radians(d.lat - o.lat) / 2), 2) +
            cos(radians(o.lat)) * cos(radians(d.lat)) * power(sin(radians(d.lon - o.lon) / 2), 2)
        )) as km,
        mod((d.lon - o.lon + 540)::numeric, 360) - 180 as east
    from public.routes rt
    join public.airports o on o.code = rt.origin
    join public.airports d on d.code = rt.destination
    join public.airlines a on a.code = rt.airline_code
),
slots as (
    select r.*, s as slot, abs(hashtext(r.airline_code || r.origin || r.destination || s)::bigint) as h
    from r cross join generate_series(1, r.daily - r.seeded) as s
),
calc as (
    select *,
        -- Block time: cruise speed by stage length, jet-stream ±4% on long east/west sectors, +30 min taxi.
        (round(((km / case when km < 1000 then 560 when km < 3000 then 720 else 830 end) * 60
            * case when km > 3000 then (case when east > 0 then 0.96 else 1.04 end) else 1 end + 30) / 5) * 5)::int as minutes,
        -- Departure: long-haul singles use typical banks; other singles anywhere 06:00–22:00; pairs split the day.
        case when daily = 1 and km > 6000
            then (array[60, 120, 480, 540, 600, 780, 900, 1080, 1140, 1200, 1260, 1320, 1380])[1 + (h % 13)::int] + ((h / 13) % 6)::int * 5
            when daily = 1 then 360 + (h % 193)::int * 5
            else 360 + (slot - 1) * (960 / daily) + (h % 30)::int * 5
        end as dep_min
    from slots
)
insert into public.flights
    (airline_code, flight_number, origin, destination, departure_time, duration_minutes, stops,
     aircraft, base_fare_usd, rating, perks, default_gate, days_of_week)
select
    airline_code,
    airline_code || ' ' || (700 + row_number() over (partition by airline_code order by origin, destination, slot)),
    origin, destination,
    make_time(((dep_min / 60) % 24)::int, (dep_min % 60)::int, 0),
    minutes,
    0,
    case
        when airline_code = 'NZ' and km < 700
            and (origin in ('NSN','NPE','ROT','TRG','HLZ','PMR','IVC','NPL','GIS','KKE','WRE','BHE')
              or destination in ('NSN','NPE','ROT','TRG','HLZ','PMR','IVC','NPL','GIS','KKE','WRE','BHE'))
            then 'ATR 72-600'
        when km < 1500 then fleet[1]
        when km < 5000 then fleet[2]
        else fleet[3]
    end,
    round((case when km < 1500 then 45 + km * 0.11 else 140 + km * 0.062 end)
        * case tier when 'premium' then 1.12 when 'low' then 0.72 else 1 end
        * (0.9 + (h % 21) / 100.0)),
    least(4.9, rating + ((h % 3) - 1) * 0.1),
    case
        when tier = 'low' then (case when km < 3000 then '{Personal item,Buy on board}' else '{Personal item,Meals for purchase}' end)::text[]
        when km < 1500 then (case when airline_code = 'NZ' then '{Carry-on,Free Wi-Fi,Snack}' else '{Carry-on,Snack}' end)::text[]
        when km < 5000 then '{Carry-on,Meal,Wi-Fi}'::text[]
        when airline_code = 'NZ' then '{Carry-on,Meal,Wi-Fi,Skycouch option}'::text[]
        when tier = 'premium' then '{Carry-on,Meal,Wi-Fi,Lie-flat option}'::text[]
        else '{Carry-on,Meal,Wi-Fi}'::text[]
    end,
    chr(65 + (h % 5)::int) || (1 + (h / 5) % 40)::text,
    case days_per_week
        when 7 then '{1,2,3,4,5,6,7}'
        when 6 then '{1,2,3,4,5,7}'
        when 5 then '{1,2,4,5,7}'
        when 4 then '{1,3,5,7}'
        when 3 then (case when h % 2 = 0 then '{1,3,5}' else '{2,4,6}' end)
        when 2 then '{3,7}'
        else '{6}'
    end::smallint[]
from calc;

-- ---------------------------------------------------------------------------------------------
-- One-stop itineraries through hubs, for city pairs with no nonstop (e.g. Auckland–London).
-- "<airline> <origin> <hub> <destination>", both directions.
-- ---------------------------------------------------------------------------------------------
with conn as (
    select split_part(c, ' ', 1) as airline, split_part(c, ' ', 2) as a, split_part(c, ' ', 3) as hub, split_part(c, ' ', 4) as b
    from regexp_split_to_table($c$EK AKL DXB LHR,EK AKL DXB CDG,EK SYD DXB CDG,EK SYD DXB DME,EK MEL DXB IST,EK AKL DXB CAI,EK SYD DXB AMM,EK SYD DXB BEY,EK MEL DXB BEY,EK AKL DXB IST,EK SYD DXB MSQ,EK AKL DXB MSQ,QR AKL DOH LHR,QR SYD DOH CDG,QR MEL DOH FRA,QR AKL DOH CAI,QR SYD DOH BEY,QR PER DOH AMM,QR AKL DOH DME,QR AKL DOH MSQ,SQ AKL SIN LHR,SQ CHC SIN LHR,SQ SYD SIN FRA,SQ MEL SIN CDG,SQ AKL SIN BKK,QF SYD SIN LHR,QF MEL PER LHR,QF SYD AKL JFK,NZ CHC AKL LAX,NZ WLG AKL LAX,NZ ZQN AKL LAX,NZ CHC AKL NRT,NZ WLG AKL JFK,NZ CHC AKL JFK,TK MSQ IST LHR,TK SVO IST CDG,TK MSQ IST JFK,TK SVO IST JFK,TK MSQ IST CAI,TK SVO IST MAD,FZ MSQ DXB CAI,FZ MSQ DXB AMM,FZ VKO DXB BEY,FZ MSQ DXB RUH,SU MSQ SVO OVB,SU MSQ SVO SVX,SU MSQ SVO BKK,SU LED SVO DXB,UA AKL SFO JFK,UA SYD SFO JFK,UA MEL SFO ORD,DL AKL LAX JFK,DL SYD LAX JFK,FJ AKL NAN LAX,FJ SYD NAN SFO,CX AKL HKG LHR$c$, ',') as c
),
both_ways as (
    select airline, a as origin, hub, b as destination from conn
    union all
    select airline, b, hub, a from conn
),
legs as (
    select bw.*, al.tier, al.rating, al.fleet,
        2 * 6371 * asin(sqrt(power(sin(radians(h.lat - o.lat) / 2), 2) + cos(radians(o.lat)) * cos(radians(h.lat)) * power(sin(radians(h.lon - o.lon) / 2), 2))) as km1,
        2 * 6371 * asin(sqrt(power(sin(radians(d.lat - h.lat) / 2), 2) + cos(radians(h.lat)) * cos(radians(d.lat)) * power(sin(radians(d.lon - h.lon) / 2), 2))) as km2,
        abs(hashtext(bw.airline || bw.origin || bw.hub || bw.destination)::bigint) as hh
    from both_ways bw
    join public.airports o on o.code = bw.origin
    join public.airports h on h.code = bw.hub
    join public.airports d on d.code = bw.destination
    join public.airlines al on al.code = bw.airline
    where not exists (
        select 1 from public.flights f
        where f.airline_code = bw.airline and f.origin = bw.origin and f.destination = bw.destination
    )
)
insert into public.flights
    (airline_code, flight_number, origin, destination, departure_time, duration_minutes, stops, stop_airport,
     aircraft, base_fare_usd, rating, perks, default_gate, days_of_week)
select
    airline,
    airline || ' ' || (5000 + row_number() over (partition by airline order by origin, destination)),
    origin, destination,
    make_time((array[0, 1, 2, 8, 9, 10, 13, 15, 18, 20, 21, 22, 23])[1 + (hh % 13)::int], ((hh / 13) % 12)::int * 5, 0),
    (round(((km1 + km2) / 830 * 60 + 60 + 95 + (hh % 90)) / 5) * 5)::int,
    1,
    hub,
    fleet[3],
    round((280 + (km1 + km2) * 0.058) * case tier when 'premium' then 1.1 when 'low' then 0.75 else 1 end * (0.9 + (hh % 21) / 100.0)),
    least(4.9, rating),
    case when tier = 'premium' then '{Carry-on,Meal,Wi-Fi,Lie-flat option}' else '{Carry-on,Meal,Wi-Fi}' end::text[],
    chr(65 + (hh % 5)::int) || (1 + (hh / 5) % 40)::text,
    '{1,2,3,4,5,6,7}'
from legs;

-- Seasonal fares: weekends, Christmas–New Year, northern summer and Easter cost more.
create or replace function public.seasonal_factor(d date)
returns numeric
language sql
immutable
set search_path = ''
as $$
    select round((0.92
        + (abs(hashtext(d::text)::bigint) % 22) / 100.0
        + case when extract(isodow from d) in (5, 7) then 0.06 else 0 end
        + case
            when (extract(month from d) = 12 and extract(day from d) >= 18)
              or (extract(month from d) = 1 and extract(day from d) <= 8) then 0.28
            when extract(month from d) in (7, 8) then 0.12
            when extract(month from d) = 4 and extract(day from d) <= 20 then 0.08
            else 0
          end)::numeric, 2);
$$;
