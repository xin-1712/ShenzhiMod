package com.xin.shenzhimod.datagen

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.machine.CompressionDefaults
import com.xin.shenzhimod.machine.TransmutationDefaults
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput

/**
 * 本模组自定义数据：压缩表、转化表、自定义附魔。
 *
 * 这些数据放在数据包里（而不是硬编码在 Java/Kotlin 常量里），
 * 服务器可以用自己的数据包覆盖，不需要重新编译模组。
 */
class ShenzhiDataProvider(packOutput: FabricPackOutput) :
	JsonDataProvider(packOutput, "ShenzhiMod data tables") {

	override fun buildEntries(): Map<String, JsonObject> {
		val entries = linkedMapOf<String, JsonObject>()
		entries["data/${ShenzhiMod.MOD_ID}/shenzhi_table/compression/default.json"] = compressionTable()
		entries["data/${ShenzhiMod.MOD_ID}/shenzhi_table/transmutation/default.json"] = transmutationTable()
		entries["data/${ShenzhiMod.MOD_ID}/enchantment/totem_cooldown.json"] = totemCooldownEnchantment()
		entries["data/${ShenzhiMod.MOD_ID}/enchantment/flight.json"] = flightEnchantment()
		return entries
	}

	private fun compressionTable(): JsonObject {
		val array = JsonArray()
		for (entry in CompressionDefaults.ENTRIES) {
			val obj = JsonObject()
			obj.addProperty("output", entry.outputPath)
			obj.addProperty("source", entry.sourceItemId)
			obj.addProperty("count", entry.count)
			obj.addProperty("group", entry.group.name.lowercase())
			obj.addProperty("name_en", entry.nameEn)
			obj.addProperty("name_zh", entry.nameZh)
			array.add(obj)
		}
		val root = JsonObject()
		root.add("entries", array)
		return root
	}

	private fun transmutationTable(): JsonObject {
		val array = JsonArray()
		for (entry in TransmutationDefaults.build(DEFAULT_TRANSMUTATION_TICKS)) {
			val obj = JsonObject()
			obj.addProperty("id", entry.id)
			obj.addProperty("input", entry.inputItemId)
			obj.addProperty("input_count", entry.inputCount)
			obj.addProperty("catalyst", entry.catalystItemId)
			obj.addProperty("catalyst_count", entry.catalystCount)
			obj.addProperty("output", entry.outputItemId)
			obj.addProperty("output_count", entry.outputCount)
			obj.addProperty("duration_ticks", entry.durationTicks)
			array.add(obj)
		}
		val root = JsonObject()
		root.add("entries", array)
		return root
	}

	/**
	 * 不死图腾冷却附魔：只用于"带上本附魔的不死图腾"，
	 * 效果本身由 `LivingEntity` 的图腾分支 + 原版冷却组实现（M7）。
	 */
	private fun totemCooldownEnchantment(): JsonObject {
		val root = baseEnchantment(
			descriptionKey = "enchantment.shenzhimod.totem_cooldown",
			supportedItems = "minecraft:totem_of_undying",
			maxLevel = 1,
		)
		return root
	}

	/** 飞行附魔：只对鞘翅生效，能力授予由服务端仲裁器实现（M7）。 */
	private fun flightEnchantment(): JsonObject {
		return baseEnchantment(
			descriptionKey = "enchantment.shenzhimod.flight",
			supportedItems = "minecraft:elytra",
			maxLevel = 1,
		)
	}

	private fun baseEnchantment(descriptionKey: String, supportedItems: String, maxLevel: Int): JsonObject {
		val description = JsonObject()
		description.addProperty("translate", descriptionKey)

		val cost = JsonObject()
		cost.addProperty("base", 1)
		cost.addProperty("per_level_above_first", 0)

		val slots = JsonArray()
		slots.add("any")

		val root = JsonObject()
		root.add("description", description)
		root.addProperty("supported_items", supportedItems)
		root.addProperty("max_level", maxLevel)
		root.addProperty("weight", 1)
		root.addProperty("anvil_cost", 1)
		root.add("min_cost", cost.deepCopy())
		root.add("max_cost", cost.deepCopy())
		root.add("slots", slots)
		// 刻意不写 "effects"：26.1 的附魔效果表在遇到空对象 `{}` 时会报
		// "No key layers in MapLike[{}]"。这两个附魔的效果由代码实现，
		// 数据侧留空即可（缺省即"无数据驱动效果"）。
		return root
	}

	private companion object {
		/** 数据包默认的处理时间；运行时以配置文件为准。 */
		const val DEFAULT_TRANSMUTATION_TICKS = 200
	}
}
