package melox.validation

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.json.JSONArray
import org.json.JSONObject
import melox.ui.screens.MeloXLyricTimeMsKey
import melox.ui.screens.MeloXLyricBrowsingKey
import melox.ui.screens.MeloXLyricWordLinesKey
import melox.ui.screens.MeloXLyricDocumentKey

/** Read-only inspection of the pinned Compose 1.8.1 offscreen scene, including modal layers. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun ImageComposeScene.captureSemantics(): List<SemanticsNode> {
    fun Any.field(name: String): Any? = javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this)
    val composeScene = requireNotNull(field("scene"))
    val owners = listOfNotNull(composeScene.field("mainOwner")) +
        (composeScene.field("layers") as List<*>).mapNotNull { it?.field("owner") }
    return buildList {
        fun visit(node: SemanticsNode) {
            if (node.config.contains(SemanticsProperties.InvisibleToUser)) return
            add(node)
            if (!node.config.isClearingSemantics) node.children.forEach(::visit)
        }
        owners.forEach { owner ->
            val semantics = owner.javaClass.getMethod("getSemanticsOwner").invoke(owner) as SemanticsOwner
            visit(semantics.unmergedRootSemanticsNode)
        }
    }
}

internal fun List<SemanticsNode>.tagged(tag: String): SemanticsNode? =
    lastOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag && !it.boundsInWindow.isEmpty }

internal fun List<SemanticsNode>.toEvidence(): JSONArray = JSONArray().apply {
    this@toEvidence.forEach { node ->
        val config = node.config
        val tag = config.getOrNull(SemanticsProperties.TestTag)
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        val description = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        if (tag == null && text == null && description == null) return@forEach
        val bounds = node.boundsInWindow
        put(JSONObject().put("tag", tag ?: JSONObject.NULL).put("text", text ?: JSONObject.NULL)
            .put("description", description ?: JSONObject.NULL)
            .put("state", config.getOrNull(SemanticsProperties.StateDescription) ?: JSONObject.NULL)
            .put("lyric_time_ms", config.getOrNull(MeloXLyricTimeMsKey) ?: JSONObject.NULL)
            .put("lyric_browsing", config.getOrNull(MeloXLyricBrowsingKey) ?: JSONObject.NULL)
            .put("lyric_word_lines", config.getOrNull(MeloXLyricWordLinesKey) ?: JSONObject.NULL)
            .put("lyric_document", config.getOrNull(MeloXLyricDocumentKey) ?: JSONObject.NULL)
            .put("selected", config.getOrNull(SemanticsProperties.Selected) ?: JSONObject.NULL)
            .put("clickable", config.contains(SemanticsActions.OnClick))
            .put("long_clickable", config.contains(SemanticsActions.OnLongClick))
            .put("enabled", !config.contains(SemanticsProperties.Disabled))
            .put("bounds", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom))))
    }
}
