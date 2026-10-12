# 日迹（原生 Android 应用）

[![Build Android APK](https://github.com/clusflah/riji/actions/workflows/build-apk.yml/badge.svg)](https://github.com/clusflah/riji/actions/workflows/build-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**版本 1.7.1**（更新记录见 [CHANGELOG.md](CHANGELOG.md)；云盘备份设置见 [CLOUD_BACKUP.md](CLOUD_BACKUP.md)）

农历日历 + 节假日/调休 + 生日、提醒事项、闹钟、日记、常用语 + 健身、过敏、用药就医、生理期 + 隐私空间 + AI 助手 + 桌面小组件 + 云盘备份。
纯原生：Kotlin + Jetpack Compose + Room，没有网页界面，也不再依赖 Capacitor / Node。

## 截图

| 日历首页 · 全部 | 日历首页 · 提醒 | 往上滑收成一周 · 常用语 |
|---|---|---|
| <img src="docs/screenshots/calendar-all.jpg" width="240" alt="日历首页：月视图、农历、节假日和调休，下方“全部”时间轴"> | <img src="docs/screenshots/calendar-reminders.jpg" width="240" alt="日历首页：提醒标签里的待办，可打勾完成"> | <img src="docs/screenshots/calendar-phrases.jpg" width="240" alt="月历收成一周，常用语按分类分组、一键复制"> |

| AI 助手 | 设置 | 设置（续） |
|---|---|---|
| <img src="docs/screenshots/ai-assistant.jpg" width="240" alt="AI 助手：读取本周数据做总结，支持语音输入和朗读"> | <img src="docs/screenshots/settings-1.jpg" width="240" alt="设置：农历、节日节气、每周开始、外观、通知、提醒检查、功能开关"> | <img src="docs/screenshots/settings-2.jpg" width="240" alt="设置：功能开关、数据备份、云盘备份、节假日数据、高德地图 Key"> |

## 下载

**正式版**：到 [Releases](https://github.com/clusflah/riji/releases/latest) 下载 `riji-<版本>-full.apk`（完整版）或 `riji-<版本>-noAllergy.apk`（无过敏记录版），两个只能装一个；不需要登录。

**开发中的版本**：在 [Actions](https://github.com/clusflah/riji/actions/workflows/build-apk.yml) 里打开最近一次成功的构建（需要登录 GitHub，90 天后过期），页面底部 Artifacts 里有两个压缩包，各含完整版和无过敏记录版两个 APK：

- `birthday-reminder-apk-release`：正式版，日常使用装这个（不可调试，运行更流畅）
- `birthday-reminder-apk`：调试版

> 从以前装的调试版（或旧签名的版本）换成正式版时，签名不同，不能直接覆盖安装：
> 先在 设置 → 数据 里导出备份（或做一次云盘备份），卸载旧版，装上正式版后再恢复备份。以后正式版之间可以直接覆盖升级。

## 签名

正式版用日迹自己的签名（`android/app/build.gradle` 的 `signingConfigs.release`）：

- **本机**：从 `android/keystore.properties` 读（`storeFile`、`storePassword`、`keyAlias`、`keyPassword`），这个文件和签名文件都不提交到 git。
- **GitHub Actions**：从仓库 Secrets 读：`RELEASE_KEYSTORE_BASE64`（签名文件的 base64）、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`。
- 都没有时退回调试签名，别人 fork 之后也能正常打包。

> **签名文件一定要备份**（连同密码）。丢了就再也发不出能覆盖升级的新版本，只能让用户卸载重装。

调试版签名：可选再加一个 `DEBUG_KEYSTORE_BASE64`（电脑上 `~/.android/debug.keystore` 的 base64），
自动构建的调试版就和自己电脑上打的调试版签名一致；不设置则每次构建用临时签名。

## 发布新版本

改 `android/app/build.gradle` 里的 `versionName`，在 CHANGELOG.md 写好这一版的记录，提交推送后打标签：

```
git tag -a v1.5.8 -m "日迹 1.5.8"
git push origin v1.5.8
```

`.github/workflows/release.yml` 会检查标签和 `versionName` 是否一致，用上面的正式版签名打出两个 APK（缺签名 Secret 时直接失败），
把 CHANGELOG 里这一版的内容作为说明，连同 SHA256SUMS.txt 发布到 Releases。

## 打开和运行

用 Android Studio 打开 **`android`** 这个文件夹（不是最外层），等 Gradle 同步完成，接上手机点运行。
命令行打包：

```
cd android
./gradlew assembleDebug        # Windows 用 gradlew.bat
# APK：android/app/build/outputs/apk/<版本>/debug/
```

需要 JDK 21。GitHub 上也可以直接用 `.github/workflows/build-apk.yml` 在线打包（推送到 main 时自动运行，也可以手动触发）。

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
| 日历首页 | 月视图，每格显示农历、节日节气、法定假期“休”/调休补班“班”和事件小圆点；可选周日或周一开头；往上滑时月历收成只剩一周。下方日程区有“全部”时间轴（全天的事在上，其余按时间排）和各项目标签，可左右滑动切换 |
| 日程视图 | 顶栏切换“月历 / 日程”：按月、按周分组的连续列表，显示生日、提醒、假期、复诊；往前最多看五年，往后两年 |
| 生日 | 公历/农历（含闰月）、可不填出生年份、提前 0/1/3/7 天多选提醒；农历三十遇小月落在二十九，2 月 29 日出生的平年在 2 月 28 日过 |
| 提醒事项 | 日期 + 时间/全天、不重复/每天/每周/每月/每年/自定义周期、准时或提前 10 分钟/1 小时/1 天，重复提醒逐次打勾 |
| 闹钟 | 仅一次/每天/工作日/休息日/自定义周几；工作日按法定假期和调休补班判断；稍后提醒、振动、自定义铃声（选本机音频，可试听）；系统不让响铃服务启动时，改发闹钟级别的通知兜底 |
| 日记 | 所见即所得格式（加粗、斜体、标题、列表、引用）、表格、图片、附件；可添加位置（高德地图搜索/点选/手机定位）；✨ AI 润色 |
| 常用语 | 标题 + 内容，按分类分组，一键复制 |
| 学习 | 一条记录里记好几项内容（名称、数量、单位）+ 学习时长、开始时间；常用项目点一下就加一行；每天定时的学习提醒；今天 / 本周 / 本月统计和连续天数；可以从旧日记里识别“名称 数字”导入 |
| 健身 | 分组记录训练、极限组；身体数据（体重曲线等），可从 Health Connect 自动同步心率、睡眠、体重、身高、体脂、血压、血氧 |
| 过敏记录 | 记录每次过敏的情况（无过敏记录版没有这一项） |
| 用药就医 | 药物与服药提醒、打卡，生病就医和复诊记录；复诊会显示在日历和日程里 |
| 生理期 | 经期记录与预测、易孕期、经前期提醒、避孕药打卡（药板进度、连续天数、最近 7 天、服药历史编辑） |
| 隐私空间 | 设为私密的内容只在隐私空间里显示，进入时要验证指纹或锁屏密码；私密内容不会出现在桌面小组件上 |
| 全局搜索 | 在所有记录里搜索 |
| AI 助手 | OpenAI / Claude / Gemini / Grok / DeepSeek，可对话查看、添加生日、提醒、闹钟、日记；支持语音输入和朗读回复（Azure / Gemini / Google / 系统语音）；Key、对话记录只存本机 |
| 桌面小组件 | 可选日历、生日、提醒、闹钟、日记、健身、过敏、今天的用药、生理期状态、常用语；2×2、4×2，可长按拖动改变大小，内容按尺寸自动调整；点击跳到对应页面 |
| 设置 | 通知、每日提醒时间、默认提前提醒、节假日提醒、农历显示、主题、高德 Key；功能开关（不用的项目可以整个关掉）；“提醒检查”（通知、精确闹钟、全屏通知等是否正常，一键跳到系统设置） |
| 备份 | 导出/恢复 .zip（数据、设置、Key、图片、附件、铃声），旧的 .json 备份也能恢复；定时自动备份到云盘（OneDrive、百度网盘或 WebDAV：坚果云、Nextcloud、群晖、Koofr 等） |

## 代码结构（`android/app/src/main/java/app/birthdayreminder/`）

```
domain/     纯逻辑，不依赖 Android：日期匹配（Occurrence）、通知排程（ScheduleBuilder）、
            闹钟触发（AlarmTriggers）、节假日（Holidays）、备份读写（BackupCodec）、
            日记块操作（BlockOps）、Markdown（InlineMarkdown/BlockMarkdown/MarkdownEdit）、
            小组件数据（WidgetSummary）、小组件跳转（OpenTarget）……
lunar/      农历换算（1900–2100）
data/       Room 实体、DAO、数据库（含迁移）、节假日联网缓存、图片存储、备份落地
ai/         五家服务商的请求/响应格式、工具定义与校验、对话记录、语音输入与朗读
cloud/      云盘备份：OneDrive 登录授权、WebDAV、定时同步
health/     Health Connect 读取与自动同步
privacy/    隐私空间的解锁状态与验证
notify/     通知（AlarmManager）、设置存取、开机重排
ui/         Compose 界面：calendar / edit / list / alarm / diary / phrase / health / med / cycle /
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
- 云盘的登录信息（OneDrive 令牌、WebDAV 密码）用系统密钥库加密保存，不放进备份文件。
- 数据只保存在手机里（以及你自己设置的云盘里），没有开发者的服务器。

## 高德地图 Key

日记“添加位置”用的是高德 **Web 端（JS API）** Key（去 console.amap.com 申请，个人使用免费额度够用），
新申请的 Key 通常还需要“安全密钥 JSCode”。在 设置 → 高德地图 里填写，只保存在本机。

## 权限

通知（POST_NOTIFICATIONS）、精确闹钟（SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM）、开机自启重排（RECEIVE_BOOT_COMPLETED）、
定位（仅日记里点“定位”时用）、麦克风（仅 AI 助手语音输入时用）、联网（节假日更新、AI、地图、云盘备份）、
前台服务 / 全屏通知 / 振动 / 唤醒（闹钟响铃）、忽略电池优化（让闹钟准时）、Health Connect 只读权限（同步身体数据时用）。

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

## 许可证

本项目以 [MIT 许可证](LICENSE) 发布。
