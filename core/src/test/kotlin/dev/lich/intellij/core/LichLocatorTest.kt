package dev.lich.intellij.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LichLocatorTest {
    private class FakeEnv(
        override val windows: Boolean = false,
        override val home: String = "/home/me",
        private val vars: Map<String, String> = emptyMap(),
        private val executables: Set<String> = emptySet(),
        private val shell: String? = null,
    ) : Environment {
        override fun getenv(name: String) = vars[name]
        override fun isExecutable(path: String) = path in executables
        override fun loginShellLookup() = shell
    }

    private val sep = File.separator

    @Test
    fun `the path from settings wins, and must exist`() {
        val env = FakeEnv(executables = setOf("/opt/lich"), vars = mapOf("PATH" to "/usr/bin"))
        assertEquals("/opt/lich", LichLocator.find("/opt/lich", env))
        assertFailsWith<LichException> { LichLocator.find("/nope/lich", env) }
    }

    @Test
    fun `path is searched in order`() {
        val first = "/a${sep}lich"
        val second = "/b${sep}lich"
        val env = FakeEnv(vars = mapOf("PATH" to "/a${File.pathSeparator}/b"), executables = setOf(first, second))
        assertEquals(first, LichLocator.find(null, env))
    }

    @Test
    fun `an ide started from a launcher still finds lich where it is installed`() {
        val brew = "/opt/homebrew/bin${sep}lich"
        val env = FakeEnv(vars = mapOf("PATH" to "/usr/bin:/bin"), executables = setOf(brew))
        assertEquals(brew, LichLocator.find(null, env))
    }

    @Test
    fun `the login shell is the last place asked`() {
        val env = FakeEnv(executables = setOf("/weird/lich"), shell = "/weird/lich")
        assertEquals("/weird/lich", LichLocator.find("", env))
    }

    @Test
    fun `a login shell's answer that is not runnable is no answer`() {
        assertFailsWith<LichException> { LichLocator.find(null, FakeEnv(shell = "/gone/lich")) }
    }

    @Test
    fun `windows looks for the exe and for scoop's shim`() {
        val shim = "C:\\Users\\me\\scoop\\shims${sep}lich.exe"
        val env = FakeEnv(windows = true, home = "C:\\Users\\me", executables = setOf(shim))
        assertEquals(shim, LichLocator.find(null, env))
    }

    @Test
    fun `nothing found says how to fix it`() {
        val error = assertFailsWith<LichException> { LichLocator.find(null, FakeEnv()) }
        assertEquals(true, error.message!!.contains("Settings"))
    }
}
