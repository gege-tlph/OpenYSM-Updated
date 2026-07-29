# OpenYSM-Updated — Minecraft 1.21.11 Fabric

> [!IMPORTANT]
> 本仓库是 [IzumiiKonata/OpenYSM-Updated](https://github.com/IzumiiKonata/OpenYSM-Updated)
> 的分支，在其 Fabric 高版本移植的基础上补上了**车万女仆（TouhouLittleMaid）兼容层**，
> 目标平台为 **Minecraft 1.21.11 + Fabric**，独立维护。
> 本项目不代表 Yes Steve Model 或 OpenYSM 的官方版本。

[OpenYSM](https://github.com/OpenYSM/OpenYSM) 是 Yes Steve Model 的开源实现，
让玩家使用自定义的 Bedrock / Gecko 模型替换原版玩家模型，并支持动画、材质切换与动画轮盘。
原版基于 Minecraft 1.20.1 Forge。

## 关于本分支

上游已经把 OpenYSM 移植到了 1.21.11 Fabric，但其中的车万女仆兼容模块在 Fabric 平台上是**全 stub**
（`isLoaded()` 恒返回 false、16 个女仆 molang 变量全部返回常量），即装了也不会生效。
本分支把它做成了真的实现，用作
[TouhouLittleMaid: Tsumugi](https://github.com/gege-tlph/TouhouLittleMaid-Tsumugi)
的 YSM 兼容依赖。

兼容层已实现的内容：

- 女仆的模型切换与渲染、13 个动画状态、材质切换、名字牌，以及雕像与手办中的动画。
- 女仆专用的模型选择屏与材质选择屏，并接上开屏事件。
- 动画轮盘：按键与玩家自己的轮盘分流，仅在准星指向已切换 YSM 模型且属主为本地玩家的女仆时接管。
- 骨骼定位桥，把 YSM 的骨骼与模型映射到车万女仆的定位契约上。
- 双向网络包，已在专用服务器与多人环境下验证。
- 未安装车万女仆时完全隔离：相关类不会被加载，功能自动关闭。

## 本分支相对上游修掉的缺陷

以下三项都**与车万女仆无关**，是任何使用者都会踩到的上游缺陷：

- **在 GUI 中渲染实体会被压成全黑剪影。** 载具模型接管的判据被放在了副作用之后，
  导致每个实体都会先被重新提取一遍渲染状态。世界渲染无感，但第三方模组在 GUI
  中渲染实体时就会全黑。
- **客户端在 `handleLogin` 崩溃（仅开发环境）。** 玩家能力对象在构造期就读取 SERVER
  作用域配置，而该对象挂在**每一个**玩家上，包含客户端的本地玩家。服务端未安装本模组时
  那份配置永远不会送达，开发环境下当场抛出异常。正式环境虽不崩溃，但会静默使用硬编码
  默认值而非服务端配置值。已改为首次读取时惰性解析。
- **动画预览界面：载具与床画不出来，且所有动画的位置都不对。** 见下节。

## 已知问题

~~ModelPreviewRenderer 中的动画暂未适配, 会出现问题 (sleep, ride 等动画)~~
**已修复**。原因是三个互相独立的缺陷：

1. 载具从未被绘制。1.21.11 把 GUI 中的实体渲染改为提交式渲染图模型后，
   布景回调只能拿到立即模式的参数，移植时方法体就留空了；方块因为其绘制接口
   仍是立即模式而幸存。
2. 床从未被绘制。与方块的渲染形状无关——**床的方块模型本身没有任何几何**，
   只有一张粒子贴图，绘制单个方块的接口对它是空操作。床的几何由方块实体渲染器提供。
   该缺陷在 1.20.1 原版上同样存在。
3. 所有动画的位置都不对。GUI 实体渲染器是「先平移再旋转」，而原版是在旋转**之后**
   才施加动画偏移；把它折进旋转前的平移会被 180° 翻转取反并掺入一个额外分量，
   取任何数值都不可能等价。

### 仍然开着的

**YSM 模型的女仆不渲染车万女仆的挂件**（背包、手持物、背旗、头顶方块）。
通道两端都在、中间断开：车万女仆的相关渲染层在移植到 1.21.11 时改用了自家的模型状态，
定位接口成了零消费者接口。**缺口在车万女仆一侧**，本分支的骨骼定位桥已就绪。

## 版本支持

| 版本      | 支持状态                                        |
|---------|---------------------------------------------|
| 1.20.1  | ✅ (原生)                                      |
| 1.21.1  | ✅ (见 commit history)                        |
| 1.21.4  | ✅ (见 commit history, 有的类忘了交了可能无法编译，从后面的提交里找 |
| 1.21.8  | ✅ (见 commit history)                        |
| 1.21.9  | ✅ (见 commit history)                        |
| 1.21.10 | ✅ (见 commit history)                        |
| 1.21.11 | ✅                                           |
| 26.1.x  | ❌ Architectury 没更新                          |

## 安装

### 必要前置

| 组件 | 已验证版本 | 下载 |
|---|---:|---|
| Minecraft | 1.21.11 | [Minecraft 官网](https://www.minecraft.net/) |
| Java | 21 | [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21) |
| Fabric Loader | 0.19.2 | [Fabric Installer](https://fabricmc.net/use/installer/) |

Fabric API、Architectury、Cardinal Components 与 Forge Config API Port 已内嵌在发布产物中，
不需要另行安装。

本模组请从 [Releases](../../releases) 下载，选择名称中**不含** `sources` 或 `dev-shadow`
的 `openysm-fabric-*.jar`。客户端与服务器都需要安装。

### 与车万女仆一起使用

车万女仆兼容层会在检测到 [TouhouLittleMaid: Tsumugi](https://github.com/gege-tlph/TouhouLittleMaid-Tsumugi)
时自动启用，不需要额外配置。未安装时本模组行为与上游一致。

模型包如果要提供女仆动画，`ysm.json` 中必须声明 `"tlm": "animations/tlm.animation.json"`，
否则女仆动画会**静默不播**。

## 注意

### 该项目仅作为高版本移植可行性验证，_**对可用性没有任何保证，使用过程中可能会出现大量bug**_，如果发现问题请提交 issue。

## 许可

沿用上游许可，见 [LICENSE.txt](LICENSE.txt)。
