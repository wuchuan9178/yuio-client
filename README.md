# yuio Meteor Addon

基于 [Meteor Client](https://meteorclient.com/) 的第三方扩展，主要做 HUD 美化、灵动岛、QQ 消息同步、网易云播放器、字体替换这些东西。

> 测试阶段，持续更新。无混淆、无验证、纯公益。

## 环境要求

- Minecraft **1.21.11**
- [Fabric Loader](https://fabricmc.net/) 0.18+
- [Meteor Client](https://meteorclient.com/) 1.21.11-86+
- JDK 21（自己编译才需要）

## 安装

1. 从 [Releases](https://github.com/wuchuan9178/yuio-meteor-addon/releases) 下载最新的 `yuio-x.x.x.jar`
2. 丢进 `.minecraft/mods/`
3. 和 Meteor Client 一起启动

> 别下带 `-sources` 的 jar，那是源码包，塞进游戏会报错。

## 功能

### HUD

| 名字 | 说明 |
|---|---|
| **Dynamic Island** | 灵动岛。空闲态显示客户端信息/服务器/延迟；放歌时整块变成网易云播放器（左边圆形专辑封面 + 歌名/歌手 + 实时歌词，换行有淡入上浮动画）；按住 Tab 变成玩家列表（分栏、信号条、自己在列高亮）；还有容器预览、进食进度、模块开关通知 |
| **Image HUD** | 在屏幕上贴一张自定义图片 |
| **Friend HUD** | Meteor 好友列表，圆角毛玻璃 + 灰色分割线；可选"附近/同服/任意"判定，命中时名字变绿 |
| **Radar HUD** | 俯视雷达。十字准星、随你朝向旋转的灰格点阵、自己在正中心，玩家红、好友绿 |
| **Key List HUD** | 所有绑了快捷键的模块，左边模块名右边按键，灰线分隔，开启白色未开启灰色 |

### QQ 消息同步

- 直接读 Windows 通知数据库（`wpndatabase.db`）里的 QQ toast
- **不需要 NapCat / LLOneBot 之类的注入插件，不占端口，不联外网，也不需要管理员**
- 群名/联系人、消息正文、发送者都从通知里解析；`@全体成员` / `[有人@我]` 红色、其它 `@` 蓝色
- 头像直接用通知里给的本地文件路径（QQ 自己的头像缓存）
- 支持忽略列表（群号或群名）和"只显示@我"

### 网易云音乐

- 歌名/歌手从网易云主窗口标题读取（`歌名 - 歌手`），不需要装任何插件
- **专辑封面**：按 `歌名 + 歌手` 搜索 → 取 songId → 取 `album.picUrl` 下载 → 解码成纹理画成圆形封面
- **歌词**：调用网易云歌词接口拿 LRC，按当前进度显示当前行
- **精确同步（可选）**：用 `--remote-debugging-port=9222` 启动网易云后，可以从 CEF DevTools 读到真实播放进度和页面上的当前歌词行，暂停/拖动进度条都不会漂

### 主界面

- 自绘背景（整张图按铺满屏幕缩放 + 视差 + 缓慢漂移）
- 四个白色方块按钮：单人游戏 / 多人游戏 / 设置 / 退出游戏，带悬停和按下反馈

### 其他

- **Custom Fonts** —— 用 HarmonyOS Sans 替换游戏全局字体，6 种字重，参数可在 GUI 里调
- **Auto Copy Chat** —— 鼠标悬停到某条聊天消息后 `Ctrl+C` 复制它
- Mace 系列（`MaceMissAura` / `MaceMissAurav2` / `MaceAura` / `LacrymiraAura` …）、VClip 系列、各种杂项模块：直接在游戏内 Meteor GUI 里看，分类是 `main` / `assist` / `Other`

## 已知限制

- **QQ 消息**：Windows 同一个会话只保留最新一条通知，所以同一群连发多条时灵动岛只会看到最后一条；内容完全相同的重复消息会被判重去重
- **网易云歌词**：不带 `--remote-debugging-port=9222` 启动时拿不到真实播放进度，歌词按自己的时钟推算，暂停/拖动会漂；封面和歌词都依赖网易云接口
- **主界面按钮**：是圆角/纯色方块，没有做磨砂模糊（主界面阶段拿不到世界画面做模糊源）

## 编译

```bash
git clone https://github.com/wuchuan9178/yuio-meteor-addon.git
cd yuio-meteor-addon
./gradlew build
```

产物在 `build/libs/yuio-x.x.x.jar`。

## 致谢

- [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) —— 本扩展运行的框架
- `MaceMissAurav2` —— **skid by puerhx(iaa)**
