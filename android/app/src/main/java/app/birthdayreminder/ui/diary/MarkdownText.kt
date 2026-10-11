package app.birthdayreminder.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.birthdayreminder.domain.BlockMarkdown
import app.birthdayreminder.domain.BulletStyle
import app.birthdayreminder.domain.InlineKind
import app.birthdayreminder.domain.InlineMarkdown
import app.birthdayreminder.domain.LineKind
import app.birthdayreminder.domain.MdBlock

private const val LINK_TAG = "url"

private fun inlineAnnotated(raw: String, codeColor: Color, linkColor: Color): AnnotatedString {
    val (clean, spans) = InlineMarkdown.parse(raw)
    return buildAnnotatedString {
        append(clean)
        spans.forEach { s ->
            when (s.kind) {
                InlineKind.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), s.start, s.end)
                InlineKind.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), s.start, s.end)
                InlineKind.UNDERLINE -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), s.start, s.end)
                InlineKind.CODE -> addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeColor), s.start, s.end)
                InlineKind.LINK -> {
                    addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), s.start, s.end)
                    s.url?.let { addStringAnnotation(LINK_TAG, it, s.start, s.end) }
                }
            }
        }
    }
}

/**
 * 只读渲染一段日记正文：标题、小标题、正文、加粗、斜体、下划线、待办、三种列表、引用、表格、链接。
 * 图片和附件不在这里处理，由调用方按块单独渲染。
 * onToggleTask：传了就能直接点待办的复选框（参数是这一行在原文里的行号）；不传就只显示。
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, onToggleTask: ((Int) -> Unit)? = null) {
    val codeColor = MaterialTheme.colorScheme.surfaceVariant
    val linkColor = MaterialTheme.colorScheme.secondary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val uriHandler = LocalUriHandler.current

    @Composable
    fun Inline(raw: String, mod: Modifier = Modifier, color: Color = Color.Unspecified, strike: Boolean = false) {
        val annotated = inlineAnnotated(raw, codeColor, linkColor)
        val hasLink = annotated.getStringAnnotations(LINK_TAG, 0, annotated.length).isNotEmpty()
        val style = MaterialTheme.typography.bodyLarge.copy(
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
            textDecoration = if (strike) TextDecoration.LineThrough else null
        )
        if (hasLink) {
            ClickableText(annotated, style = style, modifier = mod) { off ->
                annotated.getStringAnnotations(LINK_TAG, off, off).firstOrNull()?.let { uriHandler.openUri(it.item) }
            }
        } else Text(annotated, style = style, modifier = mod)
    }

    Column(modifier = modifier) {
        BlockMarkdown.parse(text).forEach { block ->
            when (block) {
                is MdBlock.Table -> MdTable(block, codeColor, linkColor)
                is MdBlock.Line -> when (val line = block.kind) {
                    is LineKind.Blank -> Text(" ", style = MaterialTheme.typography.bodySmall)
                    is LineKind.Heading -> Text(
                        inlineAnnotated(line.content, codeColor, linkColor),
                        fontSize = when (line.level) { 1 -> 21.sp; 2 -> 18.sp; else -> 16.sp },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                    is LineKind.Task -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = line.checked,
                            onCheckedChange = if (onToggleTask != null) { _ -> onToggleTask(block.lineIndex) } else null,
                            modifier = Modifier.size(36.dp)
                        )
                        // 勾掉的划线、变浅
                        Inline(line.content, color = if (line.checked) muted else Color.Unspecified, strike = line.checked)
                    }
                    is LineKind.Bullet -> Row(modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) {
                        Text(if (line.style == BulletStyle.DOT) "•" else "–", modifier = Modifier.width(18.dp), fontWeight = FontWeight.Bold)
                        Inline(line.content)
                    }
                    is LineKind.Numbered -> Row(modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) {
                        Text("${line.number}.", modifier = Modifier.width(26.dp))
                        Inline(line.content)
                    }
                    is LineKind.Quote -> Row(modifier = Modifier.padding(vertical = 2.dp).height(IntrinsicSize.Min)) {
                        Text(" ", modifier = Modifier.width(3.dp).fillMaxHeight().background(muted.copy(alpha = 0.4f)))
                        Inline(line.content, mod = Modifier.padding(start = 8.dp), color = muted)
                    }
                    is LineKind.Plain -> Inline(line.content, mod = Modifier.padding(bottom = 2.dp))
                    is LineKind.TableRow, is LineKind.TableSeparator -> {}   // 已经合并进 MdBlock.Table
                }
            }
        }
    }
}

/** 表格：带细边框，表头加粗、浅色底，每列等宽 */
@Composable
private fun MdTable(t: MdBlock.Table, codeColor: Color, linkColor: Color) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val headBg = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).border(1.dp, line)) {
        val all = listOfNotNull(t.header) + t.rows
        all.forEachIndexed { ri, row ->
            val isHead = t.header != null && ri == 0
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).then(if (isHead) Modifier.background(headBg) else Modifier)) {
                row.forEach { cell ->
                    Text(
                        inlineAnnotated(cell, codeColor, linkColor),
                        fontWeight = if (isHead) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, line).padding(horizontal = 6.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
