create index diary_entries_user_source_created_idx
    on diary_entries(user_id, entry_type, source_revision_id, created_at desc)
    where source_revision_id is not null;

create index diary_entries_user_consumed_idx
    on diary_entries(user_id, consumed_at desc)
    where source_revision_id is not null;
