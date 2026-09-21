# AllMusic GUI

Minecraft Fabric 客户端模组：为 [AllMusic](https://github.com/Coloryr/AllMusic) 服务端插件提供游戏内点歌界面。

## 功能

- **歌曲搜索**：按单曲 / 歌手 / 专辑 / 歌单搜索网易云音乐
- **二级菜单**：搜索歌手/专辑/歌单后点击可下钻到歌曲列表，再点单曲直接点歌
- **本地歌单**：右键收藏单曲到本地歌单，可随时播放/移除
- **云歌单同步**：粘贴网易云 MUSIC_U Cookie 登录后自动拉取你的网易云歌单，一键同步
- **左键点歌 / 右键收藏**：左键单曲发送 `/music <ID>`，右键单曲加入本地歌单
- **不集成播放**：界面只负责选曲，播放完全由服务端 AllMusic 插件处理
- **原版风格**：使用 Minecraft 原版 UI 素材和配色，与游戏风格统一

## 使用

1. 安装 [Fabric Loader](https://fabricmc.net/) 和 Fabric API
2. 下载对应版本的 jar 放入 `mods/` 文件夹
3. 进游戏按 **G** 打开点歌界面（可在 选项 → 控制 中改键）
4. 服务端需安装 AllMusic 插件才能实际播放

## 版本

| 文件夹 | MC 版本 | 模组版本 |
|--------|---------|----------|
| `1.21.1/` | 1.21.1 | 3.0.0 |
| `26.1.2/` | 26.1.2 | 4.0.0 |
| `26.3/` | 26.3 | 4.1.0 |

每个版本是独立的 Fabric 工程，互不影响。在对应文件夹下用 `gradlew build` 构建。

## 构建

需要 JDK 21+（26.x 版本需要 JDK 25）。

```bash
cd 26.1.2   # 或 1.21.1 / 26.3
./gradlew build
```

构建产物在 `build/libs/allmusic-gui-<version>.jar`。

## 登录说明

在「我的」页点击「点击登录」，粘贴浏览器登录 music.163.com 后的 Cookie（需包含 `MUSIC_U`）。登录成功后自动拉取你的网易云歌单。

Cookie 仅保存在本地 `config/allmusic-gui/playlists.json`。

## 许可证

CC0-1.0
