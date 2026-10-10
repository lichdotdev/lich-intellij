package io.omartelo.lich.intellij.core

import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

object PathUtil {
    /**
     * Whether two spellings name the same directory. The filesystem decides where
     * it can, so case, symlinks and a trailing separator are not differences; the
     * normalized paths decide where one of them does not exist.
     */
    fun same(a: String, b: String): Boolean {
        val pa = parse(a) ?: return false
        val pb = parse(b) ?: return false
        if (pa == pb) return true
        return try {
            Files.exists(pa) && Files.exists(pb) && Files.isSameFile(pa, pb)
        } catch (_: IOException) {
            false
        }
    }

    /** Whether [child] is [parent] or lies somewhere under it. */
    fun isWithin(child: String, parent: String): Boolean {
        val pc = parse(child) ?: return false
        val pp = parse(parent) ?: return false
        if (pc.startsWith(pp)) return true
        return try {
            Files.exists(pc) && Files.exists(pp) && pc.toRealPath().startsWith(pp.toRealPath())
        } catch (_: IOException) {
            false
        }
    }

    /**
     * [file] relative to [base] with forward slashes, or the absolute path when
     * it lies outside it. A path outside the project is still a path the agent
     * can read; a `../../..` chain is only a way to get it wrong.
     */
    fun display(file: String, base: String?): String {
        val pf = parse(file) ?: return file
        val pb = base?.let { parse(it) }
        val shown = if (pb != null && pf.startsWith(pb)) pb.relativize(pf) else pf
        return shown.toString().replace('\\', '/')
    }

    private fun parse(raw: String): Path? {
        if (raw.isBlank()) return null
        return try {
            val path = Paths.get(raw)
            if (path.isAbsolute) path.normalize() else null
        } catch (_: InvalidPathException) {
            null
        }
    }
}
