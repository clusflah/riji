# 日迹（原生 Android 应用）
n[![Build Android APK](https://github.com/clusflah/riji/actions/workflows/build-apk.yml/badge.svg)](https://github.com/clusflah/riji/actions/workflows/build-apk.yml)

**版本 1.5.7**（更新记录见 CHANGELOG.md；云盘备份设置见 CLOUD_BACKUP.md）

农历日历 + 节假日/调休 + 生日、提醒事项、闹钟、日记、常用语 + AI 助手 + 桌面小组件。
纯原生：Kotlin + Jetpack Compose + Room，没有网页界面，也不再依赖 Capacitor / Node。

## 打开和运行

用 Android Studio 打开 **`android`** 这个文件夹（不是最外层），等 Gradle 同步完成，接上手机点运行。
命令行打包：

```
cd android
./gradlew assembleDebug        # Windows 用 gradlew.bat
# APK：android/app/build/outputs/apk/debug/app-debug.apk
```

需要 JDK 21。GitHub 上也可以直接用 `.github/workflows/build-apk.yml` 在线打包。

## 两个版本

同一份代码打出两个版本（`android/app/build.gradle` 里的 `productFlavors`）：

| 版本 | 说明 |
|---|---|
| `full` 完整版（默认） | 全部功能 |
| `noAllergy` 无过敏记录版 | 去掉“过敏记录”（加号菜单、全部页标签、日历当天面板），其它完全一样；应用名、图标、包名相同，所以和完整版不能同时装在一台手机上，互相覆盖安装时数据保留 |

在 Android Studio 左边栏 **Build Variants** 里把 `app` 切到 `fullDebug` 或 `noAllergyDebug`，再运行或打包。
命令行：`./gradlew assembleFullDebug` / `./gradlew assembleNoAllergyDebug`，APK 在 `android/app/build/outputs/apk/<版本>/debug/`。

## 功能一览

| 功能 | 说明 |
|---|---|
| 日历 | 月视图，每格显示农历、法定假期“休”/调休补班“班”，生日/提醒/日记小圆点；点某天看当天日程；可选周日或周一开头 |
| 生日 | 公历/农历（含闰月）、可不填出生年份、提前 0/1/3/7 天多选提醒；农历三十遇小月落在二十九，2 月 29 日出生的平年在 2 月 28 日过 |
| 提醒事项 | 日期 + 时间/全天、不重复/每天/每周/每月/每年/自定义周期、准时或提前 10 分钟/1 小时/1 天，重复提醒逐次打勾 |
| 闹钟 | 仅一次/每天/工作日/休息日/自定义周几；工作日按法定假期和调休补班判断；稍后提醒、振动、自定义铃声（选本机音频，可试听） |
| 日记 | 正文由文字块和图片块组成，图片可插在任意位置；基本 Markdown（加粗、斜体、标题、列表、引用）+ 预览；可添加位置（高德地图搜索/点选/手机定位）；✨ 用 Gemini 润色 |
| 常用语 | 标题 + 内容，按分类分组，一键复制 |
| AI 助手 | OpenAI / Claude / Gemini / Grok / DeepSeek，可对话查看、添加生日、提醒、闹钟、日记；Key、对话记录只存本机 |
| 桌面小组件 | 日历、生日、提醒、闹钟、日记；2×2、4×2 和可缩放尺寸；点击跳到对应页面 |
| 设置与备份 | 通知、每日提醒时间、默认提前提醒、节假日提醒、农历显示、主题、高德 Key；导出/恢复 .json 备份 |

## 代码结构（`android/app/src/main/java/app/birthdayreminder/`）

```
domain/     纯逻辑，不依赖 Android：日期匹配（Occurrence）、通知排程（ScheduleBuilder）、
            闹钟触发（AlarmTriggers）、节假日（Holidays）、备份读写（BackupCodec）、
            日记块操作（BlockOps）、Markdown（InlineMarkdown/BlockMarkdown/MarkdownEdit）、
            小组件数据（WidgetSummary）、小组件跳转（OpenTarget）……
lunar/      农历换算（1900–2100）
data/       Room 实体、DAO、数据库（含迁移）、节假日联网缓存、图片存储、备份落地
ai/         五家服务商的请求/响应格式、工具定义与校验、对话记录
notify/     通知（AlarmManager）、设置存取、开机重排
ui/         Compose 界面：calendar / edit / list / alarm / diary / phrase / settings / assistant
*.java      响铃引擎：AlarmStore / AlarmReceiver / AlarmService / AlarmActivity / AlarmSound / BootReceiver
            桌面小组件：LiWidgetProvider（及 2x2、4x2）/ WidgetConfigActivity
*.kt (根)   MainActivityCompose（入口）、AlarmEngine（算出触发时刻交给响铃引擎）、WidgetUpdater（生成小组件数据）
```

纯逻辑部分（`domain/`、`lunar/`、`ai/` 里的协议与工具）不碰 Android 类，可以直接用 Kotlin 编译器单独编译测试。

## 数据

- 本地数据库 `li-native.db`（Room，数据库第 9 版，升级都写了正式迁移，不会清数据）。
- 日记图片存在应用私有目录 `files/diary_images/`，闹钟铃声在 `files/alarm_sounds/`。
- 备份文件格式：`{"app":"li-calendar","version":1,"exportedAt":…,"data":{birthdays,reminders,alarms,diary,phrases,settings}}`。
  兼容以前网页版“历”导出的备份。**铃声文件、日记图片文件本身、AI Key 不在备份里。**

## 高德地图 Key

日记“添加位置”用的是高德 **Web 端（JS API）** Key（去 console.amap.com 申请，个人使用免费额度够用），
新申请的 Key 通常还需要“安全密钥 JSCode”。在 设置 → 高德地图 里填写，只保存在本机。

## 权限

通知（POST_NOTIFICATIONS）、精确闹钟（SCHEDULE_EXACT_ALARM）、开机自启重排（RECEIVE_BOOT_COMPLETED）、
定位（仅日记里点“定位”时用）、联网（节假日更新、AI、地图）、前台服务/全屏通知（闹钟响铃）。

## 修改包名

包名是 `app.birthdayreminder`：改 `android/app/build.gradle` 里的 `namespace` 和 `applicationId`，
再把 `app/src/main/java/app/birthdayreminder` 目录和所有 `package` / `import` 一起改掉。

## 第三方说明

- **用药就医（药物追踪与提醒、生病就医记录）**：用药部分的功能设计参考开源项目 [MedTimer](https://github.com/Futsch1/medTimer)（作者 Futsch1，MIT 许可证）。
  本项目按“日迹”自己的结构重新实现（Room 数据库、Compose 界面、通知与备份），没有直接复制 MedTimer 的源代码。
  MedTimer 的 MIT 许可证允许参考、修改和再发布，只需保留其版权声明：Copyright (c) Futsch1。
- 用药提醒只是辅助工具，不能代替医生的建议，用药请遵医嘱。
- **生理期（经期记录与预测、易孕期、经前期提醒、避孕药打卡）**：功能设计参考开源项目 [Mooneva Cycle](https://github.com/aradar46/Mooneva-Cycle-Private-Period-Tracker)（GPL-3.0）。
  只参考了功能设计，没有复制它的源代码；避孕药部分参照之前对 Mooneva 做过的改进（药板进度、一键打卡、连续天数、最近 7 天、服药历史编辑）。
  预测只按平均周期估算，仅供参考，不能作为避孕或诊断依据。
- **身体数据同步**：通过 Android 官方开放的健康数据接口 Health Connect（androidx.health.connect:connect-client，Apache-2.0）只读心率、睡眠、体重、身高、体脂、血压、血氧。
  手环 / 手表 / 体脂秤的 App 需要先把数据写进 Health Connect。数据只保存在手机里。

