# AGENTS.md

给在这个仓库里干活的 AI 编程助手看的说明。应用功能和使用见 [README.md](README.md)，两个版本、代码结构见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)，这里只写干活时要守的规矩。

## 项目概况

- 日迹：纯原生 Android 应用，Kotlin + Jetpack Compose + Room（少量响铃引擎和小组件是 Java）。
- Gradle 工程在 **`android/`** 下，不是仓库根目录；包名 `app.birthdayreminder`。
- JDK 21，minSdk 24，compile/targetSdk 36。
- 两个版本（productFlavors）：`full` 完整版、`noAllergy` 无过敏记录版，包名相同。版本差异只通过 `Features.kt`（读 `BuildConfig`）判断，新加的差异也走这里。

## 编译和检查

```
cd android
./gradlew assembleFullDebug          # Windows：gradlew.bat assembleFullDebug
./gradlew assembleDebug assembleRelease   # 和 CI 一样，四个 APK 都打
```

- 仓库里没有单元测试，改完至少编译通过（两个 flavor 都要能编）。
- `domain/`、`lunar/`、`ai/` 里的协议与工具是纯逻辑，不要在里面引入 Android 类。

## 写代码的约定

- 注释、提交信息、CHANGELOG、界面文字都用中文，口语化，说清“为什么”；跟着周围代码的写法和注释密度走。
- Compose 里的 `Text` 默认行高是 24sp：在固定高度的容器里放小字号文字时要显式给 `lineHeight`，否则会把布局撑开（月历格子的小圆点就是这样被挤没的）。
- 数据库（`data/AppDatabase.kt`）改表结构：版本号加一，写正式的 `Migration` 并加进 `addMigrations`，**不能**用破坏性迁移清用户数据。
- 新增要保存的数据或设置时，同时在备份（`domain/BackupCodec.kt` 及相关）里读写，旧备份要能继续恢复。
- 私密内容不能出现在桌面小组件和通知里。
- `versionCode` 一直保持 8 不变（方便回退安装），**不要改**。

## 版本和发布

版本号规则（见 CHANGELOG.md 开头）：修复和小改动加最后一位，新功能加中间一位，大改版加第一位。

发布一个新版本：

1. `android/app/build.gradle` 的 `versionName` 改成新版本号。
2. `CHANGELOG.md` 最上面加 `## x.y.z（YYYY-MM-DD）` 一节（发布说明就从这里取）。
3. `README.md` 开头的 **版本 x.y.z** 也改掉。
4. 提交并推送（或合并）到 `main` 就行，不用自己打标签。

`.github/workflows/release.yml` 在推送到 `main` 时看 `versionName`：还没有对应的 `v` 标签就自动打标签，用正式签名打包、核对证书指纹，发布到 GitHub Releases；标签已经有了（版本号没改）就不发。所以**改了 `versionName` 的提交进了 `main` 就会发版**，只有用户要求发布时才改版本号。

- 手动推 `v` 开头的标签也会发布（标签必须和 `versionName` 一致）。
- 在 Actions 页面手动运行 Release：不填标签就发布当前 `versionName`；标签不存在会在 `main` 最新提交上创建。上次发布失败时用它重新发。

## 签名（不要动）

- 正式版签名配置在 `android/app/build.gradle` 的 `signingConfigs.release`：本机读 `android/keystore.properties`，CI 读仓库 Secrets（`RELEASE_KEYSTORE_BASE64` 等）。
- `keystore.properties`、`*.jks` / `*.keystore` **绝不能提交**，也不要打印其中的密码。
- 正式签名证书 SHA-256：`d94b4d8479505f8d30b96013cb85beed4c5c8028aa92e008265ca17535071bc5`（公开信息，release.yml 里用它校验）。

## 提交

- 直接在 `main` 上提交推送是这个仓库的常规做法；提交信息一行中文概括改了什么，可加“模块：”前缀，例如 `月历：修复……`。
- 不要提交 `android/app/build/`、`local.properties` 等生成文件。
