package dev.lich.intellij.core

import java.io.IOException
import java.util.concurrent.TimeUnit

/** What running the command came to. */
data class Run(val exitCode: Int, val stdout: String, val stderr: String)

/** Runs a command, feeding it [stdin]. Injected so the client is tested without a lich. */
fun interface CommandRunner {
    @Throws(IOException::class)
    fun run(command: List<String>, stdin: String?, timeoutSeconds: Long): Run
}

/** The real thing: a child process, UTF-8 both ways, killed if it outstays its timeout. */
object ProcessRunner : CommandRunner {
    override fun run(command: List<String>, stdin: String?, timeoutSeconds: Long): Run {
        val process = ProcessBuilder(command).redirectErrorStream(false).start()
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

    /** Pastes [text] at [sessionId]'s prompt, without sending it. */
    fun insert(sessionId: String, text: String): Inserted {
        val run = exec(listOf("insert", "--session", sessionId, "--json", "--timeout", "10", "-"), text, INSERT)
        return Protocol.parseInserted(run.stdout)
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
        // The insert waits up to 10s for the session's prompt (--timeout above), so the process gets a beat more.
        const val INSERT = 20L
    }
}
