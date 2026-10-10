create index ninja_mission_submitted_id_idx
    on NinjaMissionEntity (id)
    where status = 'SUBMITTED';
