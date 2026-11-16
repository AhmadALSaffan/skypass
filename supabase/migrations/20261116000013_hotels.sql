-- Hotels: five stays in every city Skypass flies to, room reservations, and the function that
-- prices and books them on the server.

-- ---------------------------------------------------------------------------------------------
-- Catalogue
-- ---------------------------------------------------------------------------------------------
-- The board's four Auckland stays stay as seeded. Every other city gets five, generated from the
-- city's code so the names, prices and photos are the same on every run. Photos are Unsplash
-- hotel shots: resort-style for beach and island destinations, city hotels everywhere else.

with pool as (
    select
        array[
            '1445019980597-93fa8acb246c', '1455587734955-081b22074882', '1529290130-4ca3753253ae',
            '1578683010236-d716f9a3f461', '1590490360182-c33d57733427', '1611892440504-42a792e24d32',
            '1618773928121-c32242e63f39', '1631049307264-da0ec9d70304'
        ] as city_photos,
        array[
            '1520250497591-112f2f40a3f4', '1535827841776-24afc1e255ac', '1542314831-068cd1dbfeeb',
            '1549294413-26f195200c16', '1551882547-ff40c63fe5fa', '1561501900-3701fa6a0864',
            '1564501049412-61c2a3083791', '1566073771259-6a8506099945', '1571896349842-33c89424de2d',
            '1582719508461-905c673771fd', '1584132967334-10e028bd69f7'
        ] as resort_photos
),
cities as (
    -- One row per city (some cities have two airports); beach and island cities get resorts.
    select distinct on (a.city)
        a.city,
        a.code,
        exists (
            select 1 from public.destinations d
            where d.airport_code = a.code and d.category in ('Beaches', 'Islands')
        ) as resort
    from public.airports a
    where a.city <> 'Auckland'
    order by a.city, a.code
),
templates (n, name_tpl, area_tpl, dist_lo, dist_hi, stars, base_price, free_cancel, amenities) as (values
    (1, '%s Airport Inn',     '%s Airport',        0.3,  2.0, 3, 125, true,  '{Free Wi-Fi,Shuttle,Breakfast}'::text[]),
    (2, 'The Grand %s',       'City centre',       14.0, 30.0, 5, 340, true,  '{Pool,Spa,Gym}'::text[]),
    (3, '%s Riverside Hotel', 'Waterfront',        12.0, 28.0, 4, 215, false, '{Rooftop bar,Gym,Free Wi-Fi}'::text[]),
    (4, 'Skyline Suites %s',  'Business district', 10.0, 25.0, 4, 185, true,  '{Kitchenette,Free Wi-Fi,Gym}'::text[]),
    (5, 'Atlas House %s',     'Old town',          15.0, 32.0, 5, 265, true,  '{Breakfast,Garden,Free Wi-Fi}'::text[])
),
rows as (
    select c.*, t.*, abs(hashtext(c.code || ':' || t.n)) as h
    from cities c cross join templates t
)
insert into public.hotels (city, name, area, distance_km, stars, rating, review_count, price_usd, free_cancel, amenities, image_url)
select
    r.city,
    format(r.name_tpl, r.city),
    format(r.area_tpl, r.city),
    round((r.dist_lo + (r.h % 1000) / 1000.0 * (r.dist_hi - r.dist_lo))::numeric, 1),
    r.stars,
    least(9.7, round((7.4 + (r.h % 17) / 10.0 + (r.stars - 3) * 0.3)::numeric, 1)),
    180 + (r.h / 7) % 2900,
    round(r.base_price * (0.7 + ((r.h / 11) % 70) / 100.0)),
    r.free_cancel,
    case when r.resort and r.n in (2, 3) then r.amenities || '{Beach access}'::text[] else r.amenities end,
    'https://images.unsplash.com/photo-'
        || case
            when r.resort or r.n = 2 then p.resort_photos[1 + (r.h / 13) % array_length(p.resort_photos, 1)]
            else p.city_photos[1 + (r.h / 13) % array_length(p.city_photos, 1)]
        end
        || '?w=700&h=400&q=75&auto=format&fit=crop'
from rows r cross join pool p
where not exists (select 1 from public.hotels x where x.city = r.city and x.name = format(r.name_tpl, r.city));

-- ---------------------------------------------------------------------------------------------
-- Reservations
-- ---------------------------------------------------------------------------------------------

create table if not exists public.hotel_reservations (
    id               uuid primary key default gen_random_uuid(),
    user_id          uuid not null references auth.users (id) on delete cascade default auth.uid(),
    hotel_id         uuid not null references public.hotels (id),
    room             text not null check (room in ('standard', 'deluxe', 'suite')),
    check_in         date not null,
    check_out        date not null check (check_out > check_in),
    guests           int not null check (guests between 1 and 4),
    total_usd        numeric(10, 2) not null,
    reference        text not null unique,
    idempotency_key  uuid not null,
    status           text not null default 'confirmed' check (status in ('confirmed', 'cancelled')),
    created_at       timestamptz not null default now(),
    unique (user_id, idempotency_key)
);
create index if not exists hotel_reservations_user_idx on public.hotel_reservations (user_id);

alter table public.hotel_reservations enable row level security;
create policy "own hotel reservations" on public.hotel_reservations
    for select to authenticated using ((select auth.uid()) = user_id);

-- Prices the room on the server (nightly rate × room factor × nights) and books it. Calling again
-- with the same idempotency key returns the first reservation instead of booking twice.
create or replace function public.reserve_hotel(
    p_hotel uuid,
    p_room text,
    p_check_in date,
    p_check_out date,
    p_guests int,
    p_idempotency_key uuid
)
returns table (reference text, total_usd numeric, nights int)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user     uuid := auth.uid();
    v_price    numeric;
    v_factor   numeric;
    v_capacity int;
    v_nights   int;
    v_total    numeric;
    v_ref      text;
    v_alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
begin
    if v_user is null then
        raise exception 'not_signed_in' using errcode = '28000';
    end if;

    return query
        select r.reference, r.total_usd, (r.check_out - r.check_in)::int
        from public.hotel_reservations r
        where r.user_id = v_user and r.idempotency_key = p_idempotency_key;
    if found then
        return;
    end if;

    select h.price_usd into v_price from public.hotels h where h.id = p_hotel;
    if v_price is null then
        raise exception 'hotel_not_found' using errcode = 'P0002';
    end if;

    case p_room
        when 'standard' then v_factor := 1.0;  v_capacity := 2;
        when 'deluxe'   then v_factor := 1.35; v_capacity := 3;
        when 'suite'    then v_factor := 1.9;  v_capacity := 4;
        else raise exception 'invalid_room' using errcode = '22023';
    end case;

    if p_guests < 1 or p_guests > v_capacity then
        raise exception 'too_many_guests' using errcode = '22023';
    end if;

    v_nights := p_check_out - p_check_in;
    if p_check_in < current_date or v_nights < 1 or v_nights > 30 then
        raise exception 'invalid_dates' using errcode = '22023';
    end if;

    v_total := round(v_price * v_factor) * v_nights;

    loop
        v_ref := 'SKH' || (
            select string_agg(substr(v_alphabet, 1 + floor(random() * length(v_alphabet))::int, 1), '')
            from generate_series(1, 3)
        );
        exit when not exists (select 1 from public.hotel_reservations x where x.reference = v_ref);
    end loop;

    insert into public.hotel_reservations (user_id, hotel_id, room, check_in, check_out, guests, total_usd, reference, idempotency_key)
    values (v_user, p_hotel, p_room, p_check_in, p_check_out, p_guests, v_total, v_ref, p_idempotency_key);

    return query select v_ref, v_total, v_nights;
end;
$$;

revoke execute on function public.reserve_hotel(uuid, text, date, date, int, uuid) from public, anon;
grant execute on function public.reserve_hotel(uuid, text, date, date, int, uuid) to authenticated;
