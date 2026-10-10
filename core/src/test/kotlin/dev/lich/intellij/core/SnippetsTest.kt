package dev.lich.intellij.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SnippetsTest {
    private fun selection(text: String, start: Int = 3, end: Int = 5, language: String? = "kotlin") =
        Selection("src/Main.kt", start, end, language, text)

    @Test
    fun `a fenced selection names its lines and language`() {
        assertEquals(
            "src/Main.kt:3-5\n```kotlin\nfun a() {}\nfun b() {}\n```\n",
            Snippets.selection(selection("fun a() {}\nfun b() {}\n"), fenced = true),
        )
    }

    @Test
    fun `one line is named by its number alone`() {
        assertEquals("src/Main.kt:7\n```kotlin\nx\n```\n", Snippets.selection(selection("x", 7, 7), fenced = true))
    }

    @Test
    fun `an unfenced selection is the header and the text`() {
        assertEquals("src/Main.kt:3-5\nx\n", Snippets.selection(selection("x"), fenced = false))
    }

    @Test
    fun `a fence outgrows any backticks in the code`() {
        val out = Snippets.selection(selection("val s = \"\"\"````\"\"\"", language = null), fenced = true)
        assertEquals("src/Main.kt:3-5\n`````\nval s = \"\"\"````\"\"\"\n`````\n", out)
    }

    @Test
    fun `windows line endings do not reach the prompt`() {
        assertEquals("src/Main.kt:3-5\na\nb\n", Snippets.selection(selection("a\r\nb\r\n"), fenced = false))
    }

    @Test
    fun `files are space separated with a trailing space`() {
        assertEquals("a/b.kt c.kt ", Snippets.files(listOf("a/b.kt", "c.kt"), FileStyle.PLAIN, windows = false))
        assertEquals("", Snippets.files(emptyList(), FileStyle.PLAIN, windows = false))
    }

    @Test
    fun `a mention puts an at sign before each path`() {
        assertEquals("@a.kt @'my file.kt' ", Snippets.files(listOf("a.kt", "my file.kt"), FileStyle.AT_MENTION, false))
    }

    @Test
    fun `a path a shell would act on is quoted the way lich quotes it`() {
        assertEquals("'my file.kt'", Snippets.quote("my file.kt", windows = false))
        assertEquals("'it'\\''s.kt'", Snippets.quote("it's.kt", windows = false))
        assertEquals("'it''s.kt'", Snippets.quote("it's.kt", windows = true))
        assertEquals("'a;b'", Snippets.quote("a;b", windows = false))
        assertEquals("C:/src/a.kt", Snippets.quote("C:/src/a.kt", windows = true))
        assertEquals("src\\a.kt", Snippets.quote("src\\a.kt", windows = true))
    }

    private fun failure(message: String? = null, stacktrace: String? = null, location: String? = "src/BarTest.kt:42") =
        TestFailure("BarTest › adds", location, message, stacktrace)

    @Test
    fun `a failure is named with where it is and its trace fenced under it`() {
        val trace = "AssertionFailedError: expected: <2> but was: <3>\n\tat BarTest.adds(BarTest.kt:42)"
        assertEquals(
            "Failing test: BarTest › adds (src/BarTest.kt:42)\n```\n$trace\n```\n",
            Snippets.testFailures(listOf(failure("expected: <2> but was: <3>", trace)), fenced = true),
        )
    }

    @Test
    fun `a message the trace does not carry is written above it`() {
        assertEquals(
            "Failing test: BarTest › adds (src/BarTest.kt:42)\nboom\n\tat X\n",
            Snippets.testFailures(listOf(failure("boom", "\tat X")), fenced = false),
        )
    }

    @Test
    fun `a failure with nothing to show is its name alone`() {
        assertEquals("Failing test: BarTest › adds\n", Snippets.testFailures(listOf(failure(location = null)), fenced = true))
    }

    @Test
    fun `several failures are separated by a blank line`() {
        val out = Snippets.testFailures(listOf(failure("a"), failure("b")), fenced = false)
        assertEquals("Failing test: BarTest › adds (src/BarTest.kt:42)\na\n\nFailing test: BarTest › adds (src/BarTest.kt:42)\nb\n", out)
    }

    @Test
    fun `problems are listed above the code they are about`() {
        val problems = listOf(Problem("ERROR", "Unresolved reference: foo"), Problem("WARNING", "Unused variable"))
        assertEquals(
            "ERROR: Unresolved reference: foo\nWARNING: Unused variable\nsrc/Main.kt:7\n```kotlin\nval x = foo()\n```\n",
            Snippets.problems(problems, selection("val x = foo()", 7, 7), fenced = true),
        )
    }

    @Test
    fun `a diff is headed by how many files it changes`() {
        val diff = "--- a/A.kt\n+++ b/A.kt\n@@ -1 +1 @@\n-x\n+y\n"
        assertEquals("Uncommitted changes, 2 files:\n```diff\n${diff.trimEnd()}\n```\n", Snippets.changes(diff, 2, fenced = true))
        assertEquals("Uncommitted changes, 1 file:\n${diff}", Snippets.changes(diff, 1, fenced = false))
    }
}
