-- LINKO realtime publication and read policies.
-- Realtime transports state-change events; the existing Edge Function/control plane
-- remains the authoritative writer and authorization layer.

begin;

alter table public.profiles replica identity full;
alter table public.friend_requests replica identity full;
alter table public.sessions replica identity full;

-- Realtime needs authenticated readers for the rows they are allowed to observe.
drop policy if exists devices_select_self_realtime on public.devices;
create policy devices_select_self_realtime
on public.devices for select
to authenticated
using ((select auth.uid()) = user_id);

drop policy if exists sessions_select_participant_realtime on public.sessions;
create policy sessions_select_participant_realtime
on public.sessions for select
to authenticated
using (
  exists (
    select 1 from public.devices d
    where d.id = sessions.receiver_device_id
      and d.user_id = (select auth.uid())
  )
  or exists (
    select 1 from public.devices d
    where d.id = sessions.provider_device_id
      and d.user_id = (select auth.uid())
  )
);

drop policy if exists friend_requests_delete_participant on public.friend_requests;
create policy friend_requests_delete_participant
on public.friend_requests for delete
to authenticated
using ((select auth.uid()) = sender_id or (select auth.uid()) = receiver_id);

-- Add the control/state tables to Supabase Realtime safely if they are not already present.
do $$
begin
  begin alter publication supabase_realtime add table public.profiles; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.friend_requests; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.devices; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.sessions; exception when duplicate_object then null; end;
end $$;

commit;
