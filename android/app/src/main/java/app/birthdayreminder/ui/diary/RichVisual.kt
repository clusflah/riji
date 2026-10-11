package app.birthdayreminder.ui.diary

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import app.birthdayreminder.domain.RichText

/**
 * 编辑框的“所见即所得”：把隐藏的格式标记换成显示效果（大小标题、编号、圆点、横线、方框、划线），
 * 并告诉编辑框原文和显示文字之间光标位置怎么对应（RichText.render 算好的对照表）。
 */
class RichVisualTransformation(private val prefixColor: Color, private val doneColor: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val r = RichText.render(text.text)
        val out = AnnotatedString.Builder(r.display)
        for (s in r.spans) {
            val style = when (s.style) {
                RichText.Style.H1 -> SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)
                RichText.Style.H2 -> SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                RichText.Style.PREFIX -> SpanStyle(color = prefixColor, fontWeight = FontWeight.SemiBold)
                RichText.Style.DONE -> SpanStyle(color = doneColor, textDecoration = TextDecoration.LineThrough)
                RichText.Style.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            }
            out.addStyle(style, s.start, s.end)
        }
        val n = text.text.length
        val m = r.display.length
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = r.origToTrans[offset.coerceIn(0, n)]
            override fun transformedToOriginal(offset: Int): Int = r.transToOrig[offset.coerceIn(0, m)]
        }
        return TransformedText(out.toAnnotatedString(), mapping)
    }

    // 颜色一样就当同一个转换，避免每次重组都重新算
    override fun equals(other: Any?) = other is RichVisualTransformation && other.prefixColor == prefixColor && other.doneColor == doneColor
    override fun hashCode() = prefixColor.hashCode() * 31 + doneColor.hashCode()
}
