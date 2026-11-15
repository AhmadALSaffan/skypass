-- A full year of departures for every flight, on the days it operates. Existing rows (including
-- the board's hand-tuned 18–22 Nov fares) are kept. Per-flight fares wobble ±4% around the
-- seasonal factor so the cheapest flight isn't always the same one. Run in four quarters so each
-- statement stays short; it's idempotent (on conflict do nothing).
insert into public.flight_instances (flight_id, departure_date, gate, boarding_time, date_factor)
select
    f.id,
    d::date,
    f.default_gate,
    f.departure_time - make_interval(mins => case when f.duration_minutes > 360 then 55 when f.duration_minutes > 150 then 45 else 35 end),
    round(public.seasonal_factor(d::date) * (0.96 + (abs(hashtext(f.id::text || d::date)::bigint) % 9) / 100.0), 2)
from public.flights f
cross join generate_series(current_date, current_date + 365, interval '1 day') as d
where extract(isodow from d)::smallint = any (f.days_of_week)
on conflict (flight_id, departure_date) do nothing;
