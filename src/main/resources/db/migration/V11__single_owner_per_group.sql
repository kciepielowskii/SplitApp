CREATE UNIQUE INDEX uq_group_members_single_owner
    ON group_members (group_id)
    WHERE role = 'OWNER';
