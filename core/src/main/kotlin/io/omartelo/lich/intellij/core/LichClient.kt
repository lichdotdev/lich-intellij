package io.omartelo.lich.intellij.core

import java.io.IOException
import java.util.concurrent.TimeUnit

/** What running the command came to. */
data class Run(val exitCode: Int, val stdout: String, val stderr: String)

/** Runs a command, feeding it [stdin]. Injected so the client is tested without a lich. */
fun interface CommandRunner {
    @Throws(IOException::class)
    fun run(command: List<String>, stdin: String?, timeoutSeconds: Long): Run
}

/**
 * How every `lich` child process is started. An IDE launched from a lich
 * terminal (`idea .` in a shell session) inherits that session's environment,
 * and two of its variables would make lich mistake the IDE for the session:
 * LICH_SESSION_ID files whatever the IDE opens as that session's work, and
 * LICH_RESTART_WAIT tells a second launch it is a restart's successor, so it
 * fights the running window for its port instead of raising it.
 */
object LichProcess {
    private val SESSION_ONLY = setOf("LICH_SESSION_ID", "LICH_RESTART_WAIT")

    fun builder(command: List<String>): ProcessBuilder = ProcessBuilder(command).also { forgetSession(it.environment()) }

    fun forgetSession(environment: MutableMap<String, String>) {
        environment.keys.removeAll(SESSION_ONLY)
    }
}

/** The real thing: a child process, UTF-8 both ways, killed if it outstays its timeout. */
object ProcessRunner : CommandRunner {
    override fun run(command: List<String>, stdin: String?, timeoutSeconds: Long): Run {
        val process = LichProcess.builder(command).redirectErrorStream(false).start()
        val out = StringBuilder()
        val err = StringBuilder()
        // Drained on their own threads: a child that fills a pipe nobody reads
        // blocks, and then neither the wait nor the stdin write below finishes.
        val readers = listOf(
            Thread { out.append(process.inputStream.readBytes().toString(Charsets.UTF_8)) },
            Thread { err.append(process.errorStream.readBytes().toString(Charsets.UTF_8)) },
        ).onEach { it.isDaemon = true; it.start() }
        try {
            process.outputStream.use { if (stdin != null) it.write(stdin.toByteArray(Charsets.UTF_8)) }
        } catch (_: IOException) {
            // The child closed its end early, because it refused the call; its exit says why.
        }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IOException("timed out after ${timeoutSeconds}s")
        }
        readers.forEach { it.join(2_000) }
        return Run(process.exitValue(), out.toString(), err.toString())
    }
}

/** Talks to a running lich through its CLI, the part of it that is covered by its semver promise. */
class LichClient(
    private val command: String,
    private val runner: CommandRunner = ProcessRunner,
) {
    fun sessions(): List<Peer> {
        val run = exec(listOf("sessions", "--json"), null, SHORT)
        val peers = Protocol.parsePeers(run.stdout)
        // A lich from before `insert` answers sessions without their directories.
        // Saying so beats an editor that finds no session beside any project.
        if (peers.isNotEmpty() && peers.all { it.path.isEmpty() }) {
            throw LichException("This lich is too old for the plugin: update it to a release that has `lich insert`.")
        }
        return peers
    }

    /**
     * Pastes [text] at [sessionId]'s prompt, without sending it. lich waits up to
     * [promptTimeoutSeconds] for the prompt, which a session just opened needs while its agent starts.
     */
    fun insert(sessionId: String, text: String, promptTimeoutSeconds: Long = PROMPT): Inserted {
        val args = listOf("insert", "--session", sessionId, "--json", "--timeout", promptTimeoutSeconds.toString(), "-")
        val run = exec(args, text, promptTimeoutSeconds + GRACE)
        return Protocol.parseInserted(run.stdout)
    }

    /**
     * Opens a [kind] session in [dir]; lich opens the directory as a project first
     * when it is not holding it open. A linked worktree is opened as its main
     * checkout's project, on the branch it holds — lich reuses the worktree that
     * already holds a branch rather than making another.
     */
    fun open(dir: String, kind: String): Opened {
        val where = GitWorktree.of(dir)?.let {
            val branch = it.branch
                ?: throw LichException("This worktree is on a detached HEAD, and lich opens a worktree by its branch: check out a branch first.")
            listOf("--project", it.mainCheckout, "--worktree", branch)
        } ?: listOf("--project", dir)
        val run = exec(listOf("open") + where + listOf("--kind", kind, "--json"), null, OPEN)
        return Protocol.parseOpened(run.stdout)
    }

    /**
     * Opens [sessionId]'s card in the lich window and raises it. False when this
     * lich predates `lich focus`, for the caller to raise the window alone.
     */
    fun focus(sessionId: String): Boolean {
        try {
            exec(listOf("focus", "--json", sessionId), null, SHORT)
        } catch (e: LichException) {
            if (e.message == UNKNOWN_FOCUS) return false
            throw e
        }
        return true
    }

    private fun exec(args: List<String>, stdin: String?, timeout: Long): Run {
        val run = try {
            runner.run(listOf(command) + args, stdin, timeout)
        } catch (e: IOException) {
            throw LichException("Could not run lich: ${e.message}", e)
        }
        if (run.exitCode != 0) throw LichException(explain(run))
        return run
    }

    /** lich words its refusals for a person; the text is what to show. */
    private fun explain(run: Run): String {
        val said = run.stderr.trim().ifEmpty { run.stdout.trim() }.removePrefix("lich: ")
        if (said.isEmpty()) return "lich exited with status ${run.exitCode}"
        // A lich with no window open has nothing listening; the CLI says how it found that out.
        return said.lines().first()
    }

    private companion object {
        const val SHORT = 10L
        // What a lich from before `focus` answers, as explain() words it.
        const val UNKNOWN_FOCUS = "unknown command \"focus\""
        const val PROMPT = 10L
        // The insert waits for the session's prompt itself (--timeout), so the process gets a beat more.
        const val GRACE = 10L
        // Opening may open the project first, which is more than listing sessions.
        const val OPEN = 30L
    }
}
