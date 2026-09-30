# yuio Meteor Addon

基于 [Meteor Client](https://meteorclient.com/) 的第三方扩展，主要做 HUD 美化、灵动岛、通知卡片、QQ 消息同步、网易云播放器、动态模糊、字体替换这些东西。

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

## HUD

| 名字 | 说明 |
|---|---|
| **Dynamic Island** | 灵动岛。空闲态显示客户端信息/服务器/延迟；放歌时整块变成网易云播放器（左边圆形专辑封面 + 歌名/歌手 + 实时歌词，换行有淡入上浮动画）；按住 Tab 显示玩家列表；还有容器预览、进食进度、模块开关通知。**胶囊大小（`scale`）和字体大小（`font-scale`）分开调**；Tab 列表有 `tab-mode`：`replace`（接管空闲态，默认）/ `below`（保留空闲态胶囊，列表堆在它下面） |
| **Notify HUD** | 通知卡片。圆角、半透明图片背景（**两套样式可切换**）、左上角图标、右上时间、底部进度条跑完自动隐藏、出现/消失都有动画、**多卡片纵向堆叠**；同一个模块反复开关**复用同一张卡**不重复堆叠；状态文字分颜色（`on-color` 绿 / `off-color` 红 / `info-color`）。也开放了接口给别的功能推通知 |
| **Image HUD** | 贴纸 HUD。**8 种内置样式**（yuio logo / 岛图标 / 应用图标 / 单人 / 多人 / 设置 / 退出 / 主界面背景）+ 自定义路径，尺寸任意不裁不重复 |
| **Friend HUD** | Meteor 好友列表，圆角 + 灰色分割线；可选"附近/同服/任意"判定，命中时名字变绿 |
| **Radar HUD** | 俯视雷达。十字准星、随朝向旋转的灰格点阵、自己在正中心，玩家红、好友绿 |
| **Key List HUD** | 所有绑了快捷键的模块，左边模块名右边按键，开启白、未开启灰 |

## 素材可以自己换

所有素材都可以用你自己的文件替换，**不用重新打包 jar**。放到游戏目录下：

```
.minecraft/meteor/yuio/
├── notify/
│   ├── background_a.png    通知卡片背景（style a）
│   ├── background_b.png    通知卡片背景（style b）
│   └── icon.png            通知卡片左上角图标
├── hud/yuio_client.png     贴纸 HUD（yuio logo 样式）
├── dynamic_island/icon.png 灵动岛图标
├── gui/singleplayer.png    贴纸 HUD 的内置样式 ……
└── title_background.png    主界面背景
```

规则：**外部文件优先**；文件不存在、或者存在但读不出/不是有效图片，**自动回退到 jar 内置素材**。所以改坏了也不会黑屏，删掉就恢复原版。

## QQ 消息同步

- 直接读 Windows 通知数据库（`wpndatabase.db`）里的 QQ 通知
- **不需要 NapCat / LLOneBot 之类的注入插件，不占端口、不联外网、不需要管理员**
- 群名/联系人、消息正文、发送者都从通知里解析；`@全体成员` / `[有人@我]` 红色、其它 `@` 蓝色
- 头像直接用通知里给的本地文件路径（QQ 自己的头像缓存）
- 支持忽略列表（群号或群名）和"只显示@我"

## 网易云音乐

- 歌名/歌手从网易云主窗口标题读取（`歌名 - 歌手`），不需要装任何插件
- **专辑封面**：按 `歌名 + 歌手` 搜索取 songId → `album.picUrl` 下载 → 解码成纹理画成圆形封面
- **歌词**：调用网易云歌词接口取 LRC，按播放进度显示当前行
- **精确同步（可选）**：用 `--remote-debugging-port=9222` 启动网易云后，通过 CEF DevTools 读取页面上的真实播放进度和当前歌词行，暂停/拖动进度条都不会漂
- 灵动岛的 `music-cdp-port` 就是那个调试端口（默认 9222，填 0 关闭）

## 主界面

- 自绘背景（整张图按铺满屏幕缩放 + 视差 + 缓慢漂移，按贴图真实尺寸采样，不裁不重复）
- 四个白色方块按钮：单人游戏 / 多人游戏 / 设置 / 退出游戏，带悬停和按下反馈
- 窗口标题和图标也是自定义的

## 主要模块

- **Mace 系列**：`MaceMissAura`（含 `miss-clip`、`breachVClip` 等可加减的 VClip 高度列表）、`MaceMissAurav2`（**skid by puerhx(iaa)**）、`MaceAura`、`LacrymiraAura`
- **VClip 系列**：`SuperVClip`、`HorizontalVClip`
- **`motion-blur` 动态模糊**：**全局**生效，`strength` 调模糊强弱、`max-opacity` 调混合比例、`sensitivity` 调跟手程度，带 **`only-when-moving`**（站着不动且不转视角时完全不显示），`tint` 可染色
- **`custom-fonts`**：用 HarmonyOS Sans 替换全局字体，6 种字重可调；可正常开关（关闭后重载一次恢复原版字体），不会反复重载资源
- **`AutoCopyChat`**：鼠标悬停到某条聊天消息后 `Ctrl+C` 复制它
- 其余杂项模块直接在游戏内 Meteor GUI 里看，分类是 `main` / `assist` / `Other`

## 已知限制

- **QQ 消息**：Windows 同一个会话只保留最新一条通知，所以同一群连发多条时灵动岛只会看到最后一条；内容完全相同的重复消息会去重
- **网易云歌词**：不带 `--remote-debugging-port=9222` 启动时拿不到真实播放进度，歌词按自己的时钟推算，暂停/拖动会漂
- **主界面按钮**：是纯色圆角方块，没做磨砂模糊（主界面阶段拿不到世界画面做模糊源）
- **动态模糊**：是"全屏混合一张模糊帧"的实现，跟手程度由位移/转向驱动，不是逐像素速度矢量模糊

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

- 此文字来自DeepSeek-V41-Flash的产物 嘻嘻
