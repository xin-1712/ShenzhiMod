package com.xin.shenzhimod.datagen

import com.google.gson.JsonObject
import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.registry.ModItems
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item

/**
 * 物品模型数据生成（26.1 新格式）。
 *
 * 1.21.4 起，物品外观由两层描述：
 * - `assets/<ns>/items/<id>.json`：`{"model":{"type":"minecraft:model","model":"<ns>:item/<id>"}}`
 * - `assets/<ns>/models/item/<id>.json`：`{"parent":"minecraft:item/generated","textures":{"layer0":"<ns>:item/<id>"}}`
 *
 * 方块物品的 `items/<id>.json` 指向方块模型，因此这里会自动区分两者。
 */
class ModItemModelProvider(packOutput: FabricPackOutput) :
	JsonDataProvider(packOutput, "ShenzhiMod item models") {

	override fun buildEntries(): Map<String, JsonObject> {
		val entries = linkedMapOf<String, JsonObject>()
		for (item in ModItems.ALL) {
			val path = pathOf(item) ?: continue
			if (item === ModItems.SYNTHETIC_DRAGON_HEAD) {
				// 龙首是"实体模型"方块：物品图标走原版的特殊头颅模型（和原版龙首一致），
				// 而不是方块模型（方块模型是空的，只用于破坏粒子）。
				// 显示变换与兜底模型都直接借用原版 `minecraft:item/dragon_head`。
				entries["assets/${ShenzhiMod.MOD_ID}/items/$path.json"] = dragonHeadItemDefinition()
				continue
			}
			val modelId = if (item is BlockItem) "block/$path" else "item/$path"
			entries["assets/${ShenzhiMod.MOD_ID}/items/$path.json"] = itemDefinition("${ShenzhiMod.MOD_ID}:$modelId")
			if (item !is BlockItem) {
				entries["assets/${ShenzhiMod.MOD_ID}/models/item/$path.json"] = generatedItemModel("${ShenzhiMod.MOD_ID}:item/$path")
			}
		}
		return entries
	}

	private fun pathOf(item: Item): String? {
		val key: Identifier = BuiltInRegistries.ITEM.getKey(item)
		return if (key.namespace == ShenzhiMod.MOD_ID) key.path else null
	}

	private fun itemDefinition(model: String): JsonObject {
		val modelObject = JsonObject()
		modelObject.addProperty("type", "minecraft:model")
		modelObject.addProperty("model", model)
		val root = JsonObject()
		root.add("model", modelObject)
		return root
	}

	private fun generatedItemModel(texture: String): JsonObject {
		val textures = JsonObject()
		textures.addProperty("layer0", texture)
		val root = JsonObject()
		root.addProperty("parent", "minecraft:item/generated")
		root.add("textures", textures)
		return root
	}

	/**
	 * 龙首方块物品：用原版的 `minecraft:special` + `head/dragon` 显示模型，
	 * 这样背包/手持里看到的和原版龙首一模一样（区别只在方块渲染器用的贴图：眼睛换色了）。
	 */
	private fun dragonHeadItemDefinition(): JsonObject {
		val inner = JsonObject()
		inner.addProperty("type", "minecraft:head")
		inner.addProperty("kind", "dragon")

		val model = JsonObject()
		model.addProperty("type", "minecraft:special")
		model.addProperty("base", "minecraft:item/dragon_head")
		model.add("model", inner)
		model.add("transformation", skullTransformation())

		val root = JsonObject()
		root.add("model", model)
		return root
	}

	/** 与 `minecraft:items/dragon_head.json` 里完全相同的变换（保证背包/手持姿态和原版一致）。 */
	private fun skullTransformation(): JsonObject {
		val transformation = JsonObject()
		transformation.add("left_rotation", floats(1.0, 0.0, 0.0, -0.0))
		transformation.add("right_rotation", floats(0.0, 0.0, 0.0, 1.0))
		transformation.add("scale", floats(1.0, 1.0, 1.0))
		transformation.add("translation", floats(0.5, 0.0, 0.5))
		return transformation
	}

	private fun floats(vararg values: Double): com.google.gson.JsonArray {
		val array = com.google.gson.JsonArray()
		values.forEach { array.add(it) }
		return array
	}
}
