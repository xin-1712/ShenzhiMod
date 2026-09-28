# ShenZhiMod — Fabric 工程

这是模组的**代码本体**（Gradle + Loom + Kotlin）。仓库总览、玩法说明、构建方式与许可证见上一级目录的
[README.md](../README.md)，版本变更见 [CHANGELOG.md](../CHANGELOG.md)。

## 快速开始

需要 **JDK 25**：

```powershell
$env:JAVA_HOME='<你的 JDK 25 目录>'
.\gradlew.bat build
.\gradlew.bat runDatagen
```

## 代码地图

| 目录 | 作用 |
|:---|:---|
| `src/main/kotlin/com/xin/shenzhimod/registry/` | 物品 / 方块 / 方块实体 / 音效 / 创造栏注册 |
| `src/main/kotlin/com/xin/shenzhimod/content/` | 物品、方块、方块实体（账本 / 容器 / 状态机） |
| `src/main/kotlin/com/xin/shenzhimod/machine/` | **纯逻辑**：压缩结算、龙首账本、转化进度、祭坛状态机（可单测） |
| `src/main/kotlin/com/xin/shenzhimod/anvil/` | **纯逻辑**：核心附魔规则与判定 |
| `src/main/kotlin/com/xin/shenzhimod/config/` | 配置模型与 `config/shenzhimod.json` 读写 |
| `src/main/kotlin/com/xin/shenzhimod/datagen/` | 语言 / 模型 / 方块状态 / 配方 / 数据表生成 |
| `src/main/java/com/xin/shenzhimod/mixin/` | 对原版的 Mixin 注入 |
| `src/main/generated/` | 数据生成产物（随 jar 打包，勿手改） |
| `src/main/resources/data/minecraft/worldgen/structure/` | 堡垒结构 `spawn_overrides`（唯一刻意覆盖的原版数据文件） |

## 许可证

MIT，见 [LICENSE](../LICENSE)（含第三方归属声明）。
