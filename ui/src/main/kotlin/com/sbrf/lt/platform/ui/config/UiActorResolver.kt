package com.sbrf.lt.platform.ui.config

data class UiActorIdentity(
    val actorId: String,
    val actorSource: UiActorSource,
    val actorDisplayName: String = actorId,
)

enum class UiActorSource {
    OS_LOGIN,
    MANUAL_INPUT,
}

open class UiActorResolver(
    private val systemPropertyProvider: (String) -> String? = System::getProperty,
    private val environmentProvider: (String) -> String? = System::getenv,
) {
    open fun resolveAutomaticActor(): UiActorIdentity? {
        val actorId = resolveAutomaticActorId() ?: return null
        return UiActorIdentity(
            actorId = actorId,
            actorSource = UiActorSource.OS_LOGIN,
            actorDisplayName = actorId,
        )
    }

    open fun resolveManualActor(actorId: String): UiActorIdentity {
        val value = actorId.trim()
        require(value.isNotEmpty()) {
            "actorId не должен быть пустым."
        }
        return UiActorIdentity(
            actorId = value,
            actorSource = UiActorSource.MANUAL_INPUT,
            actorDisplayName = value,
        )
    }

    private fun resolveAutomaticActorId(): String? {
        val systemUser = systemPropertyProvider("user.name")?.takeIf { it.isNotBlank() }
        if (systemUser != null) {
            return systemUser
        }

        val unixUser = environmentProvider("USER")?.takeIf { it.isNotBlank() }
        if (unixUser != null) {
            return unixUser
        }

        return environmentProvider("USERNAME")?.takeIf { it.isNotBlank() }
    }
}
