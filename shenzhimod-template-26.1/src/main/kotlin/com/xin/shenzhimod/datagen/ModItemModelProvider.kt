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
}
