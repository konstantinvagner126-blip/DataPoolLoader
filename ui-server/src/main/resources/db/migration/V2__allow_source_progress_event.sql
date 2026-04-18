alter table ui_registry.module_run_event
    drop constraint if exists chk_module_run_event_type;

alter table ui_registry.module_run_event
    add constraint chk_module_run_event_type check (
        event_type in (
            'RUN_CREATED',
            'SOURCE_STARTED',
            'SOURCE_PROGRESS',
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
    );
