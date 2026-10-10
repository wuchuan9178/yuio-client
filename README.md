# yuio Meteor Addon

基于 [Meteor Client](https://meteorclient.com/) 的第三方扩展。

一半是**美化**：灵动岛、通知卡片、贴纸 HUD、目标 HUD、圆角玻璃、自定义字体、动态模糊、自绘主界面。
一半是**实战**：Mace 系列、Spear 系列、自瞄、VClip、落地水、反拉回、发包限速。

> 测试阶段，持续更新。**无混淆、无验证、纯公益。**

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

---

## HUD

| 名字 | 说明 |
|---|---|
| **Dynamic Island** | 灵动岛。空闲态显示客户端信息 / 服务器 / 延迟 / 坐标；放歌时整块变成网易云播放器（圆形专辑封面 + 歌名 + 歌手 + 实时歌词，**过长的歌名和歌词左右循环滚动**）；按住 Tab 显示玩家列表；还有容器预览、进食进度、QQ 消息、模块开关通知。**胶囊大小（`scale`）和字体大小（`font-scale`）分开调**，中文宽度按字体实测自适应，不会再被截断或者撑破面板；Tab 列表有 `tab-mode`：`replace`（接管空闲态，默认）/ `below`（保留空闲态胶囊，列表堆在它下面） |
| **Notify HUD** | 通知卡片。圆角、半透明图片背景（**两套样式可切换**）、左上角图标、右上时间、底部进度条跑完自动隐藏、出现 / 消失都有过渡动画、**多卡片纵向堆叠**。同一个模块反复开关**复用同一张卡**不重复堆叠；状态文字分颜色（`on-color` 绿 / `off-color` 红 / `info-color`）。**还带图腾 pop 提示**：`%name% pop %count%` 格式自己改，**同一个玩家名只占一张卡，pop 次数往上累加**，不会弹一下刷一张 |
| **Target HUD** | 目标 HUD（新）。圆角卡片，半透明图片背景。左侧是**目标皮肤头像**（NEAREST 采样，像素清晰不糊）；右边上排是名字、下排是**圆角红色血条**，条内实时显示 `当前血量/血量上限`（整数）；血条下面是**物品栏**：副手 —— 黑色竖条 —— 四格护甲（灰色竖条分隔）—— 黑色竖条 —— 主手。支持**同时多个目标纵向堆叠**（一次攻击一个卡片，按最近攻击排序），长时间没打就自动淡出。**死亡动画**：除头像外先消失 → 头像顺时针转 20° → 头像淡出 → 卡片里像打字一样逐字出现 `EZZZZZZZZ` → 整张卡缓缓消失。`scale` / `radius` / `card-gap` / 背景不透明度 / 压暗强度 / 血条和名字颜色全部可调，字体有 `Game`（游戏字体，中文正常）和 `Meteor` 两档 |
| **Image HUD** | 贴纸 HUD。**8 种内置样式**（yuio logo / 岛图标 / 应用图标 / 单人 / 多人 / 设置 / 退出 / 主界面背景）+ 自定义路径，尺寸任意不裁不重复 |
| **Friend HUD** | Meteor 好友列表，圆角 + 灰色分割线；可选"附近 / 同服 / 任意"判定，命中时名字变绿 |
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

规则：**外部文件优先**；文件不存在、或者存在但读不出 / 不是有效图片，**自动回退到 jar 内置素材**。所以改坏了也不会黑屏，删掉就恢复原版。

## QQ 消息同步

- 直接读 Windows 通知数据库（`wpndatabase.db`）里的 QQ 通知
- **不需要 NapCat / LLOneBot 之类的注入插件，不占端口、不联外网、不需要管理员**
- 群名 / 联系人、消息正文、发送者都从通知里解析；`@全体成员` / `[有人@我]` 红色、其它 `@` 蓝色
- 头像直接用通知里给的本地文件路径（QQ 自己的头像缓存）
- 支持忽略列表（群号或群名）和"只显示 @ 我"

## 网易云音乐

- 歌名 / 歌手从网易云主窗口标题读取（`歌名 - 歌手`），不需要装任何插件
- **专辑封面**：按 `歌名 + 歌手` 搜索取 songId → `album.picUrl` 下载 → 解码成纹理画成圆形封面
- **歌词**：调用网易云歌词接口取 LRC，按播放进度显示当前行
- **精确同步（可选）**：用 `--remote-debugging-port=9222` 启动网易云后，通过 CEF DevTools 读取页面上的真实播放进度和当前歌词行，暂停 / 拖动进度条都不会漂
- 灵动岛的 `music-cdp-port` 就是那个调试端口（默认 9222，填 0 关闭）

## 主界面

- 自绘背景（整张图按铺满屏幕缩放 + 视差 + 缓慢漂移，按贴图真实尺寸采样，不裁不重复）
- 四个白色方块按钮：单人游戏 / 多人游戏 / 设置 / 退出游戏，带悬停和按下反馈
- 窗口标题和图标也是自定义的

---

## 模块

分类一共四个：**Attack**（攻击）/ **Assist**（辅助）/ **Other**（其他）/ **Settings**（设置）。

### Attack

| 模块 | 说明 |
|---|---|
| `AimAssist` | 自瞄。**5 种平滑曲线**（指数 / 指数加强 / 线性 / 正弦 / 瞬移），指数模式下 `responsiveness` 调跟手，线性 / 正弦模式用 `speed`。**两种索敌方式**：`auto`（自动找最近的）和 `crosshair`（准星真正指到实体上才锁）；还有 **`lock-target` 锁死目标**，开了之后就认第一个目标不换人。`range` / `fov` / `aim-height` / 穿墙 / 忽略好友 / 忽略创造 都有；`rotation-mode` 可切 **服务端瞄准**（只动服务端，你屏幕上看不出来）和 **客户端瞄准**（连你自己视角一起转） |
| `MaceMissAura` | 锤子。含 `miss-clip`、`breachVClip` 等**可加减的 VClip 高度列表** |
| `MaceMissAurav2` | 锤子 v2。**skid by puerhx(iaa)** |
| `MaceAura` | 自动锤击暴击光环 |
| `SpearAura` | 长矛光环。**VClip 到目标头顶 → 悬停蓄力 → 俯冲穿刺**，循环执行。`move-mode` 可选 `Velocity` / `Teleport` / `Both`；升空 / 定位 / 俯冲 / 攻击 / 反拉回五组参数全开，攻击点可以取**包围盒中心**（打小碰撞箱才打得中）；附带 pull-back 保护，被服务器拉回时自动清空节奏 |
| `SpearArmorBreaker` | 长矛破甲 |
| `LacrymiraAura` | 光环 |
| `AutoElytraMace` | 鞘翅起飞 + 俯冲 + 锤击一条龙 |

### Assist

| 模块 | 说明 |
|---|---|
| `Orbit` | 绕圈走位，可以只在弹出图腾（`only-popped`）时绕 |
| `AntiMiss` | 锁定一个快捷栏槽位放图腾，自动补货 |
| `AntiLag` | 抗拉回。把移动包平滑化，降低被服务器拽回去的概率 |
| `SpearAura` 之外的落地相关 | 见 `auto-landing-water` |
| `AutoLandingWater` | 落地水。下落时自动低头放水、落地后自动把水收回来；水里 / 地面两种状态都能触发，水被冲走了会重新找水源 |
| `SuperVClip` | 垂直穿墙 |
| `HorizontalVClip` | 水平穿墙 |
| `PacketLimiter` | 发包限速。**只按 PPS 算**：一秒内发出去的包超过 `max-pps` 就按 `limit-action` 处理（丢包 / 提示 / 两者），保留**白名单**机制，常用包不会被误伤 |
| `PacketLimitTester` | 刷垃圾包，用来测服务器的发包上限 |
| `TPBot` / `TP-bot-V2` | 自动追踪目标 |
| `auto-copy-chat` | 鼠标悬停到某条聊天消息后 `Ctrl+C` 复制它 |
| `auto-ez` | 击杀 / 被击杀猫娘播报，双模式，支持 `%s` 死者 / `%m` 自己 / `%z` 坐标 / `%c` 计数 / `%p` 图腾数 |

### Other

`keep-bouncy`、`model-replace`（把自己替换成原版怪物模型）、`animations`（第一人称挥手动画）、`totem-particle`（改 pop 粒子）、`smooth-camera`、`IQ+++`、`custom-prefix`（彩虹流动前缀）、`custom-fonts`、`custom-sound-alerts`、`web-gui`、`repeater`、`AutoDick`、`AutoSex`、`auto-kouzi`。

### Settings

| 模块 | 说明 |
|---|---|
| `GlobalSettings` | 全局设置。把 `TpUtils` 的移动距离、速度、分包策略、发包间隔这些**统一收到一个模块里**，不用再一个个翻别的面板 |
| `TP Setting` | TP 相关参数（`TpUtils` / `TPUtilX` / `LTpUtils` 三种实现可切） |
| `predict-setting` | 预测相关参数 |

### 重点模块

- **`motion-blur` 动态模糊**：**全局**生效，`strength` 调模糊强弱、`max-opacity` 调混合比例、`sensitivity` 调跟手程度，带 **`only-when-moving`**（站着不动且不转视角时完全不显示），`tint` 可染色
- **`custom-fonts`**：用 HarmonyOS Sans 替换全局字体，6 种字重可调；可正常开关（关闭后重载一次恢复原版字体），不会反复重载资源

## 已知限制

- **QQ 消息**：Windows 同一个会话只保留最新一条通知，所以同一群连发多条时灵动岛只会看到最后一条；内容完全相同的重复消息会去重
- **网易云歌词**：不带 `--remote-debugging-port=9222` 启动时拿不到真实播放进度，歌词按自己的时钟推算，暂停 / 拖动会漂
- **主界面按钮**：是纯色圆角方块，没做磨砂模糊（主界面阶段拿不到世界画面做模糊源）
- **动态模糊**：是"全屏混合一张模糊帧"的实现，跟手程度由位移 / 转向驱动，不是逐像素速度矢量模糊
- **目标 HUD**：靠"谁被打了"来记目标，所以只统计你真正攻击过的实体；同一帧里打多个目标会同时出现多张卡

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

- 此文字来自 DeepSeek-V41-Flash 的产物 嘻嘻
