-- Advisor fixes.
-- handle_new_user is only ever run by the auth.users trigger; nobody should call it over the API.
revoke execute on function public.handle_new_user() from public, anon, authenticated;

-- The seat map needs a signed-in user (guests sign in anonymously, which is the authenticated role).
revoke execute on function public.get_seat_map(uuid) from public, anon;
grant execute on function public.get_seat_map(uuid) to authenticated;

-- create_booking, pay_booking and get_seat_map stay SECURITY DEFINER for authenticated users on
-- purpose: they compute prices server-side, hold seats across users and only ever touch the
-- caller's own rows (auth.uid()), or return booleans (seat taken).
