package io.omartelo.lich.intellij.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathUtilTest {
    private val dir = Files.createTempDirectory("lich-path").toRealPath()

    @Test
    fun `two spellings of one directory are the same`() {
        assertTrue(PathUtil.same(dir.toString(), dir.toString() + "/"))
        assertTrue(PathUtil.same(dir.toString(), dir.resolve("a/..").toString().also { Files.createDirectories(dir.resolve("a")) }))
    }

    @Test
    fun `a symlink is the directory it points at`() {
        val link = dir.resolveSibling(dir.fileName.toString() + "-link")
        try {
            Files.createSymbolicLink(link, dir)
        } catch (_: Exception) {
            return // no symlinks on this filesystem
        }
        try {
            assertTrue(PathUtil.same(link.toString(), dir.toString()))
            Files.createDirectory(dir.resolve("x"))
            assertTrue(PathUtil.isWithin(link.resolve("x").toString(), dir.toString()))
        } finally {
            Files.delete(link)
        }
    }

    @Test
    fun `different or unusable paths are not the same`() {
        assertFalse(PathUtil.same(dir.toString(), dir.parent.toString()))
        assertFalse(PathUtil.same("", dir.toString()))
        assertFalse(PathUtil.same("relative/dir", "relative/dir"))
        assertFalse(PathUtil.same(dir.resolve("x").toString(), dir.resolve("y").toString()))
    }

    @Test
    fun `within means the folder or anything under it`() {
        assertTrue(PathUtil.isWithin(dir.toString(), dir.toString()))
        assertTrue(PathUtil.isWithin(dir.resolve("a/b").toString(), dir.toString()))
        assertFalse(PathUtil.isWithin(dir.parent.toString(), dir.toString()))
        assertFalse(PathUtil.isWithin("", dir.toString()))
    }

    @Test
    fun `a file is shown relative to the project, or whole when it lies outside`() {
        assertEquals("src/Main.kt", PathUtil.display(dir.resolve("src/Main.kt").toString(), dir.toString()))
        val outside = dir.parent.resolve("other/x.kt").toString().replace('\\', '/')
        assertEquals(outside, PathUtil.display(outside, dir.toString()))
        assertEquals("/a/b.kt", PathUtil.display("/a/b.kt", null))
    }
}
