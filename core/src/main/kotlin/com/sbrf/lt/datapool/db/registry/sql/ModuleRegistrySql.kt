package com.sbrf.lt.datapool.db.registry.sql

/**
 * SQL-запросы PostgreSQL registry для хранилища модулей.
 */
object ModuleRegistrySql {
    fun catalog(schema: String): String =
        """
        select
            m.module_code as module_code,
            r.title as title,
            r.description as description,
            r.tags::text as tags_json,
            r.validation_status as validation_status,
            r.validation_issues::text as validation_issues_json
        from $schema.module m
        join $schema.module_revision r
            on r.module_id = m.module_id
            and r.revision_id = m.current_revision_id
        where (? = true or r.hidden_from_ui = false)
        order by m.module_code
        """.trimIndent()

    fun details(schema: String): String =
        """
        select
            m.module_code as module_code,
            r.revision_id::text as current_revision_id,
            r.title as title,
            r.description as description,
            r.tags::text as tags_json,
            r.validation_status as validation_status,
            r.validation_issues::text as validation_issues_json,
            coalesce(w.working_copy_yaml, r.snapshot_yaml) as config_text,
            case
                when w.working_copy_id is null then 'CURRENT_REVISION'
                else 'WORKING_COPY'
            end as source_kind,
            w.working_copy_id::text as working_copy_id,
            w.status as working_copy_status,
            w.base_revision_id::text as base_revision_id,
            w.working_copy_json::text as working_copy_json
        from $schema.module m
        join $schema.module_revision r
            on r.module_id = m.module_id
            and r.revision_id = m.current_revision_id
        left join $schema.module_working_copy w
            on w.module_id = m.module_id
            and w.owner_actor_id = ?
            and w.owner_actor_source = ?
        where m.module_code = ?
        """.trimIndent()

    fun sqlAssets(schema: String): String =
        """
        select
            label,
            asset_key,
            sql_text
        from $schema.module_revision_sql_asset
        where revision_id = ?::uuid
        order by sort_order, label
        """.trimIndent()

    fun moduleForSave(schema: String): String =
        """
        select
            m.module_id::text as module_id,
            m.current_revision_id::text as current_revision_id,
            w.working_copy_id::text as working_copy_id,
            w.status as working_copy_status
        from $schema.module m
        left join $schema.module_working_copy w
            on w.module_id = m.module_id
            and w.owner_actor_id = ?
            and w.owner_actor_source = ?
        where m.module_code = ?
        """.trimIndent()

    fun upsertWorkingCopy(schema: String): String =
        """
        insert into $schema.module_working_copy (
            working_copy_id,
            module_id,
            owner_actor_id,
            owner_actor_source,
            owner_actor_display_name,
            base_revision_id,
            status,
            working_copy_json,
            working_copy_yaml,
            content_hash
        ) values (
            ?::uuid,
            ?::uuid,
            ?,
            ?,
            ?,
            ?::uuid,
            'DIRTY',
            ?::jsonb,
            ?,
            ?
        )
        on conflict (module_id, owner_actor_id, owner_actor_source)
        do update set
            owner_actor_display_name = excluded.owner_actor_display_name,
            status = case
                when $schema.module_working_copy.status = 'STALE' then 'STALE'
                else 'DIRTY'
            end,
            working_copy_json = excluded.working_copy_json,
            working_copy_yaml = excluded.working_copy_yaml,
            content_hash = excluded.content_hash,
            updated_at = now()
        """.trimIndent()

    fun discardWorkingCopy(schema: String): String =
        """
        delete from $schema.module_working_copy w
        using $schema.module m
        where w.module_id = m.module_id
            and w.owner_actor_id = ?
            and w.owner_actor_source = ?
            and m.module_code = ?
        """.trimIndent()

    fun moduleForPublish(schema: String): String =
        """
        select
            m.module_id::text as module_id,
            m.current_revision_id::text as current_revision_id,
            r.revision_no as max_revision_no,
            w.working_copy_id::text as working_copy_id,
            w.base_revision_id::text as base_revision_id,
            w.status as working_copy_status,
            w.working_copy_json::text as working_copy_json,
            w.working_copy_yaml as working_copy_yaml,
            w.content_hash as content_hash
        from $schema.module m
        join $schema.module_revision r
            on r.module_id = m.module_id
            and r.revision_id = m.current_revision_id
        left join $schema.module_working_copy w
            on w.module_id = m.module_id
            and w.owner_actor_id = ?
            and w.owner_actor_source = ?
        where m.module_code = ?
        """.trimIndent()

    fun insertRevision(schema: String): String =
        """
        insert into $schema.module_revision (
            revision_id,
            module_id,
            revision_no,
            created_by,
            revision_source,
            title,
            description,
            tags,
            hidden_from_ui,
            validation_status,
            validation_issues,
            output_dir,
            file_format,
            merge_mode,
            error_mode,
            parallelism,
            fetch_size,
            query_timeout_sec,
            progress_log_every_rows,
            max_merged_rows,
            delete_output_files_after_completion,
            snapshot_json,
            snapshot_yaml,
            content_hash
        ) values (
            ?::uuid,
            ?::uuid,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?::jsonb,
            ?,
            'VALID',
            '[]'::jsonb,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?::jsonb,
            ?,
            ?
        )
        """.trimIndent()

    fun insertPublishedRevision(schema: String): String =
        """
        insert into $schema.module_revision (
            revision_id,
            module_id,
            revision_no,
            created_by,
            revision_source,
            title,
            description,
            tags,
            hidden_from_ui,
            validation_status,
            validation_issues,
            output_dir,
            file_format,
            merge_mode,
            error_mode,
            parallelism,
            fetch_size,
            query_timeout_sec,
            progress_log_every_rows,
            max_merged_rows,
            delete_output_files_after_completion,
            snapshot_json,
            snapshot_yaml,
            content_hash
        )
        select
            ?::uuid,
            r.module_id,
            ?,
            ?,
            'PUBLISH',
            r.title,
            r.description,
            r.tags,
            r.hidden_from_ui,
            r.validation_status,
            r.validation_issues,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?::jsonb,
            ?,
            ?
        from $schema.module_revision r
        where r.module_id = ?::uuid
            and r.revision_id = ?::uuid
        """.trimIndent()

    fun copySqlAssets(schema: String): String =
        """
        insert into $schema.module_revision_sql_asset (
            sql_asset_id,
            revision_id,
            asset_kind,
            asset_key,
            label,
            sql_text,
            origin_kind,
            origin_path,
            sort_order,
            content_hash
        ) values (
            ?::uuid,
            ?::uuid,
            ?,
            ?,
            ?,
            ?,
            'INLINE',
            null,
            ?,
            ?
        )
        """.trimIndent()

    fun insertRevisionSource(schema: String): String =
        """
        insert into $schema.module_revision_source (
            revision_id,
            source_name,
            sort_order,
            jdbc_url_expr,
            username_expr,
            password_expr,
            sql_asset_id
        ) values (
            ?::uuid,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?::uuid
        )
        """.trimIndent()

    fun insertRevisionTarget(schema: String): String =
        """
        insert into $schema.module_revision_target (
            revision_id,
            enabled,
            jdbc_url_expr,
            username_expr,
            password_expr,
            table_name,
            truncate_before_load
        ) values (
            ?::uuid,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?
        )
        """.trimIndent()

    fun insertRevisionQuota(schema: String): String =
        """
        insert into $schema.module_revision_quota (
            revision_id,
            source_name,
            percent,
            sort_order
        ) values (
            ?::uuid,
            ?,
            ?,
            ?
        )
        """.trimIndent()

    fun updateCurrentRevision(schema: String): String =
        """
        update $schema.module
        set current_revision_id = ?::uuid,
            updated_at = now()
        where module_id = ?::uuid
        """.trimIndent()

    fun deleteWorkingCopyAfterPublish(schema: String): String =
        """
        delete from $schema.module_working_copy w
        using $schema.module m
        where w.module_id = m.module_id
            and w.owner_actor_id = ?
            and w.owner_actor_source = ?
            and m.module_id = ?::uuid
        """.trimIndent()

    fun insertModule(schema: String): String =
        """
        insert into $schema.module (
            module_id,
            module_code,
            module_origin_kind,
            current_revision_id
        ) values (
            ?::uuid,
            ?,
            ?,
            null
        )
        """.trimIndent()

    fun deleteWorkingCopyForModule(schema: String): String =
        """
        delete from $schema.module_working_copy
        where module_id = ?::uuid
        """.trimIndent()

    fun deleteModule(schema: String): String =
        """
        delete from $schema.module
        where module_id = ?::uuid
        """.trimIndent()

    fun checkActiveRun(schema: String): String =
        """
        select count(*) as active_runs
        from $schema.module_run mr
        join $schema.module m on m.module_id = mr.module_id
        where m.module_code = ?
            and mr.status = 'RUNNING'
        """.trimIndent()
}
