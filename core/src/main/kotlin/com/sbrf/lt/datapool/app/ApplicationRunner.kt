package com.sbrf.lt.datapool.app

import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.datapool.config.ValueResolver
import com.sbrf.lt.datapool.app.port.ResultMerger
import com.sbrf.lt.datapool.app.port.SourceExporter
import com.sbrf.lt.datapool.app.port.TargetImporter
import com.sbrf.lt.datapool.app.port.TargetSchemaValidator
import com.sbrf.lt.datapool.db.PostgresSourceExporter
import com.sbrf.lt.datapool.db.PostgresTargetImporter
import com.sbrf.lt.datapool.db.TargetTableValidator
import com.sbrf.lt.datapool.merge.MergeService
import java.nio.file.Files
import java.nio.file.Path

class ApplicationRunner(
    private val configLoader: ConfigLoader = ConfigLoader(),
    private val exporter: SourceExporter = PostgresSourceExporter(),
    private val mergeService: ResultMerger = MergeService(),
    private val targetTableValidator: TargetSchemaValidator = TargetTableValidator(),
    private val importer: TargetImporter = PostgresTargetImporter(),
) {
    private val support = ApplicationRunnerSupport(
        exporter = exporter,
        targetTableValidator = targetTableValidator,
        importer = importer,
    )
    private val pipelineSupport = ApplicationRunnerPipelineSupport(
        mergeService = mergeService,
        support = support,
    )

    fun run(
        configPath: Path,
        credentialsPath: Path? = support.defaultCredentialsPath(),
        executionListener: ExecutionListener = NoOpExecutionListener,
    ): ApplicationRunResult {
        val appConfig = configLoader.load(configPath)
        val configYaml = Files.newBufferedReader(configPath).use { it.readText() }
        return run(
            snapshot = RuntimeModuleSnapshot(
                moduleCode = null,
                moduleTitle = null,
                configYaml = configYaml,
                sqlFiles = emptyMap(),
                appConfig = appConfig,
                launchSourceKind = "FILES",
                configLocation = configPath.toString(),
            ),
            credentialsPath = credentialsPath,
            executionListener = executionListener,
        )
    }

    fun run(
        snapshot: RuntimeModuleSnapshot,
        credentialsPath: Path? = support.defaultCredentialsPath(),
        executionListener: ExecutionListener = NoOpExecutionListener,
    ): ApplicationRunResult {
        BannerPrinter.printBanner()
        return pipelineSupport.runSnapshot(
            snapshot = snapshot,
            credentialsPath = credentialsPath,
            executionListener = executionListener,
        )
    }
}
