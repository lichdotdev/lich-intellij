package io.omartelo.lich.intellij.core

/** How a file is named at the prompt. */
enum class FileStyle {
    /** The path alone, which every provider reads. */
    PLAIN,

    /** `@path`, the mention Claude Code and several others resolve into the file. */
    AT_MENTION,
}

/** A range of an editor's text, with where it came from. Lines are 1-based and inclusive. */
data class Selection(
    val displayPath: String,
    val startLine: Int,
    val endLine: Int,
    val language: String?,
    val text: String,
)

/** A test that failed, as the IDE's test runner reports it. [location] is `path:line`, when the runner can tell. */
data class TestFailure(
    val name: String,
    val location: String?,
    val message: String?,
    val stacktrace: String?,
)

/** An error or warning the IDE shows in the code. [severity] is the IDE's name for it: ERROR, WARNING, ... */
data class Problem(val severity: String, val message: String)

/** The text a gesture in the IDE becomes at the prompt. */
object Snippets {
    /**
     * `path:12-18` and, under it, the code — in a fence when [fenced], so the
     * agent and the person reading the prompt see where it starts and stops. A
     * trailing newline leaves the cursor on a fresh line to write the question
     * on.
     */
    fun selection(selection: Selection, fenced: Boolean): String {
        return block(header(selection), selection.text, selection.language, fenced)
    }

    /** One `SEVERITY: message` line per problem, then the code they are about, as [selection] writes it. */
    fun problems(problems: List<Problem>, code: Selection, fenced: Boolean): String =
        problems.joinToString("") { "${it.severity}: ${it.message}\n" } + selection(code, fenced)

    /** A unified [diff] of uncommitted changes, headed by how many files it touches. */
    fun changes(diff: String, files: Int, fenced: Boolean): String =
        block("Uncommitted changes, $files ${if (files == 1) "file" else "files"}:", diff, "diff", fenced)

    /**
     * Each failure under a `Failing test:` line naming it and where it is, with
     * its trace. Runners mostly start the trace with the exception and its
     * message, so the message is written apart only when the trace lacks it.
     */
    fun testFailures(failures: List<TestFailure>, fenced: Boolean): String =
        failures.joinToString("\n") { failure ->
            val header = "Failing test: ${failure.name}" + (failure.location?.let { " ($it)" } ?: "")
            val message = failure.message?.takeIf { it.isNotBlank() }
            val trace = failure.stacktrace?.takeIf { it.isNotBlank() }
            val body = when {
                trace == null -> message
                message == null || trace.contains(message) -> trace
                else -> "$message\n$trace"
            }
            if (body == null) "$header\n" else block(header, body, null, fenced)
        }

    /** [header], then [text] under it — in a fence when [fenced]. CRLF becomes LF: the prompt is not a Windows text file. */
    private fun block(header: String, text: String, language: String?, fenced: Boolean): String {
        val body = text.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n')
        if (!fenced) return "$header\n$body\n"
        val fence = "`".repeat(maxOf(3, longestRun(body, '`') + 1))
        return "$header\n$fence${language.orEmpty()}\n$body\n$fence\n"
    }

    /**
     * Paths for the prompt, space separated with a trailing space so the next
     * word does not run into the last one — the way lich's own drop of files onto
     * a terminal writes them. A path a shell would act on is single-quoted, as
     * lich does, because the prompt underneath may well be a shell.
     */
    fun files(paths: List<String>, style: FileStyle, windows: Boolean): String {
        if (paths.isEmpty()) return ""
        return paths.joinToString(" ") { mention(it, style, windows) } + " "
    }

    private fun mention(path: String, style: FileStyle, windows: Boolean): String {
        val quoted = quote(path, windows)
        return if (style == FileStyle.AT_MENTION) "@$quoted" else quoted
    }

    private fun header(selection: Selection): String = location(selection.displayPath, selection.startLine, selection.endLine)

    /** `path:12` for one line, `path:12-18` for several: where a selection or a review comment is. */
    fun location(displayPath: String, startLine: Int, endLine: Int): String =
        if (endLine <= startLine) "$displayPath:$startLine" else "$displayPath:$startLine-$endLine"

    // Mirrors quotePath in lich's frontend (drop-files.ts) and internal/shquote:
    // an ordinary path of either OS is written bare, anything else is single
    // quoted, and the two hosts differ only in how a quote inside escapes.
    private val safe = Regex("^[\\w@%+=:,./\\\\-]+$")

    fun quote(path: String, windows: Boolean): String {
        if (safe.matches(path)) return path
        return if (windows) "'" + path.replace("'", "''") + "'" else "'" + path.replace("'", "'\\''") + "'"
    }

    private fun longestRun(text: String, ch: Char): Int {
        var longest = 0
        var run = 0
        for (c in text) {
            run = if (c == ch) run + 1 else 0
            if (run > longest) longest = run
        }
        return longest
    }
}
