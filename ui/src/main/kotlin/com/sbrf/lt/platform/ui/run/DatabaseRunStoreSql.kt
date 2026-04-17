package com.sbrf.lt.platform.ui.run

/**
 * SQL-запросы для DB run-history (`module_run*`).
 */
internal object DatabaseRunStoreSql {
    fun hasActiveRun(schema: String): String =
        """
        select count(*) as active_runs
        from $schema.module_run mr
        join $schema.module m on m.module_id = mr.module_id
        where m.module_code = ?
          and mr.status = 'RUNNING'
        """.trimIndent()

    fun listActiveRunIds(schema: String): String =
        """
        select mr.run_id::text as run_id
        from $schema.module_run mr
        join $schema.module m on m.module_id = mr.module_id
        where m.module_code = ?
          and mr.status = 'RUNNING'
        order by mr.requested_at desc
        """.trimIndent()

    fun insertRun(schema: String): String =
        """
        insert into $schema.module_run (
            run_id,
            module_id,
            execution_snapshot_id,
            requested_by_actor_id,
            requested_by_actor_source,
            requested_by_actor_display_name,
            requested_at,
            started_at,
            finished_at,
            status,
            launch_source_kind,
            module_code_snapshot,
            module_title_snapshot,
            output_dir,
            merge_mode,
            merged_row_count,
            successful_source_count,
            failed_source_count,
            skipped_source_count,
            target_enabled,
            target_status,
            target_table_name,
            target_rows_loaded,
            summary_json,
            error_message
        )
        select
            ?::uuid,
            es.module_id,
            es.execution_snapshot_id,
            ?,
            ?,
            ?,
            ?::timestamptz,
            ?::timestamptz,
            null,
            'RUNNING',
            ?,
            ?,
            ?,
            ?,
            ?,
            null,
            0,
            0,
            0,
            ?,
            ?,
            ?,
            null,
            '{}'::jsonb,
            null
        from $schema.execution_snapshot es
        where es.execution_snapshot_id = ?::uuid
        """.trimIndent()

    fun insertSourceResult(schema: String): String =
        """
        insert into $schema.module_run_source_result (
            run_source_result_id,
            run_id,
            source_name,
            sort_order,
            status,
            started_at,
            finished_at,
            exported_row_count,
            merged_row_count,
            error_message
        ) values (
            ?::uuid,
            ?::uuid,
            ?,
            ?,
            'PENDING',
            null,
            null,
            null,
            null,
            null
        )
        """.trimIndent()

    fun updateSourceStarted(schema: String): String =
        """
        update $schema.module_run_source_result
        set status = 'RUNNING',
            started_at = coalesce(started_at, ?::timestamptz)
        where run_id = ?::uuid
          and source_name = ?
        """.trimIndent()

    fun updateSourceFinished(schema: String): String =
        """
        update $schema.module_run_source_result
        set status = ?,
            finished_at = ?::timestamptz,
            exported_row_count = ?,
            error_message = ?
        where run_id = ?::uuid
          and source_name = ?
        """.trimIndent()

    fun updateSourceMismatch(schema: String): String =
        """
        update $schema.module_run_source_result
        set status = 'SKIPPED',
            finished_at = ?::timestamptz,
            error_message = ?
        where run_id = ?::uuid
          and source_name = ?
        """.trimIndent()

    fun updateSourceMergedRows(schema: String): String =
        """
        update $schema.module_run_source_result
        set merged_row_count = ?
        where run_id = ?::uuid
          and source_name = ?
        """.trimIndent()

    fun updateTargetStatus(schema: String): String =
        """
        update $schema.module_run
        set target_status = ?,
            target_table_name = coalesce(?, target_table_name),
            target_rows_loaded = ?
        where run_id = ?::uuid
        """.trimIndent()

    fun updateMergedRowCount(schema: String): String =
        """
        update $schema.module_run
        set merged_row_count = ?
        where run_id = ?::uuid
        """.trimIndent()

    fun insertEvent(schema: String): String =
        """
        insert into $schema.module_run_event (
            run_event_id,
            run_id,
            seq_no,
            stage,
            event_type,
            severity,
            source_name,
            message,
            payload_json
        ) values (
            ?::uuid,
            ?::uuid,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?::jsonb
        )
        """.trimIndent()

    fun upsertArtifact(schema: String): String =
        """
        insert into $schema.module_run_artifact (
            run_artifact_id,
            run_id,
            artifact_kind,
            artifact_key,
            file_path,
            storage_status,
            file_size_bytes,
            content_hash
        ) values (
            ?::uuid,
            ?::uuid,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?
        )
        on conflict (run_id, artifact_kind, artifact_key)
        do update set
            file_path = excluded.file_path,
            storage_status = excluded.storage_status,
            file_size_bytes = excluded.file_size_bytes,
            content_hash = excluded.content_hash
        """.trimIndent()

    fun markArtifactDeleted(schema: String): String =
        """
        update $schema.module_run_artifact
        set storage_status = 'DELETED'
        where run_id = ?::uuid
          and artifact_kind = ?
          and artifact_key = ?
        """.trimIndent()

    fun finishRun(schema: String): String =
        """
        update $schema.module_run
        set finished_at = ?::timestamptz,
            status = ?,
            merged_row_count = ?,
            successful_source_count = ?,
            failed_source_count = ?,
            skipped_source_count = ?,
            target_status = ?,
            target_table_name = ?,
            target_rows_loaded = ?,
            summary_json = ?::jsonb,
            error_message = ?
        where run_id = ?::uuid
        """.trimIndent()

    fun markRunFailed(schema: String): String =
        """
        update $schema.module_run
        set finished_at = ?::timestamptz,
            status = 'FAILED',
            target_status = case
                when target_status = 'NOT_ENABLED' then 'NOT_ENABLED'
                else 'FAILED'
            end,
            error_message = ?
        where run_id = ?::uuid
          and status = 'RUNNING'
        """.trimIndent()

    fun markIncompleteSourcesFailed(schema: String): String =
        """
        update $schema.module_run_source_result
        set status = 'FAILED',
            finished_at = coalesce(finished_at, ?::timestamptz),
            error_message = coalesce(error_message, ?)
        where run_id = ?::uuid
          and status in ('PENDING', 'RUNNING')
        """.trimIndent()

    fun listRuns(schema: String): String =
        """
        select
            mr.run_id::text as run_id,
            mr.execution_snapshot_id::text as execution_snapshot_id,
            mr.status as status,
            mr.launch_source_kind as launch_source_kind,
            mr.requested_at as requested_at,
            mr.started_at as started_at,
            mr.finished_at as finished_at,
            mr.module_code_snapshot as module_code_snapshot,
            mr.module_title_snapshot as module_title_snapshot,
            mr.output_dir as output_dir,
            mr.merged_row_count as merged_row_count,
            mr.successful_source_count as successful_source_count,
            mr.failed_source_count as failed_source_count,
            mr.skipped_source_count as skipped_source_count,
            mr.target_status as target_status,
            mr.target_table_name as target_table_name,
            mr.target_rows_loaded as target_rows_loaded,
            mr.error_message as error_message
        from $schema.module_run mr
        join $schema.module m on m.module_id = mr.module_id
        where m.module_code = ?
        order by mr.requested_at desc
        limit ?
        """.trimIndent()

    fun loadRunDetails(schema: String): String =
        """
        select
            mr.run_id::text as run_id,
            mr.execution_snapshot_id::text as execution_snapshot_id,
            mr.status as status,
            mr.launch_source_kind as launch_source_kind,
            mr.requested_at as requested_at,
            mr.started_at as started_at,
            mr.finished_at as finished_at,
            mr.module_code_snapshot as module_code_snapshot,
            mr.module_title_snapshot as module_title_snapshot,
            mr.output_dir as output_dir,
            mr.merged_row_count as merged_row_count,
            mr.successful_source_count as successful_source_count,
            mr.failed_source_count as failed_source_count,
            mr.skipped_source_count as skipped_source_count,
            mr.target_status as target_status,
            mr.target_table_name as target_table_name,
            mr.target_rows_loaded as target_rows_loaded,
            mr.error_message as error_message,
            mr.summary_json::text as summary_json
        from $schema.module_run mr
        join $schema.module m on m.module_id = mr.module_id
        where m.module_code = ?
          and mr.run_id = ?::uuid
        """.trimIndent()

    fun listRunSourceResults(schema: String): String =
        """
        select
            run_source_result_id::text as run_source_result_id,
            source_name,
            sort_order,
            status,
            started_at,
            finished_at,
            exported_row_count,
            merged_row_count,
            error_message
        from $schema.module_run_source_result
        where run_id = ?::uuid
        order by sort_order asc
        """.trimIndent()

    fun listRunEvents(schema: String): String =
        """
        select
            run_event_id::text as run_event_id,
            seq_no,
            created_at,
            stage,
            event_type,
            severity,
            source_name,
            message,
            payload_json::text as payload_json
        from $schema.module_run_event
        where run_id = ?::uuid
        order by seq_no asc
        """.trimIndent()

    fun listRunArtifacts(schema: String): String =
        """
        select
            run_artifact_id::text as run_artifact_id,
            artifact_kind,
            artifact_key,
            file_path,
            storage_status,
            file_size_bytes,
            content_hash,
            created_at
        from $schema.module_run_artifact
        where run_id = ?::uuid
        order by artifact_kind asc, artifact_key asc
        """.trimIndent()
}
