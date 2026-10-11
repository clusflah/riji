package app.birthdayreminder

/** 各版本的功能开关（值来自 app/build.gradle 里的 productFlavors） */
object Features {
    /** 过敏记录：完整版有，无过敏记录版没有 */
    val allergy: Boolean get() = BuildConfig.SHOW_ALLERGY
}
