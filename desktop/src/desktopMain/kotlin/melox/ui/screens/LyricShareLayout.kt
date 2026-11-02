package melox.ui.screens

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.text.AttributedString

/** Measure every selected line before allocating the image, so nothing is silently clipped. */
internal object LyricShareLayout {
    const val width = 1080
    const val maxHeight = 8192
    private const val top = 340
    private const val bottom = 140
    private const val lineHeight = 60
    private const val paragraphGap = 26
    private const val textWidth = 924f

    internal data class Row(val layout: TextLayout?, val baseline: Float, val text: String)
    internal data class Result(val height: Int, val rows: List<Row>)

    fun measure(lines: List<String>, font: Font, context: FontRenderContext): Result {
        require(lines.isNotEmpty()) { "请至少选择一行歌词" }
        val rows = mutableListOf<Row>()
        var y = top
        for (line in lines) {
            for (paragraph in line.split('\n')) {
                if (paragraph.isEmpty()) {
                    rows.add(Row(null, (y + 45).toFloat(), ""))
                    y += lineHeight
                } else {
                    val attributed = AttributedString(paragraph).apply { addAttribute(TextAttribute.FONT, font) }
                    val measurer = LineBreakMeasurer(attributed.iterator, context)
                    while (measurer.position < paragraph.length) {
                        val start = measurer.position
                        val layout = checkNotNull(measurer.nextLayout(textWidth))
                        rows.add(Row(layout, (y + 45).toFloat(), paragraph.substring(start, measurer.position)))
                        y += lineHeight
                        require(y + bottom <= maxHeight) { "歌词图片过长，请减少选择的歌词行" }
                    }
                }
                y += paragraphGap
                require(y + bottom <= maxHeight) { "歌词图片过长，请减少选择的歌词行" }
            }
        }
        return Result(y + bottom, rows)
    }
}
