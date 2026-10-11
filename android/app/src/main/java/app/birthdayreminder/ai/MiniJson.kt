package app.birthdayreminder.ai

/**
 * 很小的 JSON 读写工具（不依赖 Android 的 org.json），这样请求体怎么拼、
 * 返回怎么解析，都能在电脑上单独用测试跑，不用每次都上手机试。
 * 类型对应：对象 = Map<String, Any?>，数组 = List<Any?>，数字 = Long 或 Double，还有 String / Boolean / null。
 */
object MiniJson {
    fun write(v: Any?): String = StringBuilder().also { append(it, v) }.toString()

    private fun append(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> quote(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Float, is Double -> {
                val d = (v as Number).toDouble()
                if (d.isNaN() || d.isInfinite()) sb.append("null")
                else if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append(d.toLong()) else sb.append(d)
            }
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, x) in v) {
                    if (!first) sb.append(',')
                    first = false
                    quote(sb, k.toString()); sb.append(':'); append(sb, x)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (x in v) { if (!first) sb.append(','); first = false; append(sb, x) }
                sb.append(']')
            }
            is Array<*> -> append(sb, v.toList())
            else -> quote(sb, v.toString())
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    class ParseError(msg: String) : Exception(msg)

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (p.i != text.length) throw ParseError("多余的内容 @${p.i}")
        return v
    }

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            if (i >= s.length) throw ParseError("意外结束")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw ParseError("不认识的字符 '$c' @$i")
            }
        }
        fun lit(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) throw ParseError("不认识的内容 @$i")
            i += w.length; return v
        }
        fun num(): Any {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && s[i].isDigit()) i++
            var isD = false
            if (i < s.length && s[i] == '.') { isD = true; i++; while (i < s.length && s[i].isDigit()) i++ }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                isD = true; i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i].isDigit()) i++
            }
            val t = s.substring(st, i)
            return if (isD) t.toDouble() else (t.toLongOrNull() ?: t.toDouble())
        }
        fun str(): String {
            i++ // 开头的引号
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw ParseError("字符串没有结束")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) throw ParseError("转义没有结束")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw ParseError("\\u 转义不完整")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                            }
                            else -> throw ParseError("不认识的转义 \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun arr(): List<Any?> {
            i++; ws()
            val out = ArrayList<Any?>()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                ws(); out.add(value()); ws()
                if (i >= s.length) throw ParseError("数组没有结束")
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; return out }
                throw ParseError("数组里出现不该有的字符 @$i")
            }
        }
        fun obj(): Map<String, Any?> {
            i++; ws()
            val out = LinkedHashMap<String, Any?>()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') throw ParseError("对象的键必须是字符串 @$i")
                val k = str(); ws()
                if (i >= s.length || s[i] != ':') throw ParseError("缺少冒号 @$i")
                i++; ws()
                out[k] = value(); ws()
                if (i >= s.length) throw ParseError("对象没有结束")
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; return out }
                throw ParseError("对象里出现不该有的字符 @$i")
            }
        }
    }
}
