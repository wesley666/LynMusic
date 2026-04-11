LynMusic is a cross-platform local music player for Windows, Linux, macOS, Android and iOS , built with Kotlin Multiplatform.

LynMusic是基于 Kotlin Multiplatform 的跨平台本地音乐播放器项目，目标平台包括 Android、iOS 和桌面端（JVM），支持 Windows 、 macOS 和 Linux。

## 编译

### 编译安卓 APP

- macOS/Linux
  ```shell
  #编译debug版本
  ./gradlew :composeApp:assembleDebug
  #编译release版本
  ./gradlew :composeApp:assembleRelease
  ```
- Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  .\gradlew.bat :composeApp:assembleRelease
  ```

### 直接运行Desktop (JVM)应用

- macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

**打包当前系统的独立安装包**（比如在 Mac 上运行就会打出 Mac 的包）：

```
./gradlew :composeApp:packageDistributionForCurrentOS
```

*或者简写为*：`./gradlew :composeApp:package`

*产物路径*：`composeApp/build/compose/binaries/main/`

### 运行IOS应用

要构建并运行 iOS 应用的开发版，可以使用 IDE 工具栏运行控件中的运行配置；或者直接在 Xcode 中打开 [/iosApp](./iosApp)  目录并从那里启动。
