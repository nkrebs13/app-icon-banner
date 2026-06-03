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
        val msg = ex.message!!
        assertTrue(msg.contains("notacolor") && msg.contains("appIconBanner"),
            "expected 'notacolor' and 'appIconBanner' in: $msg")
    }

    @Test
    fun `label containing pipe throws with a clear message`() {
        val ext = extension { buildType("debug") { label = "lab|el" } }
        val ex = assertThrows<IllegalArgumentException> {
            ext.resolveAndroid("phoneDebug", listOf("phone"), "debug")
        }
        val msg = ex.message!!
        assertTrue(msg.contains("|") && msg.contains("appIconBanner"),
            "expected '|' and 'appIconBanner' in: $msg")
    }

    @Test
    fun `null buildType returns no banner and does not throw`() {
        // AGP can pass a null buildType for headerless APK variants (e.g. dynamic feature modules).
        assertNull(extension().resolveAndroid("someVariant", emptyList(), null))
    }

    @Test
    fun `invalid color throws via iosConfigLines`() {
        val ext = extension { iosConfiguration("Debug") { color = "notacolor" } }
        val ex = assertThrows<IllegalArgumentException> { ext.iosConfigLines() }
        val msg = ex.message!!
        assertTrue(msg.contains("notacolor") && msg.contains("appIconBanner"),
            "expected 'notacolor' and 'appIconBanner' in: $msg")
    }

    @Test
    fun `label with percent sign throws with a clear message`() {
        val ext = extension { buildType("debug") { label = "%[fx:debug(1)]" } }
        val ex = assertThrows<IllegalArgumentException> {
            ext.resolveAndroid("phoneDebug", listOf("phone"), "debug")
        }
        val msg = ex.message!!
        assertTrue(msg.contains("%") && msg.contains("appIconBanner"),
            "expected '%' and 'appIconBanner' in: $msg")
    }

    @Test
    fun `label starting with at-sign throws with a clear message`() {
        val ext = extension { buildType("debug") { label = "@/etc/passwd" } }
        val ex = assertThrows<IllegalArgumentException> {
            ext.resolveAndroid("phoneDebug", listOf("phone"), "debug")
        }
        val msg = ex.message!!
        assertTrue(msg.contains("@") && msg.contains("appIconBanner"),
            "expected '@' and 'appIconBanner' in: $msg")
    }

    @Test
    fun `label with newline throws with a clear message`() {
        val ext = extension { buildType("debug") { label = "DEBUG\nINJECTED" } }
        val ex = assertThrows<IllegalArgumentException> {
            ext.resolveAndroid("phoneDebug", listOf("phone"), "debug")
        }
        val msg = ex.message!!
        assertTrue(msg.contains("newline") && msg.contains("appIconBanner"),
            "expected 'newline' and 'appIconBanner' in: $msg")
    }

    @Test
    fun `flavor name used as fallback label rejects pipe character`() {
        val ext = extension { flavor("stag|ing") {} }
        val ex = assertThrows<IllegalArgumentException> {
            ext.resolveAndroid("stag|ingDebug", listOf("stag|ing"), "debug")
        }
        val msg = ex.message!!
        assertTrue(msg.contains("|") && msg.contains("appIconBanner"),
            "expected '|' and 'appIconBanner' in: $msg")
    }

    @Test
    fun `label exceeding max length throws`() {
        val ext = extension { buildType("debug") { label = "A".repeat(101) } }
        val ex = assertThrows<IllegalArgumentException> {
            ext.resolveAndroid("debug", emptyList(), "debug")
        }
        val msg = ex.message!!
        assertTrue(msg.contains("too long"),
            "expected 'too long' in: $msg")
    }

    @Test
    fun `label at exactly max length is accepted`() {
        val ext = extension { buildType("debug") { label = "A".repeat(100) } }
        val config = ext.resolveAndroid("debug", emptyList(), "debug")
        assertEquals("A".repeat(100), config?.label)
    }

    @Test
    fun `textSizePct is passed through to BannerConfig`() {
        val ext = extension { buildType("internal") { color = "#FF6F00"; label = "INTERNAL"; textSizePct = 40 } }
        val config = ext.resolveAndroid("internal", emptyList(), "internal")
        assertEquals(40, config?.textSizePct)
    }

    @Test
    fun `textSizePct null by default`() {
        val ext = extension { buildType("debug") {} }
        val config = ext.resolveAndroid("debug", emptyList(), "debug")
        assertNull(config?.textSizePct)
    }

    @Test
    fun `textSizePct out of range throws — lower bound`() {
        val ext = extension { buildType("debug") { textSizePct = 0 } }
        val ex = assertThrows<IllegalArgumentException> {
            ext.resolveAndroid("debug", emptyList(), "debug")
        }
        assertTrue(ex.message!!.contains("textSizePct"),
            "expected 'textSizePct' in: ${ex.message}")
    }

    @Test
    fun `textSizePct out of range throws — upper bound`() {
        val ext = extension { buildType("debug") { textSizePct = 101 } }
        val ex = assertThrows<IllegalArgumentException> {
            ext.resolveAndroid("debug", emptyList(), "debug")
        }
        assertTrue(ex.message!!.contains("textSizePct"),
            "expected 'textSizePct' in: ${ex.message}")
    }

    @Test
    fun `textSizePct minimum value 1 is accepted`() {
        val ext = extension { buildType("debug") { textSizePct = 1 } }
        val config = ext.resolveAndroid("debug", emptyList(), "debug")
        assertEquals(1, config?.textSizePct)
    }

    @Test
    fun `textSizePct maximum value 100 is accepted`() {
        val ext = extension { buildType("debug") { textSizePct = 100 } }
        val config = ext.resolveAndroid("debug", emptyList(), "debug")
        assertEquals(100, config?.textSizePct)
    }

    @Test
    fun `bold flag is passed through to BannerConfig`() {
        val ext = extension { buildType("internal") { color = "#FF6F00"; label = "INTERNAL"; bold = true } }
        val config = ext.resolveAndroid("internal", emptyList(), "internal")
        assertEquals(true, config?.bold)
    }

    @Test
    fun `bold defaults to false`() {
        val ext = extension { buildType("debug") {} }
        val config = ext.resolveAndroid("debug", emptyList(), "debug")
        assertEquals(false, config?.bold)
    }

    @Test
    fun `validateAndroidResDir rejects absolute path`() {
        val ex = assertThrows<IllegalArgumentException> { validateAndroidResDir("/etc/passwd") }
        assertTrue(ex.message!!.contains("relative") && ex.message!!.contains("appIconBanner"),
            "expected 'relative' and 'appIconBanner' in: ${ex.message}")
    }

    @Test
    fun `validateAndroidResDir rejects path traversal`() {
        val ex = assertThrows<IllegalArgumentException> { validateAndroidResDir("../../etc") }
        assertTrue(ex.message!!.contains("..") && ex.message!!.contains("appIconBanner"),
            "expected '..' and 'appIconBanner' in: ${ex.message}")
    }

    @Test
    fun `validateAndroidResDir accepts normal relative path`() {
        validateAndroidResDir("src/androidMain/res")  // should not throw
    }
}
