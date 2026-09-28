package com.xin.shenzhimod.datagen

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.xin.shenzhimod.ShenzhiMod
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput

/**
 * 原版类型的配方（工作台）。压缩、核心合成、转化、融合都是自定义机制，不走这里。
 *
 * 配方来源（需求 §3.5 / §4.2 / §4.3 / §4.4 / §4.5）：
 * - 方解石 = 石头×4 + 骨粉×4 → **原版 `minecraft:calcite`** ×4（不新增同名物品）
 * - 石灰岩 = 石头×4 + 燧石×4 → `shenzhimod:limestone` ×4；铜锭 = 铁锭×4 + 火药×4 → 原版铜锭 ×4
 * - 下界之星分解：1 下界之星 → 64 下界之星碎片
 * - 合成龙首 = 黑曜石×4 + 下界合金锭×4 + 原版龙首×1
 * - 转化核心 = 黑曜石×4 + **钻石×4** + 紫水晶块×1
 *   （原稿是下界合金锭×4，实测后按"转化核心需要大量制作"的反馈下调为钻石；已同步需求文档 §4.3）
 * - 终极祭坛 = 中心 1 个合成龙首 + 四边 4 个下界之星 + 四角 4 个黑曜石（3×3，90° 旋转对称）
 */
class ModRecipeProvider(packOutput: FabricPackOutput) :
	JsonDataProvider(packOutput, "ShenzhiMod recipes") {

	override fun buildEntries(): Map<String, JsonObject> = linkedMapOf(
		recipe("calcite_from_stone_and_bone_meal") {
			shapeless(
				ingredients = listOf(
					"minecraft:stone", "minecraft:stone", "minecraft:stone", "minecraft:stone",
					"minecraft:bone_meal", "minecraft:bone_meal", "minecraft:bone_meal", "minecraft:bone_meal",
				),
				result = "minecraft:calcite",
				count = 4,
			)
		},
		recipe("limestone_from_stone_and_flint") {
			shapeless(
				ingredients = listOf(
					"minecraft:stone", "minecraft:stone", "minecraft:stone", "minecraft:stone",
					"minecraft:flint", "minecraft:flint", "minecraft:flint", "minecraft:flint",
				),
				result = "shenzhimod:limestone",
				count = 4,
			)
		},
		recipe("copper_ingot_from_iron_ingot_and_gunpowder") {
			shapeless(
				ingredients = listOf(
					"minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:iron_ingot",
					"minecraft:gunpowder", "minecraft:gunpowder", "minecraft:gunpowder", "minecraft:gunpowder",
				),
				result = "minecraft:copper_ingot",
				count = 4,
			)
		},
		recipe("nether_star_shard_from_nether_star") {
			shapeless(ingredients = listOf("minecraft:nether_star"), result = "shenzhimod:nether_star_shard", count = 64)
		},
		recipe("synthetic_dragon_head") {
			shaped(
				pattern = listOf("oNo", "NdN", "oNo"),
				keys = mapOf(
					"o" to "minecraft:obsidian",
					"N" to "minecraft:netherite_ingot",
					"d" to "minecraft:dragon_head",
				),
				result = "shenzhimod:synthetic_dragon_head",
			)
		},
		recipe("transmutation_core") {
			shaped(
				pattern = listOf("oNo", "NaN", "oNo"),
				keys = mapOf(
					"o" to "minecraft:obsidian",
					"N" to "minecraft:diamond",
					"a" to "minecraft:amethyst_block",
				),
				result = "shenzhimod:transmutation_core",
			)
		},
		recipe("ultimate_altar") {
			shaped(
				pattern = listOf("oso", "sDs", "oso"),
				keys = mapOf(
					"o" to "minecraft:obsidian",
					"s" to "minecraft:nether_star",
					"D" to "shenzhimod:synthetic_dragon_head",
				),
				result = "shenzhimod:ultimate_altar",
			)
		},
	)

	private fun recipe(name: String, body: RecipeHolder.() -> Unit): Pair<String, JsonObject> =
		"data/${ShenzhiMod.MOD_ID}/recipe/$name.json" to RecipeHolder().apply(body).json

	/** 简易配方构造器，只支持工作台的有序/无序两种。 */
	private class RecipeHolder {
		val json = JsonObject()

		fun shapeless(ingredients: List<String>, result: String, count: Int) {
			json.addProperty("type", "minecraft:crafting_shapeless")
			json.addProperty("category", "misc")
			val array = JsonArray()
			ingredients.forEach(array::add)
			json.add("ingredients", array)
			json.add("result", result(result, count))
		}

		fun shaped(pattern: List<String>, keys: Map<String, String>, result: String, count: Int = 1) {
			json.addProperty("type", "minecraft:crafting_shaped")
			json.addProperty("category", "misc")
			val keyObject = JsonObject()
			keys.forEach { (symbol, item) -> keyObject.addProperty(symbol, item) }
			json.add("key", keyObject)
			val patternArray = JsonArray()
			pattern.forEach(patternArray::add)
			json.add("pattern", patternArray)
			json.add("result", result(result, count))
		}

		private fun result(item: String, count: Int): JsonObject {
			val result = JsonObject()
			if (count > 1) result.addProperty("count", count)
			result.addProperty("id", item)
			return result
		}
	}
}
