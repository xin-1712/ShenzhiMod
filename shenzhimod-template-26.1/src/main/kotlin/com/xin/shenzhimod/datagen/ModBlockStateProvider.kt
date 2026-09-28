package com.xin.shenzhimod.datagen

import com.google.gson.JsonObject
import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.registry.ModBlocks
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Block

/**
 * 方块状态与方块模型数据生成。
 *
 * 当前统一用 `minecraft:block/cube_all` 父模型 + 同名贴图，
 * 目的是**先让方块在游戏里看得见、能调试**；正式模型与贴图在 M9 替换
 * （龙首需要朝向、祭坛需要复杂模型）。替换时只改这个文件，不影响逻辑。
 */
class ModBlockStateProvider(packOutput: FabricPackOutput) :
	JsonDataProvider(packOutput, "ShenzhiMod block states") {

	override fun buildEntries(): Map<String, JsonObject> {
		val entries = linkedMapOf<String, JsonObject>()
		for (block in ModBlocks.ALL) {
			val path = pathOf(block) ?: continue
			entries["assets/${ShenzhiMod.MOD_ID}/blockstates/$path.json"] = blockState("${ShenzhiMod.MOD_ID}:block/$path")
			entries["assets/${ShenzhiMod.MOD_ID}/models/block/$path.json"] = cubeAllModel("${ShenzhiMod.MOD_ID}:block/$path")
		}
		return entries
	}

	private fun pathOf(block: Block): String? {
		val key: Identifier = BuiltInRegistries.BLOCK.getKey(block)
		return if (key.namespace == ShenzhiMod.MOD_ID) key.path else null
	}

	private fun blockState(model: String): JsonObject {
		val variant = JsonObject()
		variant.addProperty("model", model)
		val variants = JsonObject()
		variants.add("", variant)
		val root = JsonObject()
		root.add("variants", variants)
		return root
	}

	private fun cubeAllModel(texture: String): JsonObject {
		val textures = JsonObject()
		textures.addProperty("all", texture)
		val root = JsonObject()
		root.addProperty("parent", "minecraft:block/cube_all")
		root.add("textures", textures)
		return root
	}
}
