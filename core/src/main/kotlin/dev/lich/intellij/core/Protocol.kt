package dev.lich.intellij.core

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

/**
 * One live lich session, as `lich sessions --json` prints it. Only the fields
 * this plugin reads are kept; lich appends fields to this shape and never
 * removes one (docs/stability.md in the lich repo), so an unknown one is ignored.
 */
data class Peer(
    val id: String,
    val label: String,
    val project: String,
    val kind: String,
    val state: String,
    /** The directory the session runs in: its worktree's, for a worktree session. */
    val path: String,
    /** The root of the project the session belongs to. */
    val projectPath: String,
)

/** What `lich insert --json` prints: where the text went. */
data class Inserted(val id: String, val label: String, val project: String, val kind: String, val bytes: Int)

object Protocol {
    fun parsePeers(json: String): List<Peer> {
        val root = parse(json)
        if (!root.isJsonArray) throw LichException("lich answered something other than a list of sessions")
        return root.asJsonArray.map { element ->
            val obj = element.asJsonObject
            Peer(
                id = obj.text("id"),
                label = obj.text("label"),
                project = obj.text("project"),
                kind = obj.text("kind"),
                state = obj.text("state"),
                path = obj.text("path"),
                projectPath = obj.text("projectPath"),
            )
        }
    }

    fun parseInserted(json: String): Inserted {
        val root = parse(json)
        if (!root.isJsonObject) throw LichException("lich answered something other than a result")
        val obj = root.asJsonObject
        return Inserted(
            id = obj.text("id"),
            label = obj.text("label"),
            project = obj.text("project"),
            kind = obj.text("kind"),
            bytes = obj.get("bytes")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
        )
    }

    private fun parse(json: String) = try {
        JsonParser.parseString(json.trim())
    } catch (e: JsonParseException) {
        throw LichException("lich answered something this plugin cannot read — is it the same lich the CLI belongs to?", e)
    }

    private fun JsonObject.text(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: ""
}

/** A failure a person can act on; [message] is worded for them, not for a log. */
class LichException(message: String, cause: Throwable? = null) : Exception(message, cause)
