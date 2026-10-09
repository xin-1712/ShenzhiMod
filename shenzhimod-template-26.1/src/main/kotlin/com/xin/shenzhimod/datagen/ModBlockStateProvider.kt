package com.xin.shenzhimod.datagen

import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.registry.ModBlocks
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Block

/**
 * 方块状态与方块模型数据生成。
 *
 * 普通方块统一用 `minecraft:block/cube_all` + 同名贴图；
 * **合成龙首是例外**：它的外观由方块实体渲染器画（和原版龙首同一个头颅模型），
 * 所以方块模型是"空模型"（只留破坏粒子贴图），方块状态要覆盖 powered × active × facing 的全部组合。
 * 转化核心与终极祭坛是立方体，但会按"是否在干活"切换贴图：
 * 转化核心用 `lit`（静止 / 工作中），终极祭坛用 `state`（空转 / 三核就位 / 融合中）；
 * 工作时那一档指向 8 帧竖排动画贴图（同名 `.png.mcmeta` 控制帧速）。
 */
class ModBlockStateProvider(packOutput: FabricPackOutput) :
	JsonDataProvider(packOutput, "ShenzhiMod block states") {

	override fun buildEntries(): Map<String, JsonObject> {
		val entries = linkedMapOf<String, JsonObject>()
		for (block in ModBlocks.ALL) {
			val path = pathOf(block) ?: continue
			if (block === ModBlocks.SYNTHETIC_DRAGON_HEAD) {
				entries["assets/${ShenzhiMod.MOD_ID}/blockstates/$path.json"] = dragonHeadBlockState("${ShenzhiMod.MOD_ID}:block/$path")
				// 破坏粒子直接用原版龙贴图（省一张占位贴图，粒子看起来也像龙首的材质）
				entries["assets/${ShenzhiMod.MOD_ID}/models/block/$path.json"] = emptyModel("minecraft:entity/enderdragon/dragon")
			} else if (block === ModBlocks.TRANSMUTATION_CORE) {
				// 熔炉式两态：静止 / 工作中。工作中的贴图是 8 帧动画（textures/block/*_lit.png + 同名 .mcmeta）。
				entries[blockStatePath(path)] = variantBlockState(
					"lit=false" to "${ShenzhiMod.MOD_ID}:block/$path",
					"lit=true" to "${ShenzhiMod.MOD_ID}:block/${path}_lit",
				)
				entries[blockModelPath(path)] = cubeAllModel("${ShenzhiMod.MOD_ID}:block/$path")
				entries[blockModelPath("${path}_lit")] = cubeAllModel("${ShenzhiMod.MOD_ID}:block/${path}_lit")
			} else if (block === ModBlocks.ULTIMATE_ALTAR) {
				// 四态：底座不正确 / 空转 / 三核就位待启动 / 融合中，除空转外都是 8 帧动画贴图。
				entries[blockStatePath(path)] = variantBlockState(
					"state=idle" to "${ShenzhiMod.MOD_ID}:block/$path",
					"state=invalid" to "${ShenzhiMod.MOD_ID}:block/${path}_invalid",
					"state=ready" to "${ShenzhiMod.MOD_ID}:block/${path}_ready",
					"state=active" to "${ShenzhiMod.MOD_ID}:block/${path}_active",
				)
				entries[blockModelPath(path)] = cubeAllModel("${ShenzhiMod.MOD_ID}:block/$path")
				entries[blockModelPath("${path}_invalid")] = cubeAllModel("${ShenzhiMod.MOD_ID}:block/${path}_invalid")
				entries[blockModelPath("${path}_ready")] = cubeAllModel("${ShenzhiMod.MOD_ID}:block/${path}_ready")
				entries[blockModelPath("${path}_active")] = cubeAllModel("${ShenzhiMod.MOD_ID}:block/${path}_active")
			} else {
				entries[blockStatePath(path)] = blockState("${ShenzhiMod.MOD_ID}:block/$path")
				entries[blockModelPath(path)] = cubeAllModel("${ShenzhiMod.MOD_ID}:block/$path")
			}
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

	private fun blockStatePath(path: String): String = "assets/${ShenzhiMod.MOD_ID}/blockstates/$path.json"

	private fun blockModelPath(path: String): String = "assets/${ShenzhiMod.MOD_ID}/models/block/$path.json"

	/** 按方块状态条件（如 "lit=true" / "state=ready"）分别指定模型。 */
	private fun variantBlockState(vararg variants: Pair<String, String>): JsonObject {
		val variantsObject = JsonObject()
		for ((condition, model) in variants) {
			val variant = JsonObject()
			variant.addProperty("model", model)
			variantsObject.add(condition, variant)
		}
		val root = JsonObject()
		root.add("variants", variantsObject)
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

	/** 空模型：只提供破坏粒子贴图，几何体交给方块实体渲染器。 */
	private fun emptyModel(texture: String): JsonObject {
		val textures = JsonObject()
		textures.addProperty("particle", texture)
		val root = JsonObject()
		root.add("textures", textures)
		return root
	}

	/** 龙首的方块状态：powered（2）× active（2）× facing（4）全枚举，全部指向同一个空模型。 */
	private fun dragonHeadBlockState(model: String): JsonObject {
		val variants = JsonObject()
		for (powered in listOf(false, true)) {
			for (active in listOf(false, true)) {
				for (facing in listOf("north", "east", "south", "west")) {
					val variant = JsonObject()
					variant.addProperty("model", model)
					variants.add("facing=$facing,powered=$powered,active=$active", variant)
				}
			}
		}
		val root = JsonObject()
		root.add("variants", variants)
		return root
	}
}
