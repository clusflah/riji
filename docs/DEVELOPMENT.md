# 日迹 开发说明

自己编译、签名、发布日迹时看这里。应用功能和使用方法见 [README.md](../README.md)。

纯原生：Kotlin + Jetpack Compose + Room（响铃引擎和小组件是 Java），没有网页界面。

## 打开和运行

用 Android Studio 打开 **`android`** 这个文件夹（不是最外层），等 Gradle 同步完成，接上手机点运行。需要 JDK 21。
命令行打包：

```
cd android
./gradlew assembleDebug        # Windows 用 gradlew.bat
# APK：android/app/build/outputs/apk/<版本>/debug/
```

GitHub 上也可以直接用 `.github/workflows/build-apk.yml` 在线打包（推送到 main 时自动运行，也可以手动触发）。
在 [Actions](https://github.com/clusflah/riji/actions/workflows/build-apk.yml) 里打开最近一次成功的构建（需要登录 GitHub，90 天后过期），页面底部 Artifacts 里有两个压缩包，各含完整版和无过敏记录版两个 APK：

- `riji-apk-release`：正式版（不可调试，运行更流畅）
- `riji-apk-debug`：调试版

## 两个版本

同一份代码打出两个版本（`android/app/build.gradle` 里的 `productFlavors`），版本差异只通过 `Features.kt` 判断：

| 版本 | 说明 |
|---|---|
| `full` 完整版（默认） | 全部功能 |
| `noAllergy` 无过敏记录版 | 去掉“过敏记录”（加号菜单、日程区域标签、日历当天面板），其它完全一样；应用名、图标、包名相同 |

在 Android Studio 左边栏 **Build Variants** 里把 `app` 切到 `fullDebug` 或 `noAllergyDebug`，再运行或打包。
命令行：`./gradlew assembleFullDebug` / `./gradlew assembleNoAllergyDebug`。

## 签名

正式版用日迹自己的签名（`android/app/build.gradle` 的 `signingConfigs.release`）：

- **本机**：从 `android/keystore.properties` 读（`storeFile`、`storePassword`、`keyAlias`、`keyPassword`），这个文件和签名文件都不提交到 git。
- **GitHub Actions**：从仓库 Secrets 读：`RELEASE_KEYSTORE_BASE64`（签名文件的 base64）、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`。
- 都没有时退回调试签名，别人 fork 之后也能正常打包。

> **签名文件一定要备份**（连同密码）。丢了就再也发不出能覆盖升级的新版本，只能让用户卸载重装。

调试版签名：可选再加一个 `DEBUG_KEYSTORE_BASE64`（电脑上 `~/.android/debug.keystore` 的 base64），
自动构建的调试版就和自己电脑上打的调试版签名一致；不设置则每次构建用临时签名。

## 发布新版本

改 `android/app/build.gradle` 里的 `versionName`，在 CHANGELOG.md 写好这一版的记录，README 开头的版本号也改掉，提交推送后打标签：

```
git tag -a v1.5.8 -m "日迹 1.5.8"
git push origin v1.5.8
```

`.github/workflows/release.yml` 会检查标签和 `versionName` 是否一致，用上面的正式版签名打出两个 APK（缺签名 Secret 时直接失败），
把 CHANGELOG 里这一版的内容作为说明，连同 SHA256SUMS.txt 发布到 Releases。

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
