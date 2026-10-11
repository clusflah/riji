package app.birthdayreminder.domain

import app.birthdayreminder.data.Phrase

/** 常用语的分类、分组、排序，规则和旧版 phraseCategories / phrasesGrouped 一致。 */
object PhraseLogic {
    const val UNCATEGORIZED = "未分类"

    fun categoryOf(p: Phrase): String = p.category.ifBlank { UNCATEGORIZED }

    /** 已经用过的分类名（排序后），编辑页里做成可点选的标签 */
    fun categories(all: List<Phrase>): List<String> = all.map { categoryOf(it) }.distinct().sorted()

    /** 按分类分组：分类名排序，组内按标题排序 */
    fun grouped(all: List<Phrase>): List<Pair<String, List<Phrase>>> =
        all.groupBy { categoryOf(it) }.toSortedMap().map { (c, items) -> c to items.sortedBy { it.title } }

    /** 列表里的一行：分组标题 或 一条常用语 */
    sealed class Row {
        data class Header(val category: String, val count: Int, val collapsed: Boolean) : Row()
        data class Item(val phrase: Phrase) : Row()
    }

    /** 按分组排好，折叠的组只留标题（标题上带条数，折叠时也知道里面有几条） */
    fun rows(all: List<Phrase>, collapsed: Set<String>): List<Row> =
        grouped(all).flatMap { (cat, items) ->
            val c = cat in collapsed
            listOf<Row>(Row.Header(cat, items.size, c)) + (if (c) emptyList() else items.map { Row.Item(it) })
        }

    /** 点分组标题：折叠的展开，展开的折叠 */
    fun toggle(collapsed: Set<String>, category: String): Set<String> =
        if (category in collapsed) collapsed - category else collapsed + category

    /** 已经不存在的分组（被删光或改名了）从折叠记录里清掉，免得越积越多 */
    fun prune(collapsed: Set<String>, all: List<Phrase>): Set<String> = collapsed intersect categories(all).toSet()

    fun validate(category: String, title: String, value: String): String? = when {
        title.isBlank() -> "请填写标题"
        value.isBlank() -> "请填写内容"
        else -> null
    }
}
