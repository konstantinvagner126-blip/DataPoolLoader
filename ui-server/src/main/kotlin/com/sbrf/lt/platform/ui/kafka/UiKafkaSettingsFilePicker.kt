package com.sbrf.lt.platform.ui.kafka

import com.sbrf.lt.platform.ui.model.KafkaSettingsFilePickResponse
import java.awt.GraphicsEnvironment
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

internal interface UiKafkaSettingsFilePickerOperations {
    fun pickFile(
        targetProperty: String,
        currentValue: String,
        configBaseDir: Path?,
    ): KafkaSettingsFilePickResponse
}

internal open class DesktopUiKafkaSettingsFilePicker : UiKafkaSettingsFilePickerOperations {
    override fun pickFile(
        targetProperty: String,
        currentValue: String,
        configBaseDir: Path?,
    ): KafkaSettingsFilePickResponse {
        require(targetProperty in supportedKafkaSettingsFileTargets()) {
            "Kafka settings file chooser не поддерживает поле '$targetProperty'."
        }
        check(!GraphicsEnvironment.isHeadless()) {
            "Локальный file chooser недоступен в headless runtime."
        }
        val currentPath = resolveKafkaSettingsPath(currentValue, configBaseDir)
        val chooser = JFileChooser().apply {
            dialogTitle = kafkaSettingsFileDialogTitle(targetProperty)
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = false
            currentPath?.let { path ->
                when {
                    Files.isDirectory(path) -> currentDirectory = path.toFile()
                    Files.exists(path) -> {
                        currentDirectory = path.parent?.toFile()
                        selectedFile = path.toFile()
                    }
                    else -> currentDirectory = path.parent?.toFile()
                }
            }
            kafkaSettingsFileExtensions(targetProperty)?.let { (description, extensions) ->
                fileFilter = FileNameExtensionFilter(description, *extensions.toTypedArray())
            }
        }
        val resultRef = AtomicReference<KafkaSettingsFilePickResponse>()
        SwingUtilities.invokeAndWait {
            val choice = chooser.showOpenDialog(null)
            resultRef.set(
                if (choice == JFileChooser.APPROVE_OPTION) {
                    val selectedPath = chooser.selectedFile.toPath().toAbsolutePath().normalize()
                    KafkaSettingsFilePickResponse(
                        targetProperty = targetProperty,
                        cancelled = false,
                        selectedPath = selectedPath.toString(),
                        configValue = formatKafkaSettingsPathValue(targetProperty, selectedPath),
                    )
                } else {
                    KafkaSettingsFilePickResponse(
                        targetProperty = targetProperty,
                        cancelled = true,
                    )
                },
            )
        }
        return resultRef.get()
    }
}

internal fun supportedKafkaSettingsFileTargets(): Set<String> = setOf(
    "ssl.truststore.location",
    "ssl.truststore.certificates",
    "ssl.keystore.location",
    "ssl.keystore.certificate.chain",
    "ssl.keystore.key",
)

private fun kafkaSettingsFileDialogTitle(targetProperty: String): String =
    when (targetProperty) {
        "ssl.truststore.location" -> "Выбери truststore файл"
        "ssl.truststore.certificates" -> "Выбери trust certificates файл"
        "ssl.keystore.location" -> "Выбери keystore файл"
        "ssl.keystore.certificate.chain" -> "Выбери certificate chain файл"
        "ssl.keystore.key" -> "Выбери private key файл"
        else -> "Выбери Kafka settings файл"
    }

private fun kafkaSettingsFileExtensions(targetProperty: String): Pair<String, List<String>>? =
    when (targetProperty) {
        "ssl.truststore.location",
        "ssl.keystore.location",
            -> "Keystore files" to listOf("jks", "p12", "pfx")

        "ssl.truststore.certificates",
        "ssl.keystore.certificate.chain",
            -> "Certificate files" to listOf("pem", "crt", "cer")

        "ssl.keystore.key",
            -> "Private key files" to listOf("pem", "key")

        else -> null
    }
