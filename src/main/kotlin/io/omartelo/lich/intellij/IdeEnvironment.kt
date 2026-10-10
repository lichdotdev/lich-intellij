package io.omartelo.lich.intellij

import com.intellij.openapi.util.SystemInfo
import io.omartelo.lich.intellij.core.Environment
import io.omartelo.lich.intellij.core.ProcessRunner
import java.io.File

/** The machine the IDE runs on, as [io.omartelo.lich.intellij.core.LichLocator] asks about it. */
object IdeEnvironment : Environment {
    override val windows: Boolean = SystemInfo.isWindows
    override val home: String = System.getProperty("user.home").orEmpty()

    override fun getenv(name: String): String? = System.getenv(name)

    override fun isExecutable(path: String): Boolean = File(path).let { it.isFile && it.canExecute() }

    /**
     * A login shell reads the profile a terminal would, which is where a PATH the
     * launcher-started IDE never saw is set up. Bounded, because a profile that
     * waits for input must not freeze the action.
     */
    override fun loginShellLookup(): String? {
        val shell = System.getenv("SHELL")?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val run = ProcessRunner.run(listOf(shell, "-lc", "command -v lich"), null, 5)
            run.stdout.lines().map { it.trim() }.lastOrNull { it.startsWith("/") }
        } catch (_: Exception) {
            null
        }
    }
}
