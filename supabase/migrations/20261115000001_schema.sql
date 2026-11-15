-- Skypass schema: catalog (read-only for clients) + per-user data (RLS: owner only).
-- Prices are stored in USD; the app converts for display.

create extension if not exists pgcrypto with schema extensions;

-- ---------------------------------------------------------------------------------------------
-- Catalog
-- ---------------------------------------------------------------------------------------------

create table public.airports (
    code        char(3) primary key,
    city        text not null,
    country     text not null,
    name        text not null,
    tz          text not null,
    lat         double precision,
    lon         double precision
);

create table public.airlines (
    code        text primary key,
    name        text not null,
    brand_color text not null
);

create table public.flights (
    id                    uuid primary key default gen_random_uuid(),
    airline_code          text not null references public.airlines (code),
    flight_number         text not null,
    origin                char(3) not null references public.airports (code),
    destination           char(3) not null references public.airports (code),
    departure_time        time not null,
    duration_minutes      int not null check (duration_minutes > 0),
    stops                 int not null default 0 check (stops between 0 and 2),
    stop_airport          char(3) references public.airports (code),
    aircraft              text not null,
    base_fare_usd         numeric(10, 2) not null check (base_fare_usd > 0),
    rating                numeric(2, 1) not null default 4.3,
    perks                 text[] not null default '{}',
    default_gate          text,
    unique (airline_code, flight_number, origin, destination)
);
create index flights_route_idx on public.flights (origin, destination);

-- One row per flight per day. Gate and status change live (Realtime).
create table public.flight_instances (
    id              uuid primary key default gen_random_uuid(),
    flight_id       uuid not null references public.flights (id) on delete cascade,
    departure_date  date not null,
    gate            text not null,
    boarding_time   time not null,
    status          text not null default 'on_time'
                    check (status in ('on_time', 'delayed', 'boarding', 'departed', 'cancelled')),
    delay_minutes   int not null default 0,
    date_factor     numeric(4, 2) not null default 1,
    unique (flight_id, departure_date)
);
create index flight_instances_date_idx on public.flight_instances (departure_date);

create table public.destinations (
    id              uuid primary key default gen_random_uuid(),
    city            text not null,
    country         text not null,
    airport_code    char(3) not null references public.airports (code),
    category        text not null check (category in ('Beaches', 'Cities', 'Mountains', 'Islands')),
    from_price_usd  numeric(10, 2) not null,
    tone            text not null,
    image_url       text,
    popular_from    char(3) references public.airports (code),
    sort_order      int not null default 0
);

create table public.deals (
    id              uuid primary key default gen_random_uuid(),
    title           text not null,
    airline_code    text not null references public.airlines (code),
    origin          char(3) not null references public.airports (code),
    destination     char(3) not null references public.airports (code),
    nonstop         boolean not null default true,
    from_price_usd  numeric(10, 2) not null,
    tone            text not null,
    image_url       text,
    active          boolean not null default true
);

create table public.hotels (
    id              uuid primary key default gen_random_uuid(),
    city            text not null,
    name            text not null,
    area            text not null,
    distance_km     numeric(5, 1) not null,
    stars           int not null check (stars between 1 and 5),
    rating          numeric(3, 1) not null,
    review_count    int not null,
    price_usd       numeric(10, 2) not null,
    free_cancel     boolean not null default false,
    amenities       text[] not null default '{}',
    image_url       text
);
create index hotels_city_idx on public.hotels (city);

create table public.cars (
    id                  uuid primary key default gen_random_uuid(),
    location_code       char(3) not null references public.airports (code),
    name                text not null,
    type                text not null check (type in ('Compact', 'SUV', 'Electric', 'Premium')),
    seats               int not null,
    bags                int not null,
    transmission        text not null default 'Auto',
    price_per_day_usd   numeric(10, 2) not null,
    supplier            text not null,
    free_cancel         boolean not null default true,
    stage_color         text not null,
    image_path          text not null,
    sort_order          int not null default 0
);

-- ---------------------------------------------------------------------------------------------
-- Per-user data
-- ---------------------------------------------------------------------------------------------

create table public.profiles (
    id              uuid primary key references auth.users (id) on delete cascade,
    full_name       text,
    email           text,
    tier            text not null default 'Blue' check (tier in ('Blue', 'Silver', 'Gold')),
    home_airport    char(3) references public.airports (code) default 'JFK',
    miles           int not null default 0,
    created_at      timestamptz not null default now()
);

create table public.user_settings (
    user_id             uuid primary key references auth.users (id) on delete cascade,
    push_notifications  boolean not null default true,
    gate_alerts         boolean not null default true,
    price_alerts        boolean not null default false,
    currency            text not null default 'USD' check (currency in ('USD', 'EUR', 'GBP', 'NZD')),
    language            text not null default 'en',
    dark_mode           boolean not null default false,
    biometric_lock      boolean not null default true,
    updated_at          timestamptz not null default now()
);

create table public.passengers (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid not null references auth.users (id) on delete cascade default auth.uid(),
    full_name       text not null,
    passenger_type  text not null default 'adult' check (passenger_type in ('adult', 'child', 'infant')),
    passport_last4  text check (passport_last4 ~ '^[0-9]{4}$'),
    is_primary      boolean not null default false,
    created_at      timestamptz not null default now()
);
create index passengers_user_idx on public.passengers (user_id);

create table public.bookings (
    id                  uuid primary key default gen_random_uuid(),
    user_id             uuid not null references auth.users (id) on delete cascade,
    instance_id         uuid not null references public.flight_instances (id),
    pnr                 text unique,
    cabin               text not null check (cabin in ('economy', 'premium', 'business', 'first')),
    passengers          int not null check (passengers between 1 and 9),
    seat                text not null,
    bags                int not null default 0 check (bags between 0 and 3),
    fare_usd            numeric(10, 2) not null,
    taxes_usd           numeric(10, 2) not null,
    seat_fee_usd        numeric(10, 2) not null,
    bags_usd            numeric(10, 2) not null,
    total_usd           numeric(10, 2) not null,
    status              text not null default 'pending' check (status in ('pending', 'confirmed', 'cancelled')),
    payment_method      text check (payment_method in ('visa', 'mastercard', 'google_pay')),
    idempotency_key     uuid not null,
    created_at          timestamptz not null default now(),
    paid_at             timestamptz,
    unique (user_id, idempotency_key)
);
create index bookings_user_idx on public.bookings (user_id);
-- A seat can only be held by one live booking: this is the seat lock.
create unique index bookings_seat_lock on public.bookings (instance_id, seat) where status <> 'cancelled';

create table public.saved_items (
    user_id     uuid not null references auth.users (id) on delete cascade default auth.uid(),
    kind        text not null check (kind in ('flight', 'hotel', 'destination')),
    item_id     uuid not null,
    created_at  timestamptz not null default now(),
    primary key (user_id, kind, item_id)
);

create table public.rental_reservations (
    id                  uuid primary key default gen_random_uuid(),
    user_id             uuid not null references auth.users (id) on delete cascade default auth.uid(),
    car_id              uuid not null references public.cars (id),
    pickup_location     text not null,
    dropoff_location    text not null,
    pickup_at           timestamptz not null,
    dropoff_at          timestamptz not null check (dropoff_at > pickup_at),
    total_usd           numeric(10, 2) not null,
    status              text not null default 'reserved' check (status in ('reserved', 'cancelled')),
    created_at          timestamptz not null default now()
);
create index rental_reservations_user_idx on public.rental_reservations (user_id);

-- ---------------------------------------------------------------------------------------------
-- New users get a profile and default settings.
-- ---------------------------------------------------------------------------------------------

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profiles (id, full_name, email)
    values (
        new.id,
        coalesce(new.raw_user_meta_data ->> 'full_name', new.raw_user_meta_data ->> 'name'),
        new.email
    );
    insert into public.user_settings (user_id) values (new.id);
    return new;
end;
$$;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------------------------
-- Row level security
-- ---------------------------------------------------------------------------------------------

alter table public.airports enable row level security;
alter table public.airlines enable row level security;
alter table public.flights enable row level security;
alter table public.flight_instances enable row level security;
alter table public.destinations enable row level security;
alter table public.deals enable row level security;
alter table public.hotels enable row level security;
alter table public.cars enable row level security;
alter table public.profiles enable row level security;
alter table public.user_settings enable row level security;
alter table public.passengers enable row level security;
alter table public.bookings enable row level security;
alter table public.saved_items enable row level security;
alter table public.rental_reservations enable row level security;

-- Catalog: anyone can read, nobody can write through the API.
create policy "catalog is public" on public.airports for select to anon, authenticated using (true);
create policy "catalog is public" on public.airlines for select to anon, authenticated using (true);
create policy "catalog is public" on public.flights for select to anon, authenticated using (true);
create policy "catalog is public" on public.flight_instances for select to anon, authenticated using (true);
create policy "catalog is public" on public.destinations for select to anon, authenticated using (true);
create policy "catalog is public" on public.deals for select to anon, authenticated using (true);
create policy "catalog is public" on public.hotels for select to anon, authenticated using (true);
create policy "catalog is public" on public.cars for select to anon, authenticated using (true);

-- Profiles and settings: owner reads and updates (rows are created by the trigger).
create policy "own profile" on public.profiles for select to authenticated using ((select auth.uid()) = id);
create policy "own profile update" on public.profiles for update to authenticated
    using ((select auth.uid()) = id) with check ((select auth.uid()) = id);
create policy "own settings" on public.user_settings for select to authenticated using ((select auth.uid()) = user_id);
create policy "own settings update" on public.user_settings for update to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

-- Passengers, saved items, rentals: full CRUD on own rows.
create policy "own passengers" on public.passengers for all to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "own saved items" on public.saved_items for all to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "own rentals" on public.rental_reservations for all to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

-- Bookings: read own. Writes only go through create_booking / pay_booking (security definer),
-- so the server always computes prices and holds seats.
create policy "own bookings" on public.bookings for select to authenticated using ((select auth.uid()) = user_id);

-- Live gate / status changes.
alter publication supabase_realtime add table public.flight_instances;
