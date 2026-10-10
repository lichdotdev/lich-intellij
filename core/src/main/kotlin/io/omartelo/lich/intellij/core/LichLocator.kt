package io.omartelo.lich.intellij.core

import java.io.File

/** Where `lich` is found. Injected so the search can be tested without a machine to match. */
interface Environment {
    val windows: Boolean
    val home: String
    fun getenv(name: String): String?
    fun isExecutable(path: String): Boolean

    /** Asks the user's login shell where `lich` is, or null. Only meaningful off Windows. */
    fun loginShellLookup(): String?
}

/**
 * Finds the `lich` command. An IDE started from a launcher does not get the
 * PATH a terminal does — on macOS above all, where it is a bare
 * `/usr/bin:/bin:/usr/sbin:/sbin` — so PATH alone would find lich only for
 * whoever started the IDE from a shell.
 */
object LichLocator {
    fun find(configured: String?, env: Environment): String {
        if (!configured.isNullOrBlank()) {
            if (env.isExecutable(configured)) return configured
            throw LichException("The lich command set in Settings › Tools › lich was not found: $configured")
        }
        return fromPath(env) ?: fromKnownPlaces(env) ?: fromLoginShell(env)
            ?: throw LichException(
                "The lich command was not found. Install lich, or set its path in Settings › Tools › lich.",
            )
    }

    private fun names(env: Environment): List<String> =
        if (env.windows) listOf("lich.exe", "lich.cmd", "lich.bat") else listOf("lich")

    private fun fromPath(env: Environment): String? {
        val path = env.getenv("PATH") ?: env.getenv("Path") ?: return null
        return path.split(File.pathSeparatorChar).filter { it.isNotBlank() }.firstNotNullOfOrNull { dir ->
            inDirectory(dir, env)
        }
    }

    private fun fromKnownPlaces(env: Environment): String? = knownDirectories(env).firstNotNullOfOrNull { inDirectory(it, env) }

    private fun knownDirectories(env: Environment): List<String> {
        if (!env.windows) {
            return listOf(
                "/opt/homebrew/bin", "/usr/local/bin", "/usr/bin", "${env.home}/.local/bin",
                "/Applications/Lich.app/Contents/MacOS",
            )
        }
        val local = env.getenv("LOCALAPPDATA")
        val programs = env.getenv("ProgramFiles")
        return listOfNotNull(
            "${env.home}\\scoop\\shims",
            local?.let { "$it\\Programs\\lich" },
            local?.let { "$it\\lich" },
            programs?.let { "$it\\lich" },
        )
    }

    private fun fromLoginShell(env: Environment): String? =
        if (env.windows) null else env.loginShellLookup()?.takeIf { env.isExecutable(it) }

    private fun inDirectory(dir: String, env: Environment): String? =
        names(env).map { dir.trimEnd('/', '\\') + File.separator + it }.firstOrNull { env.isExecutable(it) }
}
