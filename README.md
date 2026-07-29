# OpenYSM-Updated

> [!IMPORTANT]
> 本仓库是 [IzumiiKonata/OpenYSM-Updated](https://github.com/IzumiiKonata/OpenYSM-Updated) 的非官方维护 fork，面向 Minecraft 1.21.11 与 Fabric，并提供东方小女仆 Tsumugi 兼容。本项目不代表 Yes Steve Model、OpenYSM 或其上游维护者的官方版本。

[OpenYSM](https://github.com/OpenYSM/OpenYSM) 是 Yes Steve Model 的开源实现，可使用 Bedrock 或 Gecko 模型替换玩家模型，并支持动画、材质切换与动画轮盘。本 fork 在 Fabric 高版本移植基础上提供 [东方小女仆 Tsumugi](https://github.com/gege-tlph/TouhouLittleMaid-Tsumugi) 的 YSM 模型兼容。

English: An unofficial Fabric 1.21.11 fork of OpenYSM-Updated with Touhou Little Maid: Tsumugi model integration.

## 功能

- 为玩家加载和渲染自定义 Bedrock 或 Gecko 模型
- 支持动画、材质切换、动画轮盘和模型选择界面
- 为 Tsumugi 女仆提供模型切换、动画、材质和名称显示
- 支持女仆模型选择界面、动画轮盘与骨骼定位
- Tsumugi 未安装时自动停用女仆兼容功能
- 客户端与服务端之间同步模型和动画数据

## 兼容性

| 组件 | 要求 |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.17.0 或更高版本 |
| Java | 21 |
| 安装位置 | 客户端与服务端 |

Fabric API、Architectury API、Cardinal Components API 与 Forge Config API Port 已包含在发布产物中，无需另行安装。

## 安装

1. 安装适用于 Minecraft 1.21.11 的 Fabric Loader。
2. 从 [GitHub Releases](https://github.com/gege-tlph/OpenYSM-Updated/releases/latest) 下载 `openysm-fabric-*.jar`。
3. 将 JAR 放入客户端和服务端的 `mods` 目录。
4. 如需女仆模型兼容，同时安装 [东方小女仆 Tsumugi](https://github.com/gege-tlph/TouhouLittleMaid-Tsumugi)。兼容功能会自动启用，无需额外配置。

## 模型包兼容

为 Tsumugi 女仆提供动画的模型包，需要在 `ysm.json` 中声明女仆动画文件：

```json
{
  "tlm": "animations/tlm.animation.json"
}
```

模型包的其余结构与 OpenYSM 规范保持一致。通用模型制作说明请参考 [OpenYSM Wiki](https://github.com/OpenYSM/OpenYSM/wiki)。

## 从源码构建

需要 JDK 21。克隆仓库后运行：

```bash
./gradlew build
```

Windows PowerShell：

```powershell
.\gradlew.bat build
```

Fabric 构建产物位于 `fabric/build/libs/`。

## 相关项目

| 项目 | 关系 |
|---|---|
| [OpenYSM/OpenYSM](https://github.com/OpenYSM/OpenYSM) | OpenYSM 原始项目与通用文档来源 |
| [IzumiiKonata/OpenYSM-Updated](https://github.com/IzumiiKonata/OpenYSM-Updated) | 本 fork 的直接上游 |
| [东方小女仆 Tsumugi](https://github.com/gege-tlph/TouhouLittleMaid-Tsumugi) | 女仆模型兼容目标 |
| [Maid Restaurant](https://github.com/gege-tlph/MaidRestaurant) | Tsumugi 的餐厅自动化附属模组 |
| [Patchouli](https://github.com/gege-tlph/Patchouli) | 同一维护系列中的游戏内文档库 |

## 许可证

本项目沿用上游的 [MIT License](LICENSE.txt)。
