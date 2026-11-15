-- Home: the signed-in user's next confirmed flight (or one that left in the last 3 hours).
-- Security invoker: RLS on bookings already limits rows to the caller.
create or replace function public.next_trip()
returns table (
    booking_id          uuid,
    pnr                 text,
    seat                text,
    cabin               text,
    instance_id         uuid,
    airline_code        text,
    airline_name        text,
    flight_number       text,
    origin              text,
    origin_city         text,
    destination         text,
    destination_city    text,
    departure_local     timestamp,
    duration_minutes    int,
    gate                text,
    boarding_time       time,
    status              text,
    delay_minutes       int
)
language sql
stable
set search_path = ''
as $$
    select
        b.id, b.pnr, b.seat, b.cabin, fi.id,
        a.code, a.name, f.flight_number,
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
