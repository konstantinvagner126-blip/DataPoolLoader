package com.sbrf.lt.platform.ui.run

/**
 * SQL для подготовки runtime snapshot DB-модуля и записи execution snapshot.
 */
internal object DatabaseModuleExecutionSourceSql {
    fun source(schema: String): String =
        """
        select
            m.module_id::text as module_id,
            m.module_code as module_code,
            r.title as title,
            coalesce(w.working_copy_yaml, r.snapshot_yaml) as config_text,
            case
                when w.working_copy_id is null then 'CURRENT_REVISION'
                else 'WORKING_COPY'
            end as source_kind,
            case
                when w.working_copy_id is null then r.revision_id::text
                else null
            end as source_revision_id,
            w.working_copy_id::text as source_working_copy_id,
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

    fun insertExecutionSnapshot(schema: String): String =
        """
        insert into $schema.execution_snapshot (
            execution_snapshot_id,
            module_id,
            requested_by_actor_id,
            requested_by_actor_source,
            requested_by_actor_display_name,
            source_revision_id,
            source_working_copy_id,
            snapshot_json,
            snapshot_yaml,
            content_hash
        ) values (
            ?::uuid,
            ?::uuid,
            ?,
            ?,
            ?,
            ?::uuid,
            ?::uuid,
            ?::jsonb,
            ?,
            ?
        )
        """.trimIndent()
}
