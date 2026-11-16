-- Saved payment cards. Only what's printed on the front is kept: brand, last four digits, expiry
-- and the holder's name. Full numbers and security codes never leave the phone.

create table if not exists public.payment_cards (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references auth.users (id) on delete cascade default auth.uid(),
    brand       text not null check (brand in ('visa', 'mastercard', 'amex', 'discover')),
    last4       text not null check (last4 ~ '^[0-9]{4}$'),
    exp_month   int not null check (exp_month between 1 and 12),
    exp_year    int not null check (exp_year between 2000 and 2100),
    holder      text not null check (char_length(trim(holder)) between 2 and 80),
    is_default  boolean not null default false,
    created_at  timestamptz not null default now(),
    unique (user_id, brand, last4, exp_month, exp_year)
);
create index if not exists payment_cards_user_idx on public.payment_cards (user_id);
-- At most one default card per traveller.
create unique index if not exists payment_cards_one_default on public.payment_cards (user_id) where is_default;

alter table public.payment_cards enable row level security;
create policy "own cards" on public.payment_cards for all to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

-- Makes [p_card] the default and clears the old one. Two statements: a non-deferrable unique index
-- is checked row by row, so swapping in one UPDATE could trip it.
create or replace function public.set_default_card(p_card uuid)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
begin
    update public.payment_cards set is_default = false
    where user_id = (select auth.uid()) and is_default and id <> p_card;
    update public.payment_cards set is_default = true
    where user_id = (select auth.uid()) and id = p_card;
end;
$$;
revoke execute on function public.set_default_card(uuid) from public, anon;
grant execute on function public.set_default_card(uuid) to authenticated;

-- Bookings remember which card paid (by brand and last four, so deleting the card keeps history).
alter table public.bookings drop constraint if exists bookings_payment_method_check;
alter table public.bookings add constraint bookings_payment_method_check
    check (payment_method in ('visa', 'mastercard', 'amex', 'discover', 'google_pay'));
alter table public.bookings add column if not exists payment_last4 text;

drop function if exists public.pay_booking(uuid, text);

-- Mock processor. A card payment must name one of the traveller's own, unexpired cards of that brand.
create or replace function public.pay_booking(p_booking_id uuid, p_method text, p_card_id uuid default null)
returns public.bookings
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_booking   public.bookings;
    v_card      public.payment_cards;
    v_pnr       text;
    v_alphabet  constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
begin
    select * into v_booking from public.bookings
    where id = p_booking_id and user_id = auth.uid()
    for update;
    if not found then
        raise exception 'booking_not_found' using errcode = 'P0002';
    end if;
    if v_booking.status = 'confirmed' then
        return v_booking;
    end if;
    if v_booking.status = 'cancelled' then
        raise exception 'booking_cancelled' using errcode = '22023';
    end if;
    if p_method not in ('visa', 'mastercard', 'amex', 'discover', 'google_pay') then
        raise exception 'invalid_payment_method' using errcode = '22023';
    end if;
    if p_card_id is not null then
        select * into v_card from public.payment_cards c
        where c.id = p_card_id and c.user_id = auth.uid() and c.brand = p_method;
        if not found then
            raise exception 'invalid_card' using errcode = '22023';
        end if;
        if make_date(v_card.exp_year, v_card.exp_month, 1) + interval '1 month' <= current_date then
            raise exception 'card_expired' using errcode = '22023';
        end if;
    elsif p_method <> 'google_pay' then
        raise exception 'invalid_card' using errcode = '22023';
    end if;

    loop
        v_pnr := 'SKP' || (
            select string_agg(substr(v_alphabet, 1 + floor(random() * length(v_alphabet))::int, 1), '')
            from generate_series(1, 3)
        );
        exit when not exists (select 1 from public.bookings where pnr = v_pnr);
    end loop;

    update public.bookings
    set status = 'confirmed', pnr = v_pnr, payment_method = p_method, payment_last4 = v_card.last4, paid_at = now()
    where id = p_booking_id
    returning * into v_booking;

    return v_booking;
end;
$$;
revoke execute on function public.pay_booking(uuid, text, uuid) from public, anon;
grant execute on function public.pay_booking(uuid, text, uuid) to authenticated;
