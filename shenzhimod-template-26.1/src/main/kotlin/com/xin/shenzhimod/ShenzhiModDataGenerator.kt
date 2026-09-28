package com.xin.shenzhimod

import com.xin.shenzhimod.datagen.ModBlockLootProvider
import com.xin.shenzhimod.datagen.ModBlockStateProvider
import com.xin.shenzhimod.datagen.ModItemModelProvider
import com.xin.shenzhimod.datagen.ModLanguageProvider
import com.xin.shenzhimod.datagen.ModRecipeProvider
import com.xin.shenzhimod.datagen.ShenzhiDataProvider
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.minecraft.core.HolderLookup
import java.util.concurrent.CompletableFuture

/**
 * 数据生成入口（`.\gradlew.bat runDatagen`）。
 *
 * 所有数据都在这里产出，仓库里不手写 json：
 * - 语言（中/英）
 * - 物品模型 / 方块状态 / 方块模型（26.1 的 `assets/<ns>/items/` 物品模型定义新格式）
 * - 原版类型配方（工作台、无序合成）
 * - 方块战利品表
 * - 压缩表 / 转化表 / 自定义附魔（数据包 JSON）
 *
 * 生成目录是 `src/main/generated`，已在 `build.gradle` 里挂到主资源路径上。
 */
object ShenzhiModDataGenerator : DataGeneratorEntrypoint {
	override fun onInitializeDataGenerator(fabricDataGenerator: FabricDataGenerator) {
		val pack = fabricDataGenerator.createPack()

		pack.addProvider { output: FabricPackOutput -> ModItemModelProvider(output) }
		pack.addProvider { output: FabricPackOutput -> ModBlockStateProvider(output) }
		pack.addProvider { output: FabricPackOutput -> ModRecipeProvider(output) }
		pack.addProvider { output: FabricPackOutput -> ShenzhiDataProvider(output) }

		pack.addProvider { output: FabricPackOutput, registries: CompletableFuture<HolderLookup.Provider> ->
			ModLanguageProvider(output, "en_us", registries)
		}
		pack.addProvider { output: FabricPackOutput, registries: CompletableFuture<HolderLookup.Provider> ->
			ModLanguageProvider(output, "zh_cn", registries)
		}
		pack.addProvider { output: FabricPackOutput, registries: CompletableFuture<HolderLookup.Provider> ->
			ModBlockLootProvider(output, registries)
		}
	}
}
