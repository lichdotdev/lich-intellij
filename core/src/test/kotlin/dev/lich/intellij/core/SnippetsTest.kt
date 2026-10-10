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
}
