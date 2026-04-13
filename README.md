LynMusic is a cross-platform local music player for Windows, Linux, macOS, Android and iOS , built with Kotlin Multiplatform.

LynMusic是基于 Kotlin Multiplatform 的跨平台本地音乐播放器项目，目标平台包括 Android、iOS 和桌面端（JVM），支持 Windows 、 macOS 和 Linux。

## 为什么做这个播放器

第一，很多本地播放器搜索歌词太难用了，有些歌还不一定能搜到，比如 Bobby Chen、Mr Li；第二，想学习一下 KMP；第三，因为有了 codex 这样的编程工具，实现难度大大下降；第四，可以自主决策功能和界面。

## 先看 UI
![pc_main_ui](./doc/pc_main_ui.png)

![pc_player_ui](./doc/pc_player_ui.png)

![pc_music_tag_editor](./doc/pc_music_tag_editor.png)

![pc_lrc_share_note](./doc/pc_lrc_share_note.png)

![pc_lrc_share_cover_color](./doc/pc_lrc_share_cover_color.png)

![pc_lrc_search](./doc/pc_lrc_search.png)

![pc_lrc_apply](./doc/pc_lrc_apply.png)


## 介绍

LynMusic 是一款面向个人音乐收藏场景打造的跨平台本地音乐播放器，基于 Kotlin Multiplatform 开发，可运行在 Android、iOS、Windows、macOS 和 Linux。

在功能上，LynMusic 支持本地文件夹导入，也可接入 Samba、WebDAV、Navidrome 等私有音乐来源，帮助用户把分散在硬盘、NAS 和自建音乐服务中的内容汇总到同一套曲库中。应用提供歌曲、专辑、艺人等多维度浏览方式，并支持喜欢、歌单、播放队列等常用管理能力，方便日常收听与整理。当然，为了多端统一数据，推荐使用Navidrome。

除了基础播放控制外，LynMusic 还提供歌词搜索、歌词分享、在线结果回填等增强功能。对于注重资料维护的用户，应用还支持音乐标签编辑，可修改标题、歌手、专辑、歌词和封面等信息，让曲库更加整洁统一。另外，还支持自定义界面主题等。

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

## 公众号
 ![/锋风](./doc/weixin.jpeg) 
