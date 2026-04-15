create schema if not exists ui_registry;

create table ui_registry.module (
    module_id uuid primary key,
    module_code text not null unique,
    module_origin_kind text not null,
    current_revision_id uuid null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_module_code_not_blank check (btrim(module_code) <> ''),
    constraint chk_module_origin_kind check (module_origin_kind in ('IMPORTED_FROM_FILES', 'CREATED_IN_UI'))
);

create table ui_registry.module_revision (
    revision_id uuid primary key,
    module_id uuid not null references ui_registry.module(module_id) on delete cascade,
    revision_no bigint not null,
    created_at timestamptz not null default now(),
    created_by text null,
    revision_source text not null,
    title text not null,
    description text null,
    tags jsonb not null default '[]'::jsonb,
    hidden_from_ui boolean not null default false,
    validation_status text not null,
    validation_issues jsonb not null default '[]'::jsonb,
    output_dir text not null,
    file_format text not null,
    merge_mode text not null,
    error_mode text not null,
    parallelism integer not null,
    fetch_size integer not null,
    query_timeout_sec integer null,
    progress_log_every_rows bigint not null,
    max_merged_rows bigint null,
    delete_output_files_after_completion boolean not null,
    snapshot_json jsonb not null,
    snapshot_yaml text not null,
    content_hash text not null,
    constraint uq_module_revision_no unique (module_id, revision_no),
    constraint uq_module_revision_pair unique (module_id, revision_id),
    constraint chk_module_revision_no_positive check (revision_no > 0),
    constraint chk_revision_source check (revision_source in ('SYNC_FROM_FILES', 'PUBLISH', 'CREATE_MODULE')),
    constraint chk_revision_title_not_blank check (btrim(title) <> ''),
    constraint chk_revision_tags_is_array check (jsonb_typeof(tags) = 'array'),
    constraint chk_revision_validation_issues_is_array check (jsonb_typeof(validation_issues) = 'array'),
    constraint chk_validation_status check (validation_status in ('VALID', 'WARNING', 'INVALID')),
    constraint chk_output_dir_not_blank check (btrim(output_dir) <> ''),
    constraint chk_file_format_not_blank check (btrim(file_format) <> ''),
    constraint chk_merge_mode check (merge_mode in ('PLAIN', 'ROUND_ROBIN', 'PROPORTIONAL', 'QUOTA')),
    constraint chk_error_mode check (error_mode in ('CONTINUE_ON_ERROR')),
    constraint chk_parallelism_positive check (parallelism > 0),
    constraint chk_fetch_size_positive check (fetch_size > 0),
    constraint chk_query_timeout_positive check (query_timeout_sec is null or query_timeout_sec > 0),
    constraint chk_progress_log_every_rows_positive check (progress_log_every_rows > 0),
    constraint chk_max_merged_rows_positive check (max_merged_rows is null or max_merged_rows > 0),
    constraint chk_snapshot_json_is_object check (jsonb_typeof(snapshot_json) = 'object'),
    constraint chk_snapshot_yaml_not_blank check (btrim(snapshot_yaml) <> ''),
    constraint chk_revision_content_hash_not_blank check (btrim(content_hash) <> '')
);

create table ui_registry.module_revision_sql_asset (
    sql_asset_id uuid primary key,
    revision_id uuid not null references ui_registry.module_revision(revision_id) on delete cascade,
    asset_kind text not null,
    asset_key text not null,
    label text not null,
    sql_text text not null,
    origin_kind text not null,
    origin_path text null,
    sort_order integer not null,
    content_hash text not null,
    constraint uq_sql_asset_key unique (revision_id, asset_kind, asset_key),
    constraint uq_sql_asset_pair unique (revision_id, sql_asset_id),
    constraint chk_asset_kind check (asset_kind in ('COMMON', 'SOURCE')),
    constraint chk_asset_key_not_blank check (btrim(asset_key) <> ''),
    constraint chk_asset_label_not_blank check (btrim(label) <> ''),
    constraint chk_sql_text_not_blank check (btrim(sql_text) <> ''),
    constraint chk_origin_kind check (origin_kind in ('INLINE', 'FILE')),
    constraint chk_origin_path check (
        (origin_kind = 'INLINE' and origin_path is null)
        or
        (origin_kind = 'FILE' and origin_path is not null and btrim(origin_path) <> '')
    ),
    constraint chk_sql_asset_sort_order check (sort_order >= 0),
    constraint chk_sql_asset_content_hash_not_blank check (btrim(content_hash) <> '')
);

create table ui_registry.module_revision_source (
    revision_id uuid not null references ui_registry.module_revision(revision_id) on delete cascade,
    source_name text not null,
    sort_order integer not null,
    jdbc_url_expr text not null,
    username_expr text not null,
    password_expr text not null,
    sql_asset_id uuid not null,
    primary key (revision_id, source_name),
    constraint uq_revision_source_sort_order unique (revision_id, sort_order),
    constraint fk_revision_source_sql_asset
        foreign key (revision_id, sql_asset_id)
        references ui_registry.module_revision_sql_asset(revision_id, sql_asset_id),
    constraint chk_source_name_not_blank check (btrim(source_name) <> ''),
    constraint chk_source_sort_order check (sort_order >= 0),
    constraint chk_source_jdbc_url_not_blank check (btrim(jdbc_url_expr) <> ''),
    constraint chk_source_username_not_blank check (btrim(username_expr) <> ''),
    constraint chk_source_password_not_blank check (btrim(password_expr) <> '')
);

create table ui_registry.module_revision_target (
    revision_id uuid primary key references ui_registry.module_revision(revision_id) on delete cascade,
    enabled boolean not null,
    jdbc_url_expr text not null,
    username_expr text not null,
    password_expr text not null,
    table_name text not null,
    truncate_before_load boolean not null,
    constraint chk_target_when_enabled check (
        enabled = false
        or (
            btrim(jdbc_url_expr) <> ''
            and btrim(username_expr) <> ''
            and btrim(password_expr) <> ''
            and btrim(table_name) <> ''
        )
    )
);

create table ui_registry.module_revision_quota (
    revision_id uuid not null,
    source_name text not null,
    percent numeric(9,4) not null,
    sort_order integer not null,
    primary key (revision_id, source_name),
    constraint uq_revision_quota_sort_order unique (revision_id, sort_order),
    constraint fk_revision_quota_source
        foreign key (revision_id, source_name)
        references ui_registry.module_revision_source(revision_id, source_name)
        on delete cascade,
    constraint chk_quota_percent check (percent > 0 and percent <= 100),
    constraint chk_quota_sort_order check (sort_order >= 0)
);

create table ui_registry.module_sync_run (
    sync_run_id uuid primary key,
    started_at timestamptz not null default now(),
    finished_at timestamptz null,
    started_by_actor_id text null,
    started_by_actor_source text null,
    started_by_actor_display_name text null,
    status text not null,
    scope text not null,
    module_code text null,
    details jsonb not null default '{}'::jsonb,
    constraint chk_sync_run_status check (status in ('RUNNING', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED')),
    constraint chk_sync_run_scope check (scope in ('ALL', 'ONE')),
    constraint chk_sync_run_scope_module_code check (
        (scope = 'ALL' and module_code is null)
        or
        (scope = 'ONE' and module_code is not null and btrim(module_code) <> '')
    ),
    constraint chk_sync_run_actor_source check (
        started_by_actor_source is null
        or started_by_actor_source in ('OS_LOGIN', 'MANUAL_INPUT')
    ),
    constraint chk_sync_run_details_is_object check (jsonb_typeof(details) = 'object')
);

create table ui_registry.module_working_copy (
    working_copy_id uuid primary key,
    module_id uuid not null references ui_registry.module(module_id) on delete cascade,
    owner_actor_id text not null,
    owner_actor_source text not null,
    owner_actor_display_name text null,
    base_revision_id uuid not null,
    status text not null,
    working_copy_json jsonb not null,
    working_copy_yaml text not null,
    content_hash text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_module_working_copy_owner unique (module_id, owner_actor_id, owner_actor_source),
    constraint uq_module_working_copy_pair unique (module_id, working_copy_id),
    constraint fk_working_copy_base_revision
        foreign key (module_id, base_revision_id)
        references ui_registry.module_revision(module_id, revision_id),
    constraint chk_working_copy_owner_actor_id_not_blank check (btrim(owner_actor_id) <> ''),
    constraint chk_working_copy_owner_actor_source check (owner_actor_source in ('OS_LOGIN', 'MANUAL_INPUT')),
    constraint chk_working_copy_status check (status in ('CLEAN', 'DIRTY', 'STALE')),
    constraint chk_working_copy_json_is_object check (jsonb_typeof(working_copy_json) = 'object'),
    constraint chk_working_copy_yaml_not_blank check (btrim(working_copy_yaml) <> ''),
    constraint chk_working_copy_content_hash_not_blank check (btrim(content_hash) <> '')
);

create table ui_registry.execution_snapshot (
    execution_snapshot_id uuid primary key,
    module_id uuid not null references ui_registry.module(module_id) on delete cascade,
    requested_by_actor_id text not null,
    requested_by_actor_source text not null,
    requested_by_actor_display_name text null,
    source_revision_id uuid null,
    source_working_copy_id uuid null,
    snapshot_json jsonb not null,
    snapshot_yaml text not null,
    content_hash text not null,
    created_at timestamptz not null default now(),
    constraint uq_execution_snapshot_pair unique (module_id, execution_snapshot_id),
    constraint fk_execution_snapshot_revision
        foreign key (module_id, source_revision_id)
        references ui_registry.module_revision(module_id, revision_id),
    constraint fk_execution_snapshot_working_copy
        foreign key (module_id, source_working_copy_id)
        references ui_registry.module_working_copy(module_id, working_copy_id),
    constraint chk_execution_snapshot_actor_id_not_blank check (btrim(requested_by_actor_id) <> ''),
    constraint chk_execution_snapshot_actor_source check (requested_by_actor_source in ('OS_LOGIN', 'MANUAL_INPUT')),
    constraint chk_execution_snapshot_source_exactly_one check (
        (source_revision_id is not null and source_working_copy_id is null)
        or
        (source_revision_id is null and source_working_copy_id is not null)
    ),
    constraint chk_execution_snapshot_json_is_object check (jsonb_typeof(snapshot_json) = 'object'),
    constraint chk_execution_snapshot_yaml_not_blank check (btrim(snapshot_yaml) <> ''),
    constraint chk_execution_snapshot_content_hash_not_blank check (btrim(content_hash) <> '')
);

create table ui_registry.module_sync_run_item (
    sync_run_item_id uuid primary key,
    sync_run_id uuid not null references ui_registry.module_sync_run(sync_run_id) on delete cascade,
    module_code text not null,
    action text not null,
    status text not null,
    detected_hash text not null,
    result_revision_id uuid null references ui_registry.module_revision(revision_id),
    details jsonb not null default '{}'::jsonb,
    constraint uq_sync_run_item_module unique (sync_run_id, module_code),
    constraint chk_sync_run_item_module_code_not_blank check (btrim(module_code) <> ''),
    constraint chk_sync_run_item_action check (action in ('CREATED', 'UPDATED', 'SKIPPED', 'SKIPPED_CODE_CONFLICT', 'FAILED')),
    constraint chk_sync_run_item_status check (status in ('SUCCESS', 'WARNING', 'FAILED')),
    constraint chk_sync_run_item_detected_hash_not_blank check (btrim(detected_hash) <> ''),
    constraint chk_sync_run_item_details_is_object check (jsonb_typeof(details) = 'object')
);

create table ui_registry.module_run (
    run_id uuid primary key,
    module_id uuid not null references ui_registry.module(module_id) on delete cascade,
    execution_snapshot_id uuid not null,
    requested_by_actor_id text not null,
    requested_by_actor_source text not null,
    requested_by_actor_display_name text null,
    requested_at timestamptz not null default now(),
    started_at timestamptz null,
    finished_at timestamptz null,
    status text not null,
    launch_source_kind text not null,
    module_code_snapshot text not null,
    module_title_snapshot text not null,
    output_dir text not null,
    merge_mode text not null,
    merged_row_count bigint null,
    successful_source_count integer not null default 0,
    failed_source_count integer not null default 0,
    skipped_source_count integer not null default 0,
    target_enabled boolean not null,
    target_status text not null,
    target_table_name text null,
    target_rows_loaded bigint null,
    summary_json jsonb not null,
    error_message text null,
    constraint fk_module_run_execution_snapshot
        foreign key (module_id, execution_snapshot_id)
        references ui_registry.execution_snapshot(module_id, execution_snapshot_id),
    constraint chk_module_run_actor_id_not_blank check (btrim(requested_by_actor_id) <> ''),
    constraint chk_module_run_actor_source check (requested_by_actor_source in ('OS_LOGIN', 'MANUAL_INPUT')),
    constraint chk_module_run_status check (status in ('RUNNING', 'SUCCESS', 'SUCCESS_WITH_WARNINGS', 'FAILED', 'CANCELED')),
    constraint chk_module_run_launch_source_kind check (launch_source_kind in ('CURRENT_REVISION', 'WORKING_COPY')),
    constraint chk_module_run_module_code_snapshot_not_blank check (btrim(module_code_snapshot) <> ''),
    constraint chk_module_run_module_title_snapshot_not_blank check (btrim(module_title_snapshot) <> ''),
    constraint chk_module_run_output_dir_not_blank check (btrim(output_dir) <> ''),
    constraint chk_module_run_merge_mode check (merge_mode in ('PLAIN', 'ROUND_ROBIN', 'PROPORTIONAL', 'QUOTA')),
    constraint chk_module_run_counts_non_negative check (
        successful_source_count >= 0
        and failed_source_count >= 0
        and skipped_source_count >= 0
        and (merged_row_count is null or merged_row_count >= 0)
        and (target_rows_loaded is null or target_rows_loaded >= 0)
    ),
    constraint chk_module_run_target_status check (target_status in ('NOT_ENABLED', 'PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'SKIPPED')),
    constraint chk_module_run_finished_at check (
        (status = 'RUNNING' and finished_at is null)
        or
        (status <> 'RUNNING' and finished_at is not null)
    ),
    constraint chk_module_run_summary_json_is_object check (jsonb_typeof(summary_json) = 'object')
);

create table ui_registry.module_run_source_result (
    run_source_result_id uuid primary key,
    run_id uuid not null references ui_registry.module_run(run_id) on delete cascade,
    source_name text not null,
    sort_order integer not null,
    status text not null,
    started_at timestamptz null,
    finished_at timestamptz null,
    exported_row_count bigint null,
    merged_row_count bigint null,
    error_message text null,
    constraint uq_module_run_source_name unique (run_id, source_name),
    constraint uq_module_run_source_sort_order unique (run_id, sort_order),
    constraint chk_module_run_source_name_not_blank check (btrim(source_name) <> ''),
    constraint chk_module_run_source_sort_order check (sort_order >= 0),
    constraint chk_module_run_source_status check (status in ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'SKIPPED')),
    constraint chk_module_run_source_counts_non_negative check (
        (exported_row_count is null or exported_row_count >= 0)
        and (merged_row_count is null or merged_row_count >= 0)
    )
);

create table ui_registry.module_run_event (
    run_event_id uuid primary key,
    run_id uuid not null references ui_registry.module_run(run_id) on delete cascade,
    seq_no integer not null,
    created_at timestamptz not null default now(),
    stage text not null,
    event_type text not null,
    severity text not null,
    source_name text null,
    message text not null,
    payload_json jsonb not null default '{}'::jsonb,
    constraint uq_module_run_event_seq_no unique (run_id, seq_no),
    constraint chk_module_run_event_seq_no check (seq_no >= 0),
    constraint chk_module_run_event_stage check (stage in ('PREPARE', 'SOURCE', 'MERGE', 'TARGET', 'RUN')),
    constraint chk_module_run_event_type check (
        event_type in (
            'RUN_CREATED',
            'SOURCE_STARTED',
            'SOURCE_FINISHED',
            'SOURCE_FAILED',
            'MERGE_STARTED',
            'MERGE_FINISHED',
            'MERGE_FAILED',
            'TARGET_STARTED',
            'TARGET_FINISHED',
            'TARGET_FAILED',
            'RUN_FINISHED',
            'RUN_FAILED',
            'RUN_CANCELED'
        )
    ),
    constraint chk_module_run_event_severity check (severity in ('INFO', 'SUCCESS', 'WARNING', 'ERROR')),
    constraint chk_module_run_event_message_not_blank check (btrim(message) <> ''),
    constraint chk_module_run_event_payload_is_object check (jsonb_typeof(payload_json) = 'object')
);

create table ui_registry.module_run_artifact (
    run_artifact_id uuid primary key,
    run_id uuid not null references ui_registry.module_run(run_id) on delete cascade,
    artifact_kind text not null,
    artifact_key text not null,
    file_path text not null,
    storage_status text not null,
    file_size_bytes bigint null,
    content_hash text null,
    created_at timestamptz not null default now(),
    constraint uq_module_run_artifact unique (run_id, artifact_kind, artifact_key),
    constraint chk_module_run_artifact_kind check (artifact_kind in ('SOURCE_OUTPUT', 'MERGED_OUTPUT', 'SUMMARY_JSON')),
    constraint chk_module_run_artifact_key_not_blank check (btrim(artifact_key) <> ''),
    constraint chk_module_run_artifact_file_path_not_blank check (btrim(file_path) <> ''),
    constraint chk_module_run_artifact_storage_status check (storage_status in ('PRESENT', 'DELETED', 'MISSING')),
    constraint chk_module_run_artifact_file_size_non_negative check (file_size_bytes is null or file_size_bytes >= 0)
);

alter table ui_registry.module
    add constraint fk_module_current_revision
    foreign key (module_id, current_revision_id)
    references ui_registry.module_revision(module_id, revision_id)
    deferrable initially deferred;

create index ix_module_revision_content_hash
    on ui_registry.module_revision (content_hash);

create index ix_module_working_copy_owner
    on ui_registry.module_working_copy (owner_actor_id, owner_actor_source, updated_at desc);

create index ix_execution_snapshot_module_created_at
    on ui_registry.execution_snapshot (module_id, created_at desc);

create index ix_execution_snapshot_actor_created_at
    on ui_registry.execution_snapshot (requested_by_actor_id, requested_by_actor_source, created_at desc);

create index ix_module_sync_run_started_at
    on ui_registry.module_sync_run (started_at desc);

create index ix_module_sync_run_actor_started_at
    on ui_registry.module_sync_run (started_by_actor_id, started_by_actor_source, started_at desc);

create index ix_module_run_module_requested_at
    on ui_registry.module_run (module_id, requested_at desc);

create index ix_module_run_actor_requested_at
    on ui_registry.module_run (requested_by_actor_id, requested_by_actor_source, requested_at desc);

create index ix_module_run_status_requested_at
    on ui_registry.module_run (status, requested_at desc);
