package com.sbrf.lt.platform.ui.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UiActorResolverTest {

    @Test
    fun `resolves actor from user name system property without normalization`() {
        val resolver = UiActorResolver(
            systemPropertyProvider = { key -> if (key == "user.name") "DOMAIN\\User Name" else null },
            environmentProvider = { null },
        )

        val actor = resolver.resolveAutomaticActor()

        requireNotNull(actor)
        assertEquals("DOMAIN\\User Name", actor.actorId)
        assertEquals(UiActorSource.OS_LOGIN, actor.actorSource)
        assertEquals("DOMAIN\\User Name", actor.actorDisplayName)
    }

    @Test
    fun `falls back to USER environment variable`() {
        val resolver = UiActorResolver(
            systemPropertyProvider = { null },
            environmentProvider = { key -> if (key == "USER") "linux-user" else null },
        )

        val actor = resolver.resolveAutomaticActor()

        requireNotNull(actor)
        assertEquals("linux-user", actor.actorId)
    }

    @Test
    fun `falls back to USERNAME environment variable`() {
        val resolver = UiActorResolver(
            systemPropertyProvider = { null },
            environmentProvider = { key -> if (key == "USERNAME") "windows-user" else null },
        )

        val actor = resolver.resolveAutomaticActor()

        requireNotNull(actor)
        assertEquals("windows-user", actor.actorId)
    }

    @Test
    fun `returns null when automatic actor is not available`() {
        val resolver = UiActorResolver(
            systemPropertyProvider = { null },
            environmentProvider = { null },
        )

        assertNull(resolver.resolveAutomaticActor())
    }

    @Test
    fun `manual actor keeps entered value after trim`() {
        val actor = UiActorResolver().resolveManualActor("  kwdev  ")

        assertEquals("kwdev", actor.actorId)
        assertEquals(UiActorSource.MANUAL_INPUT, actor.actorSource)
        assertEquals("kwdev", actor.actorDisplayName)
    }

    @Test
    fun `manual actor rejects blank value`() {
        assertFailsWith<IllegalArgumentException> {
            UiActorResolver().resolveManualActor("   ")
        }
    }
}
