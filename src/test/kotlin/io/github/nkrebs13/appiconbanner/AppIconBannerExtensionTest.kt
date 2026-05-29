package io.github.nkrebs13.appiconbanner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AppIconBannerExtensionTest {

    private fun extension(configure: AppIconBannerExtension.() -> Unit = {}) =
        AppIconBannerExtension().apply(configure)

    @Test
    fun `debug build type gets the default blue banner when unconfigured`() {
        val resolved = extension().resolveAndroid("phoneDebug", listOf("phone"), "debug")
        assertEquals(BannerConfig("#0288D1", "DEBUG"), resolved)
    }

    @Test
    fun `release build type has no banner by default`() {
        assertNull(extension().resolveAndroid("phoneRelease", listOf("phone"), "release"))
    }

    @Test
    fun `debugDefault false suppresses the debug convention`() {
        val ext = extension { debugDefault = false }
        assertNull(ext.resolveAndroid("phoneDebug", listOf("phone"), "debug"))
    }

    @Test
    fun `variant config beats flavor and build type`() {
        val ext = extension {
            buildType("debug") { color = "#111111"; label = "BT" }
            flavor("meta") { color = "#222222"; label = "FL" }
            variant("metaDebug") { color = "#333333"; label = "VAR" }
        }
        assertEquals(
            BannerConfig("#333333", "VAR"),
            ext.resolveAndroid("metaDebug", listOf("meta"), "debug"),
        )
    }

    @Test
    fun `flavor config beats build type and is not stacked`() {
        val ext = extension {
            buildType("debug") { color = "#111111"; label = "BT" }
            flavor("meta") { color = "#222222"; label = "FL" }
        }
        // metaDebug resolves to the flavor only (most specific wins, never both)
        assertEquals(
            BannerConfig("#222222", "FL"),
            ext.resolveAndroid("metaDebug", listOf("meta"), "debug"),
        )
        // phoneDebug has no flavor config -> falls through to the build type
        assertEquals(
            BannerConfig("#111111", "BT"),
            ext.resolveAndroid("phoneDebug", listOf("phone"), "debug"),
        )
    }

    @Test
    fun `label defaults to the slot name and color defaults to blue`() {
        val ext = extension { buildType("beta") {} }
        assertEquals(
            BannerConfig(AppIconBannerExtension.DEFAULT_COLOR, "beta"),
            ext.resolveAndroid("phoneBeta", listOf("phone"), "beta"),
        )
    }

    @Test
    fun `iOS resolves build type names case-insensitively`() {
        val ext = extension { buildType("debug") { color = "#0288D1"; label = "DEBUG" } }
        assertEquals(BannerConfig("#0288D1", "DEBUG"), ext.resolveIos("Debug"))
    }

    @Test
    fun `iosConfiguration override beats derived build-type entry`() {
        val ext = extension {
            buildType("debug") { color = "#000000"; label = "X" }
            iosConfiguration("Debug") { color = "#0288D1"; label = "DEBUG" }
        }
        assertEquals(BannerConfig("#0288D1", "DEBUG"), ext.resolveIos("Debug"))
    }

    @Test
    fun `iosConfigLines emits Title-cased build types plus explicit configs`() {
        val ext = extension {
            buildType("debug") { color = "#0288D1"; label = "DEBUG" }
            iosConfiguration("Firebase") { color = "#FF6F00"; label = "FIREBASE" }
        }
        val lines = ext.iosConfigLines()
        // debugDefault seeds "Debug" then the explicit debug build type overrides it to the same key
        assertEquals(listOf("Debug|#0288D1|DEBUG", "Firebase|#FF6F00|FIREBASE"), lines)
    }

    @Test
    fun `invalid color throws with a clear message`() {
        val ext = extension { buildType("debug") { color = "notacolor" } }
        val ex = assertThrows<IllegalArgumentException> {
            ext.resolveAndroid("phoneDebug", listOf("phone"), "debug")
        }
        assertTrue(ex.message!!.contains("notacolor"))
        assertTrue(ex.message!!.contains("appIconBanner"))
    }

    @Test
    fun `label containing pipe throws with a clear message`() {
        val ext = extension { buildType("debug") { label = "lab|el" } }
        val ex = assertThrows<IllegalArgumentException> {
            ext.resolveAndroid("phoneDebug", listOf("phone"), "debug")
        }
        assertTrue(ex.message!!.contains("|"))
        assertTrue(ex.message!!.contains("appIconBanner"))
    }

    @Test
    fun `null buildType returns no banner and does not throw`() {
        // AGP can pass a null buildType for headerless APK variants (e.g. dynamic feature modules).
        assertNull(extension().resolveAndroid("someVariant", emptyList(), null))
    }
}
