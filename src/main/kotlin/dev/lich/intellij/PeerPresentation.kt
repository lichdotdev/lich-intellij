package dev.lich.intellij

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import dev.lich.intellij.core.Peer
import dev.lich.intellij.core.SessionMatcher
import javax.swing.Icon

/** How a session is drawn in a list: its provider's mark, its name, and where and how it runs. */
internal object PeerPresentation {
    fun render(cell: ColoredListCellRenderer<*>, peer: Peer) {
        val takesText = SessionMatcher.takesText(peer)
        cell.icon = iconOf(peer.kind)
        cell.append(peer.label, if (takesText) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES else SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES)
        cell.append("  ${peer.kind}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        cell.append("  ${peer.project}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        if (peer.path.isNotEmpty() && peer.path != peer.projectPath) {
            cell.append("  ${peer.path.substringAfterLast('/')}", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
        }
        when (peer.state) {
            "busy" -> cell.append("  working", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            "waiting" -> cell.append("  waiting on a permission", SimpleTextAttributes.ERROR_ATTRIBUTES)
        }
    }

    private fun iconOf(kind: String): Icon = when (kind) {
        "shell" -> AllIcons.Nodes.Console
        in BRANDED_KINDS -> IconLoader.getIcon("/icons/providers/$kind.svg", PeerPresentation::class.java)
        else -> IconLoader.getIcon("/icons/lich.svg", PeerPresentation::class.java)
    }

    // The marks lich itself draws, vendored from @lobehub/icons (MIT, github.com/lobehub/lobe-icons)
    // by way of lich's ProviderIcon.tsx. Crush and Kiro have no mark there and wear lich's.
    private val BRANDED_KINDS = setOf("claude", "codex", "antigravity", "opencode", "omp", "cursor")
}
