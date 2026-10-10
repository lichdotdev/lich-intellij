package dev.lich.intellij.core

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

/** The text a gesture in the IDE becomes at the prompt. */
object Snippets {
    /**
     * `path:12-18` and, under it, the code — in a fence when [fenced], so the
     * agent and the person reading the prompt see where it starts and stops. A
     * trailing newline leaves the cursor on a fresh line to write the question
     * on. CRLF becomes LF: the prompt is not a Windows text file.
     */
    fun selection(selection: Selection, fenced: Boolean): String {
        val header = header(selection)
        val body = selection.text.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n')
        if (!fenced) return "$header\n$body\n"
        val fence = "`".repeat(maxOf(3, longestRun(body, '`') + 1))
        return "$header\n$fence${selection.language.orEmpty()}\n$body\n$fence\n"
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

    private fun header(selection: Selection): String =
        if (selection.endLine <= selection.startLine) {
            "${selection.displayPath}:${selection.startLine}"
        } else {
            "${selection.displayPath}:${selection.startLine}-${selection.endLine}"
        }

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
