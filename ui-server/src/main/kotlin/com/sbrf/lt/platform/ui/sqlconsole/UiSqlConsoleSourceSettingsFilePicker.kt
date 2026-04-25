package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.platform.ui.model.SqlConsoleSourceSettingsFilePickResponse
import java.awt.GraphicsEnvironment
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

internal interface UiSqlConsoleSourceSettingsFilePickerOperations {
    fun pickCredentialsFile(
        currentValue: String,
        configBaseDir: Path?,
    ): SqlConsoleSourceSettingsFilePickResponse
}

internal open class DesktopUiSqlConsoleSourceSettingsFilePicker : UiSqlConsoleSourceSettingsFilePickerOperations {
    override fun pickCredentialsFile(
        currentValue: String,
        configBaseDir: Path?,
    ): SqlConsoleSourceSettingsFilePickResponse {
        check(!GraphicsEnvironment.isHeadless()) {
            "Локальный file chooser недоступен в headless runtime."
        }
        val currentPath = resolveSqlConsoleSettingsPath(currentValue, configBaseDir)
        val chooser = JFileChooser().apply {
            dialogTitle = "Выбери credential.properties"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = false
            fileFilter = FileNameExtensionFilter("Properties files (.properties)", "properties")
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
        }
        val resultRef = AtomicReference<SqlConsoleSourceSettingsFilePickResponse>()
        SwingUtilities.invokeAndWait {
            val choice = chooser.showOpenDialog(null)
            resultRef.set(
                if (choice == JFileChooser.APPROVE_OPTION) {
                    val selectedPath = chooser.selectedFile.toPath().toAbsolutePath().normalize()
                    SqlConsoleSourceSettingsFilePickResponse(
                        cancelled = false,
                        selectedPath = selectedPath.toString(),
                        configValue = selectedPath.toString(),
                    )
                } else {
                    SqlConsoleSourceSettingsFilePickResponse(cancelled = true)
                },
            )
        }
        return resultRef.get()
    }
}

internal fun resolveSqlConsoleSettingsPath(
    rawValue: String,
    configBaseDir: Path?,
): Path? {
    val trimmed = rawValue.trim().takeIf { it.isNotEmpty() } ?: return null
    val rawPath = runCatching { Path.of(trimmed) }.getOrNull() ?: return null
    return when {
        rawPath.isAbsolute -> rawPath.normalize()
        configBaseDir != null -> configBaseDir.resolve(rawPath).normalize()
        else -> rawPath.toAbsolutePath().normalize()
    }
}
