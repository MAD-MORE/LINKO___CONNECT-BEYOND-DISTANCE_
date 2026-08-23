-- LINKO real friend workflow
-- Keeps every authenticated user discoverable by creating a profile at signup,
-- protects profile/friend-request access with RLS, and keeps internal helpers private.

create or replace function public.handle_new_linko_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  candidate text;
  display_name_value text;
begin
  display_name_value := coalesce(
    nullif(trim(new.raw_user_meta_data->>'display_name'), ''),
    'LINKO User'
  );

  loop
    candidate := 'LNK-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
    begin
      insert into public.profiles (user_id, linko_id, display_name)
      values (new.id, candidate, display_name_value)
      on conflict (user_id) do update
        set display_name = coalesce(nullif(public.profiles.display_name, ''), excluded.display_name),
            updated_at = now();
      exit;
    exception when unique_violation then
      continue;
    end;
  end loop;

  return new;
end;
$$;

drop trigger if exists on_auth_user_created_linko_profile on auth.users;
create trigger on_auth_user_created_linko_profile
after insert on auth.users
for each row execute function public.handle_new_linko_user();

alter table public.profiles enable row level security;
alter table public.friend_requests enable row level security;

drop policy if exists profiles_select_authenticated on public.profiles;
create policy profiles_select_authenticated
on public.profiles for select
to authenticated
using (true);

drop policy if exists profiles_insert_self on public.profiles;
create policy profiles_insert_self
on public.profiles for insert
to authenticated
with check ((select auth.uid()) = user_id);

drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self
on public.profiles for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists friend_requests_select_participant on public.friend_requests;
create policy friend_requests_select_participant
on public.friend_requests for select
to authenticated
using ((select auth.uid()) = sender_id or (select auth.uid()) = receiver_id);

drop policy if exists friend_requests_insert_sender on public.friend_requests;
create policy friend_requests_insert_sender
on public.friend_requests for insert
to authenticated
with check ((select auth.uid()) = sender_id and sender_id <> receiver_id);

drop policy if exists friend_requests_update_receiver on public.friend_requests;
create policy friend_requests_update_receiver
on public.friend_requests for update
to authenticated
using ((select auth.uid()) = receiver_id and status = 'pending')
with check ((select auth.uid()) = receiver_id and status in ('accepted', 'declined', 'pending'));

-- Internal helpers are not public APIs. The linko-friends Edge Function uses
-- the service role and remains the only public friend-service entry point.
revoke execute on function public.ensure_linko_profile(uuid, text) from public, anon, authenticated;
revoke execute on function public.handle_new_linko_user() from public, anon, authenticated;
revoke execute on function public.search_linko_users(text, uuid) from public, anon, authenticated;
