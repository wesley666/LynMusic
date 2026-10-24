# Gradle 9 构建与测试

项目使用 Gradle 9.6.0、AGP 9.4.1、Kotlin／Compose Compiler 2.4.21、KSP 2.3.12 和 JDK 21。
Compose Multiplatform 1.11.1、Hot Reload 1.1.1、Room 2.8.4、SQLite 2.6.2、Robolectric 4.16.1 保持原版本。
Android 的 compileSdk／targetSdk／minSdk 为 37／36／23，NDK 为 28.2.13676358。
本地使用 Gradle 默认 worker 上限，通常按 CPU 处理器数量确定；CI 保留独立的双 worker 配置。

本次迁移核对时，Kotlin [官方完整支持矩阵](https://kotlinlang.org/docs/gradle-configure-project.html)最高列到 AGP 9.3.1；
本项目保留 AGP 9.4.1，需以全量测试、各端构建和设备验收验证该组合，不能仅以同步成功验收。

## 模块入口

- 手机／平板：`androidApp`，使用内置 Kotlin和 Android Application 插件。
- PC：`composeApp` 的 JVM 目标；保留 Windows、Linux、macOS 打包及 macOS 系统媒体控制桥接。
- TV／车机：`tvApp`／`automotiveApp`。
- Android/JVM 共享库：使用 `com.android.kotlin.multiplatform.library` 和 `kotlin.android {}`。
- `composeApp` 的 Android 部分是库，保留 `Platform.android.kt`；Activity、Application 和应用资源由 `androidApp` 持有。

三个应用保留原 applicationId、组件类名、版本、签名规则、ABI 分包和 APK 名称。
TV 与车机继续复用手机入口的 Android 图标、备份和歌词分享资源。

## 构建

```shell
./gradlew :androidApp:assembleDebug :androidApp:assembleRelease
./gradlew :tvApp:assembleDebug :tvApp:assembleRelease
./gradlew :automotiveApp:assembleDebug :automotiveApp:assembleRelease
./gradlew :composeApp:run
./gradlew :composeApp:packageDistributionForCurrentOS
```

Windows 使用 `gradlew.bat`。Android APK 位于各应用模块的 `build/outputs/apk/`；
PC 包仍位于 `composeApp/build/compose/binaries/`。
手机入口的旧 `composeApp:assembleDebug`／`assembleRelease` 命令已移除。

## 全量单元测试

```shell
./gradlew clean fullTest --no-build-cache --continue --warning-mode all
```

`fullTest` 同时连接所有 JVM、Android-KMP host 和普通 Android Debug／Release 测试，需主动执行；根 `check` 不再依赖此任务。
当前基线为 JVM 2695、KMP host 807、普通 Android Debug／Release 238，共 3740 次执行。
Android-KMP 库没有 Debug／Release 变体，原两套相同用例合并为一套 host 测试，因此不再按旧的 4547 次执行验收。

任务检查有用例模块的最少执行数、失败和跳过情况，避免漏接源集后以零测试通过。
有意没有测试源码的任务可以是 `NO-SOURCE`。车机 UI 的本地化断言辅助函数仅属于 `jvmTest`。
覆盖汇总及逐项用例清单位于 `build/reports/fullTest/summary.tsv` 和 `cases.tsv`。
新增测试无需降低现有基线；移动测试到其他模块时应同步审查任务映射和覆盖清单。

共享资源夹具只接入 `commonTest`、`jvmTest` 和 `androidHostTest`，
host 测试显式启用 Android 资源，使用 Robolectric SDK 35 并初始化 Compose 资源读取器。
普通 Android 模块通过 `android.onlyEnableUnitTestForTheTestedBuildType=false` 保留两种构建类型的单测。

## 验收

- KSP 必须实际生成 Android/JVM Room 实现；数据库版本、schema 身份哈希和 DDL 保持基线。
- Lint 随应用 assemble 执行；同时验证配置缓存创建、复用及增量构建。
- 每个 Android 应用、每种构建类型保留 ARM64、ARMv7、通用 APK，核对 Manifest、资源和原生库。
- 签名未配置时保留未签名产物，完整配置时签名，缺项时报错。
- 检查生产产物没有测试夹具／Robolectric，Android 类路径没有桌面页面和 VLCJ。
- 在对应平台验证安装、旧数据升级、播放、后台控制、导航、三种语言及歌词分享。
- Windows、Linux、macOS 的安装包须由对应操作系统构建；设备和操作系统验证结果单独记录。

测试工作流仅支持手动触发，运行全量测试和各桌面平台 JVM 测试并上传报告。
PR、普通推送和标签发布均不会自动触发测试；标签发布保留原有构建、Lint 和打包流程。
本地 `fullTest`／`check` 入口保留，按需主动执行。
