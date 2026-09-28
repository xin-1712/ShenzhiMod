package com.xin.shenzhimod.datagen

import com.xin.shenzhimod.registry.ModBlocks
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider
import net.minecraft.core.HolderLookup
import java.util.concurrent.CompletableFuture

/**
 * 方块战利品表：三个功能方块都是"自己掉自己"。
 *
 * 龙首账本里的物品与转化核心的三个槽位由方块实体的 `affectNeighborsAfterRemoval`
 * 单独掉落（需求 §4.2"内容物不能丢"），不走战利品表，避免重复掉落。
 */
class ModBlockLootProvider(
	output: FabricPackOutput,
	registries: CompletableFuture<HolderLookup.Provider>,
) : FabricBlockLootSubProvider(output, registries) {

	override fun generate() {
		ModBlocks.ALL.forEach { dropSelf(it) }
	}
}
