package dev.lich.intellij.core

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LichClientTest {
    private class Recorder(private val answer: () -> Run) : CommandRunner {
        var command: List<String> = emptyList()
        var stdin: String? = null
        override fun run(command: List<String>, stdin: String?, timeoutSeconds: Long): Run {
            this.command = command
            this.stdin = stdin
            return answer()
        }
    }

    @Test
    fun `lists sessions through the cli`() {
        val runner = Recorder {
            Run(0, """[{"id":"s1","label":"a","project":"p","kind":"claude","state":"","path":"/x","projectPath":"/x"}]""", "")
        }
        val peers = LichClient("lich", runner).sessions()

        assertEquals(listOf("lich", "sessions", "--json"), runner.command)
        assertEquals("s1", peers.single().id)
    }

    @Test
    fun `an older lich is named as the problem`() {
        val runner = Recorder { Run(0, """[{"id":"s1","label":"a"}]""", "") }
        val error = assertFailsWith<LichException> { LichClient("lich", runner).sessions() }
        assertTrue(error.message!!.contains("too old"))
    }

    @Test
    fun `inserting sends the text on stdin, never in an argument`() {
        val runner = Recorder { Run(0, """{"id":"s1","label":"a","project":"p","kind":"claude","bytes":3}""", "") }
        val inserted = LichClient("/opt/lich", runner).insert("s1", "multi\nline 'quoted'")

        assertEquals(listOf("/opt/lich", "insert", "--session", "s1", "--json", "--timeout", "10", "-"), runner.command)
        assertEquals("multi\nline 'quoted'", runner.stdin)
        assertEquals("a", inserted.label)
    }

    @Test
    fun `a refusal is lich's own words`() {
        val runner = Recorder { Run(1, "", "lich: \"docs\" is waiting on a permission prompt, answer it first\nmore") }
        val error = assertFailsWith<LichException> { LichClient("lich", runner).insert("s1", "x") }
        assertEquals("\"docs\" is waiting on a permission prompt, answer it first", error.message)
    }

    @Test
    fun `a silent failure still says something`() {
        val runner = Recorder { Run(2, "", "") }
        val error = assertFailsWith<LichException> { LichClient("lich", runner).sessions() }
        assertTrue(error.message!!.contains("status 2"))
    }

    @Test
    fun `a command that cannot start is reported`() {
        val runner = CommandRunner { _, _, _ -> throw IOException("no such file") }
        val error = assertFailsWith<LichException> { LichClient("lich", runner).sessions() }
        assertTrue(error.message!!.contains("no such file"))
    }
}
