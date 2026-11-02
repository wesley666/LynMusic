[![Total Downloads](https://img.shields.io/github/downloads/wesley666/LynMusic/total?label=Downloads&color=blue)](https://github.com/wesley666/LynMusic/releases)

LynMusic is a cross-platform local music player for Android, Android TV, Android Automotive, and JVM desktops (Windows, Linux, and macOS), built with Kotlin Multiplatform.

LynMusic 是基于 Kotlin Multiplatform 的跨平台本地音乐播放器项目，维护的平台包括 Android 手机／平板、Android TV、Android Automotive 和桌面端（JVM），桌面端支持 Windows、macOS 和 Linux。

原生语言 Swift 开发的 LynMusic 现已上架苹果商店，包括 iPhone、iPad、Mac：[LynMusic App - App Store](https://apps.apple.com/cn/app/lynmusic/id6797922509)

参考：[LynMusic 终于来到 Apple 平台了](https://mp.weixin.qq.com/s/qMaVVd0ZJFal5G48F8m-7g)

![LynMusic](./doc/LynMusic.png)
## 为什么做这个播放器

第一，很多本地播放器搜索歌词太难用了，有些歌还不一定能搜到，比如 Bobby Chen、Mr Li；第二，想学习一下 KMP；第三，因为有了 codex 这样的编程工具，实现难度大大下降；第四，可以自主决策功能和界面。

## 先看 UI
### 电脑或者平板
![pc_main_ui](./doc/pc_main_ui.png)
![pc_player_ui](./doc/pc_player_ui.png)
![pc_player_full_ui](./doc/pc_player_full_ui.png)
![pc_recommend_ui](./doc/pc_recommend_ui.png)
![pc_lrc_apply](./doc/pc_lrc_apply.png)
![pc_lrc_search](./doc/pc_lrc_search.png)
![pc_lrc_share_cover_color](./doc/pc_lrc_share_cover_color.png)
![pc_lrc_share_note](./doc/pc_lrc_share_note.png)
![pc_music_tag_editor](./doc/pc_music_tag_editor.png)
![lyrics-note_share](./doc/lyrics-note_share.png)
![lyrics-share_cover2](./doc/lyrics-share_cover2.png)
![lyrics-share_note2](./doc/lyrics-share_note2.png)
![lyrics-share](./doc/lyrics-share.png)
![lyrics-share2](./doc/lyrics-share2.png)

### 手机
![phone_main_ui](./doc/phone_main_ui.jpg)
![phone_player_ui](./doc/phone_player_ui.png)
![phone_player_lrc_ui](./doc/phone_player_lrc_ui.png)
![phone_player_lrc_share_ui](./doc/phone_player_lrc_share_ui.png)
![phone_player_pop_ui](./doc/phone_player_pop_ui.png)
![phone_player_recommend_ui](./doc/phone_player_recommend_ui.png)
![phone_player_setting_ui2](./doc/phone_player_setting_ui2.png)
![phone_setting_ui](./doc/phone_setting_ui.png)

### TV
![tv_main_ui](./doc/tv_main_ui.png)
![tv_main_ui2](./doc/tv_main_ui2.png)
![tv_main_like_ui](./doc/tv_main_like_ui.png)
![tv_cast_paler_ui](./doc/tv_cast_paler_ui.png)
![tv_player_ui](./doc/tv_player_ui.png)

### 车机
![car_main_ui](./doc/car_main_ui.png)
![car_my_ui](./doc/car_my_ui.png)
![car_player_ui](./doc/car_player_ui.png)


## 介绍

LynMusic 是一款面向个人音乐收藏场景打造的跨平台本地音乐播放器，基于 Kotlin Multiplatform 开发，可运行在 Android 手机／平板、Android TV、Android Automotive，以及 Windows、macOS 和 Linux 的 JVM 桌面环境。

在功能上，LynMusic 支持本地文件夹导入，也可接入 Samba、WebDAV、Navidrome 等私有音乐来源，帮助用户把分散在硬盘、NAS 和自建音乐服务中的内容汇总到同一套曲库中。应用提供歌曲、专辑、艺人等多维度浏览方式，并支持喜欢、歌单、播放队列等常用管理能力，方便日常收听与整理。当然，为了多端统一数据，推荐使用Navidrome。

除了基础播放控制外，LynMusic 还提供歌词搜索、歌词分享、在线结果回填等增强功能。对于注重资料维护的用户，应用还支持音乐标签编辑，可修改标题、歌手、专辑、歌词和封面等信息，让曲库更加整洁统一。另外，还支持自定义界面主题等。

## 模块职责

- `shared:*`、`player:core`：共享数据、曲库、播放、下载、收藏和歌词业务。
- `app:runtime`：组装 `LynMusicAppComponent`，管理后台歌词同步、启动恢复门控和资源释放；不依赖页面模块。
- `ui:common`：主题、颜色选择器、徽标、封面／唱片绘制、字体加载、逐字歌词绘制、玻璃背景、启动错误展示和本地化辅助；不依赖应用组件或业务 Store。共用图片和文本资源位于 `shared:resources`。
- `ui:mobile`：Android 手机／平板的独立入口、主导航、迷你／完整播放器和队列，以及曲库、来源、收藏、歌单、“我的”、设置、导入、标签编辑、歌词搜索和分享页面；平板宽屏布局由本模块维护。JVM 目标仅用于页面和绘制回归测试。
- `ui:desktop`：Windows、Linux、macOS JVM 共用的独立入口、侧栏导航、窗口边距、迷你／完整播放器和队列，以及曲库、来源、收藏、歌单、“我的”、设置、导入、标签编辑、歌词搜索和分享页面；仅包含 JVM 目标。
- `tvApp`：独立的 TV 页面、导航和焦点管理，直接使用运行时和基础组件，不依赖 `player:app`。
- `ui:automotive`：车机独立入口、横竖屏导航、曲库／来源、收藏／歌单、设置、播放器、队列和歌词页面；只依赖共享业务、运行时与基础组件。JVM 目标用于运行页面和绘制回归测试。
- `androidApp`：手机／平板的 Android 启动、系统集成和 APK 打包入口。
- `composeApp`：保留 Android/JVM 共享适配与 PC 启动、系统集成和打包入口。
- `automotiveApp`：车机启动、系统集成和打包入口。

运行时和基础组件迁移后保留原 Kotlin 包名与调用接口，平台入口继续持有应用组件，页面使用已有实例。曲库目录树与歌单添加目标计算位于 `shared:core`，在线来源选项和收藏绑定规则位于 `shared:features`；手机、桌面与车机复用同一份业务计算。

车机页面位于 `top.iwesley.lyn.music.automotive.ui`，由 `automotiveApp` 加载 `AutomotiveApp`。横竖屏布局根据可用尺寸决定，修改车机页面无需改动手机或桌面页面。

`androidApp` 加载 `top.iwesley.lyn.music.mobile.ui.MobileApp`，`composeApp` 的 JVM source set 加载 `top.iwesley.lyn.music.desktop.ui.DesktopApp`。两端页面模块互不依赖，通过 `ui:common` 复用基础组件。播放器展开、队列、歌词设置窗口等展示状态由各端 `PlayerPresentation` 管理，`PlayerState`／`PlayerIntent` 不再包含这些显示字段和操作。各端拥有自己的设置导航、标签编辑布局、来源表单和歌词弹窗；搜索、分享、导入等异步业务仍由共享 Store 执行。

各端通过 `BeginManualLyricsSearch`／`EndManualLyricsSearch`、`BeginLyricsShare`／`EndLyricsShare`、`BeginCastDiscovery`／`EndCastDiscovery` 管理共享异步会话。会话标识负责隔离旧结果；结束会话会取消对应工作，切歌会清理歌词搜索和分享。关闭投屏设备列表只停止发现，不停止已建立的投屏。UI 销毁时只结束自己持有的会话。

`PlayerStore.restorePlayback()` 返回恢复结果与快照，保留仓库的幂等恢复和过期恢复令牌校验。`StartupAutoOpenGate.restore()` 在同一宿主生命周期内只交付一次自动展开请求；各端消费结果决定是否展开，不会因页面重入重复自动打开。

旧 `player:app` 已删除。`./gradlew verifyUiModuleBoundaries` 检查直接及间接依赖，禁止页面模块互相依赖，禁止运行时／基础层反向依赖 UI；该检查自动挂到 Kotlin 编译、Android `preBuild` 和 `check`。

## 编译

使用 Gradle Wrapper 9.6.0、AGP 9.4.1、Kotlin／Compose Compiler 2.4.21、KSP 2.3.12 和 JDK 21。
Android 使用新 DSL、内置 Kotlin及 Android-KMP 库插件；手机／平板应用入口为 `androidApp`。
SDK 配置仍为 compileSdk 37、targetSdk 36、minSdk 23。

完整单元测试运行 `./gradlew fullTest`（Windows 使用 `.\gradlew.bat fullTest`）。
该任务覆盖 JVM、Android-KMP host 和普通 Android Debug／Release 测试，并检查用例数量与跳过情况；
结果位于 `build/reports/fullTest/`。迁移说明见 [Gradle 9 构建说明](docs/gradle9-migration.md)。

### 编译安卓 APP

- macOS/Linux
  ```shell
  #编译debug版本
  ./gradlew :androidApp:assembleDebug
  #编译release版本
  ./gradlew :androidApp:assembleRelease
  ```
- Windows
  ```shell
  .\gradlew.bat :androidApp:assembleDebug
  .\gradlew.bat :androidApp:assembleRelease
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

## 许可证

LynMusic 以 GNU General Public License version 3 or later（GPL-3.0-or-later）分发，完整许可证文本见 [LICENSE](./LICENSE)。

项目包含第三方 DLNA/UPnP 组件 Platinum UPnP SDK，该组件使用 GPL-2.0-or-later / commercial 双许可。由于完整应用包含该 GPL 组件，发布 LynMusic 的完整源码或二进制产物时需要遵守 GPL-3.0-or-later 的条款。第三方组件许可证说明见 [THIRD_PARTY_LICENSES.md](./THIRD_PARTY_LICENSES.md)。

## 公众号
 ![/锋风](./doc/weixin.jpeg) 

## Star History
[![Star History Chart](https://star-history.dera.page/svg?repos=wesley666/LynMusic&type=Date)](https://star-history.dera.page/#wesley666/LynMusic&Date)
