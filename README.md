# ShenZhiMod

[![build](https://github.com/xin-1712/ShenzhiMod/actions/workflows/build.yml/badge.svg)](https://github.com/xin-1712/ShenzhiMod/actions/workflows/build.yml)

面向**生电（Technical Minecraft）**玩家的 Minecraft **26.1 / Fabric** 终局模组：
把玩家已有的自动化产能，通过「材料压缩 → 三核心 → 终极融合」兑换成终极奖励；
并让堡垒遗迹里的猪灵蛮兵可以量产远古残骸。

> **当前版本：v1.0.0**（首个正式版，对应 Git 标签 `v1.0.0`；逐版变更见 [CHANGELOG.md](CHANGELOG.md)）。
> 小提示：本模组**需要 Java 25**（Minecraft 26.1 的运行时要求）。

## 安装

1. 用 **Java 25** 启动 Minecraft **26.1**，并装好 **Fabric Loader ≥ 0.19.5**
   （Java 25 是 Minecraft 26.1 自身的运行时要求，不是本模组额外加的）；
2. 在 `mods/` 里放三个 jar：**Fabric API**、**Fabric Language Kotlin**、**ShenzhiMod**；
3. 联机时**客户端与服务端都要装**——所有机制都是服务端权威的，客户端只负责渲染与输入。

可选：再装上 **JEI**，就能在游戏里直接查压缩 / 核心合成 / 转化三类配方（不装也完全不影响玩法）。

## 内容一览

| 体系 | 内容 |
|:---|:---|
| 压缩体系 | 铁砧 + 重锤把掉落物压成"压缩态"物品（37 种，按核心分成农业 / 矿物 / 战斗三组） |
| 三核心 | 合成龙首在 3×3×3 范围内吸收压缩态，按配方合成 **农业核心 / 矿物核心 / 战斗核心** |
| 终极融合 | 3×3 下界合金底座上放置**终极祭坛**，投入三核心，30 秒融合出 **创世核心** |
| 核心附魔 | 在铁砧上把核心当"终极附魔书"：锋利（默认 XVII，可配置）/ 保护 XII（**只给胸甲**）/ 效率 X / 时运 X / 冷却 I / 飞行 I |
| 能力扩展 | 不死图腾"冷却"附魔（复用原版冷却圆弧，冷却随存档保留）、下界之星碎片投掷、鞘翅创造式飞行（速度接近生存模式，飞行消耗耐久，退出重进保持飞行状态，**飞行时鞘翅会扇动并拖出尾迹**） |
| 下界合金量产 | 堡垒遗迹按李芒果空岛的规则刷猪灵蛮兵，掉落远古残骸（5%，抢夺每级 +1%） |
| 前体材料 | 方解石 / 石灰岩 / 铜锭 的自动化配方，以及 1 下界之星 → 64 下界之星碎片 |
| 成就 | 8 个进度：拿到石灰岩 / 第一次压缩 / 三个核心各一个 / 祭坛底座摆对 / 落雷中诞生的创世核心（**天命所归**）/ 给鞘翅附上飞行（**天空不再是极限**） |
| JEI 集成（可选） | 装上 JEI 后可直接查询压缩、龙首核心合成、转化核心三类配方，并在铁砧分类里看到核心附魔（含目标装备与结果） |

## 工程结构

```
ShenzhiMod/
├── README.md                   # 项目说明（本文件）
├── CHANGELOG.md                # 更新日志
├── LICENSE                     # MIT + 第三方归属声明
├── .gitignore
├── .gitattributes
├── .github/workflows/build.yml  # CI：每次 push / PR 自动 build
└── shenzhimod-template-26.1/   # Fabric 工程本体（所有代码与资源）
```

`shenzhimod-template-26.1/src/main/kotlin/com/xin/shenzhimod/` 下的代码按职责分层：
`registry/` 注册、`content/` 物品与方块、`machine/`+`anvil/`+`ability/` 纯逻辑（不依赖 Minecraft 类，可单测）、
`datagen/` 数据生成、`config/` 配置、`mixin/` 原版注入。

## 构建与运行

需要 **JDK 25**。用环境变量 `JAVA_HOME` 指向本机 JDK 即可，工程本身不记录任何本机路径。

```powershell
cd shenzhimod-template-26.1
$env:JAVA_HOME='<你的 JDK 25 目录>'   # 例如 Windows 上的 C:\Java\jdk-25
.\gradlew.bat build          # 编译 + 打包（产物在 build/libs）
.\gradlew.bat runDatagen     # 重新生成配方/模型/语言/数据表（输出到 src/main/generated）
.\gradlew.bat runClient      # 启动客户端测试
.\gradlew.bat runServer      # 启动服务端测试
```

Linux / macOS 把 `.\gradlew.bat` 换成 `./gradlew` 即可。

可选的 Carpet 参考依赖（只用于对照猪灵蛮兵的刷怪参数，**不参与运行、不打包**）：

```powershell
.\gradlew.bat build -PcarpetReference=true
```

## 可选依赖：JEI

装上 [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) 后，本模组的配方会自动出现在 JEI 里：

- **铁砧压缩**（37 条）：`基础物品 × N → 压缩态`；
- **龙首核心合成**（3 条）：合成某个核心所需的全部压缩态；
- **转化核心**（3 条）：`输入 × N + 催化剂 → 产物`，并标注耗时；
- **核心附魔**：注册进 JEI 自带的**铁砧**分类（`左槽 + 右槽 → 附魔后的物品`），
  四个核心的**物品信息页**里还会列出"能附什么、几级、固定花多少级经验"。

JEI 是**纯可选**依赖：不装 JEI 时模组一切照常，JEI 相关代码根本不会被加载
（工程里对 JEI 的依赖也只存在于编译期，不会打进 jar、更不会要求玩家安装）。

## 配置

首次启动会在 `config/shenzhimod.json` 生成完整配置（含注释性的键名与默认值）。
所有玩法数值都在这里：核心附魔成本、图腾冷却、飞行速度、远古残骸掉率、各模块开关等。
新增字段会自动以默认值补齐，不会因为配置文件过期而崩溃。

压缩表与转化表可以用**数据包**覆盖（`data/shenzhimod/shenzhi_table/`），无需重新编译模组。

## 许可证与归属

本项目以 **MIT** 许可证发布，见 [LICENSE](LICENSE)。

实现"堡垒遗迹猪灵蛮兵刷怪"时，参考并移植了以下 MIT 许可项目的规则参数与生成谓词逻辑：

- **Carpet Mod** (fabric-carpet) — MIT — https://github.com/gnembon/fabric-carpet
- **Carpet Sky Additions** — MIT, Copyright (c) 2021-2023 Jack Sorrell
- **CarpetSkyAdditions-Reborn**（中文分支） — MIT, Copyright (c) 2026 难忘的旋律

移植内容限于：结构刷怪覆盖参数（权重与数量）、猪灵蛮兵放置类型调整、生成谓词判定顺序、远古残骸掉落概率。

## 版本状态

**v1.0.0（已发布）**——首个正式版。工程框架与注册体系、配置系统、数据生成链路；
压缩体系、合成龙首、转化核心（含 GUI）、终极祭坛（含真实落雷）；
堡垒遗迹猪灵蛮兵刷怪链路（结构覆盖 + Mixin + 生成谓词 + 掉落池）；
核心附魔（铁砧六条规则，含战斗核心的保护 XII）与能力扩展（图腾冷却 / 下界之星碎片投掷 / 鞘翅飞行），
含"退出重进不重置冷却 / 保持飞行状态"这类跨存档行为；
JEI 配方查询、8 个成就、飞行中的鞘翅动画；物品 / 方块 / 头颅贴图齐全。

仍待完善（都不影响 1.0 的正常游玩）：

- **自定义音效**：目前全部复用原版音效，`ModSounds` 注册的 6 个自定义 `SoundEvent` 还没有配套的
  `.ogg` 与 `sounds.json`（补上音频文件即可启用）；
- 联机回归测试与数值平衡定值。
