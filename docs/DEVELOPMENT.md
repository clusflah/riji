# 日迹 开发说明

改日迹代码时看这里。应用功能和使用方法见 [README.md](../README.md)。

纯原生：Kotlin + Jetpack Compose + Room（响铃引擎和小组件是 Java），没有网页界面。

## 两个版本

同一份代码打出两个版本（`android/app/build.gradle` 里的 `productFlavors`），版本差异只通过 `Features.kt` 判断：

| 版本 | 说明 |
|---|---|
| `full` 完整版（默认） | 全部功能 |
| `noAllergy` 无过敏记录版 | 去掉“过敏记录”（加号菜单、日程区域标签、日历当天面板），其它完全一样；应用名、图标、包名相同 |

在 Android Studio 左边栏 **Build Variants** 里把 `app` 切到 `fullDebug` 或 `noAllergyDebug`，再运行或打包。
命令行：`./gradlew assembleFullDebug` / `./gradlew assembleNoAllergyDebug`。

## 代码结构（`android/app/src/main/java/app/birthdayreminder/`）

```
domain/     纯逻辑，不依赖 Android：日期匹配（Occurrence）、通知排程（ScheduleBuilder）、
            闹钟触发（AlarmTriggers）、节假日（Holidays）、备份读写（BackupCodec）、
            日记块操作（BlockOps）、Markdown（InlineMarkdown/BlockMarkdown/MarkdownEdit）、
            小组件数据（WidgetSummary）、小组件跳转（OpenTarget）、学习（StudyLogic）、心情（MoodLogic）……
lunar/      农历换算（1900–2100）
data/       Room 实体、DAO、数据库（含迁移）、节假日联网缓存、图片存储、备份落地
ai/         五家服务商的请求/响应格式、工具定义与校验、对话记录、语音输入与朗读
cloud/      云盘备份：OneDrive / 百度网盘登录授权、WebDAV、定时同步
health/     Health Connect 读取与自动同步
privacy/    隐私空间的解锁状态与验证
notify/     通知（AlarmManager）、设置存取、开机重排
ui/         Compose 界面：calendar / edit / list / alarm / diary / phrase / study / mood / health / med / cycle /
            search / settings / assistant
*.java      响铃引擎：AlarmStore / AlarmReceiver / AlarmService / AlarmActivity / AlarmSound / BootReceiver
            桌面小组件：LiWidgetProvider（及 2x2、4x2）/ WidgetConfigActivity
*.kt (根)   MainActivityCompose（入口）、AlarmEngine（算出触发时刻交给响铃引擎）、WidgetUpdater（生成小组件数据）、
            Features（各版本的功能开关）
```

纯逻辑部分（`domain/`、`lunar/`、`ai/` 里的协议与工具）不碰 Android 类，可以直接用 Kotlin 编译器单独编译测试。

## 数据

- 本地数据库 `li-native.db`（Room，数据库第 11 版，升级都写了正式迁移，不会清数据）。
- 日记图片存在应用私有目录 `files/diary_images/`，闹钟铃声在 `files/alarm_sounds/`。
- 备份是 `.zip`：`backup.json`（全部数据、设置和 Key）+ 图片、附件、自定义铃声文件。
  旧的 `.json` 备份（包括以前网页版“历”导出的）也能恢复。
- 云盘的登录信息用系统密钥库加密保存，不放进备份文件。

## 修改包名

包名是 `app.birthdayreminder`：改 `android/app/build.gradle` 里的 `namespace` 和 `applicationId`，
再把 `app/src/main/java/app/birthdayreminder` 目录和所有 `package` / `import` 一起改掉。
