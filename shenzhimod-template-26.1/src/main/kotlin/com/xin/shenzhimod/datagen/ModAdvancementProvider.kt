package com.xin.shenzhimod.datagen

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.advancement.ModAdvancements
import com.xin.shenzhimod.anvil.CoreEnchantDefaults
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput

/**
 * 成就（advancements）数据生成。
 *
 * 26.1 的成就目录是 `data/<ns>/advancement/`（**单数**，和 `loot_table` 一样）。
 *
 * 这里刻意**不依赖任何 Minecraft 类**：成就 JSON 的形状非常固定（见 26.1 原版成就），
 * 直接产出 JSON 比追 `Advancement.Builder` 的 API 变动更稳，也和本项目其它数据生成一致。
 *
 * 两类成就的差别只在"条件"：
 * - `inventory_changed`：玩家背包里出现某物品时由原版触发（时刻 = 进背包那一刻）；
 * - `impossible`：原版永不触发，由 [ModAdvancements.award] 在代码里的精确时刻授予。
 */
class ModAdvancementProvider(packOutput: FabricPackOutput) :
	JsonDataProvider(packOutput, "ShenzhiMod advancements") {

	override fun buildEntries(): Map<String, JsonObject> {
		val entries = linkedMapOf<String, JsonObject>()
		entries[path("root")] = root()
		entries[path("compression")] = codeGranted(
			file = "compression",
			parentId = ModAdvancements.ROOT.toString(),
			iconItemId = "shenzhimod:compressed_iron_block",
			frame = "task",
		)
		entries[path("core_agriculture")] = inventoryGranted(
			file = "core_agriculture",
			parentId = ModAdvancements.COMPRESSION.toString(),
			itemId = "shenzhimod:agriculture_core",
			frame = "goal",
		)
		entries[path("core_mineral")] = inventoryGranted(
			file = "core_mineral",
			parentId = ModAdvancements.COMPRESSION.toString(),
			itemId = "shenzhimod:mineral_core",
			frame = "goal",
		)
		entries[path("core_combat")] = inventoryGranted(
			file = "core_combat",
			parentId = ModAdvancements.COMPRESSION.toString(),
			itemId = "shenzhimod:combat_core",
			frame = "goal",
		)
		entries[path("altar_base")] = codeGranted(
			file = "altar_base",
			parentId = ModAdvancements.ROOT.toString(),
			iconItemId = "shenzhimod:ultimate_altar",
			frame = "task",
		)
		entries[path("creation_core")] = codeGranted(
			file = "creation_core",
			parentId = ModAdvancements.ALTAR_BASE.toString(),
			iconItemId = "shenzhimod:creation_core",
			frame = "challenge",
		)
		// "为鞘翅附魔飞行"这件事原版触发器就能表达：物品谓词里带上"附魔"组件条件。
		// 时刻 = 带飞行附魔的鞘翅真正到手那一刻（也就是从铁砧结果槽取出来的瞬间）。
		entries[path("elytra_flight")] = inventoryGranted(
			file = "elytra_flight",
			parentId = ModAdvancements.CREATION_CORE.toString(),
			itemId = "minecraft:elytra",
			frame = "goal",
			conditions = enchantedItemConditions("minecraft:elytra", CoreEnchantDefaults.ENCHANT_FLIGHT),
		)
		return entries
	}

	/**
	 * 根节点：拿到第一块石灰岩。
	 *
	 * 背景用原版石头材质——不需要额外贴图资源，公开仓库里也不会多一个二进制文件。
	 */
	private fun root(): JsonObject {
		val criterion = JsonObject()
		criterion.add("conditions", itemConditions("shenzhimod:limestone"))
		criterion.addProperty("trigger", "minecraft:inventory_changed")

		val criteria = JsonObject()
		criteria.add("limestone", criterion)

		val root = JsonObject()
		root.add("criteria", criteria)
		root.add(
			"display",
			display(
				file = "root",
				iconItemId = "shenzhimod:limestone",
				frame = "task",
				background = "minecraft:gui/advancements/backgrounds/stone",
				showToast = false,
				announceToChat = false,
			),
		)
		root.add("requirements", requirements("limestone"))
		return root
	}

	/** 原版触发器可表达的成就：物品进入玩家物品栏。 */
	private fun inventoryGranted(
		file: String,
		parentId: String,
		itemId: String,
		frame: String,
		conditions: JsonObject = itemConditions(itemId),
	): JsonObject {
		val criterionKey = "has_item"
		val criterion = JsonObject()
		criterion.add("conditions", conditions)
		criterion.addProperty("trigger", "minecraft:inventory_changed")

		val criteria = JsonObject()
		criteria.add(criterionKey, criterion)

		val root = JsonObject()
		root.addProperty("parent", parentId)
		root.add("criteria", criteria)
		root.add("display", display(file, itemId, frame))
		root.add("requirements", requirements(criterionKey))
		return root
	}

	/** 由代码在精确时刻授予的成就：条件写成 `impossible`，永远不会被原版触发。 */
	private fun codeGranted(file: String, parentId: String, iconItemId: String, frame: String): JsonObject {
		val criterion = JsonObject()
		criterion.addProperty("trigger", "minecraft:impossible")

		val criteria = JsonObject()
		criteria.add(ModAdvancements.CODE_CRITERION, criterion)

		val root = JsonObject()
		root.addProperty("parent", parentId)
		root.add("criteria", criteria)
		root.add("display", display(file, iconItemId, frame))
		root.add("requirements", requirements(ModAdvancements.CODE_CRITERION))
		return root
	}

	private fun itemConditions(itemId: String): JsonObject {
		val predicate = JsonObject()
		// 26.1 的物品谓词：`items` 可以写成单个 ID 或 ID 数组（见原版 story/obtain_armor.json）。
		predicate.addProperty("items", itemId)
		val array = JsonArray()
		array.add(predicate)
		val conditions = JsonObject()
		conditions.add("items", array)
		return conditions
	}

	/**
	 * 带"附魔"组件条件的物品谓词。
	 *
	 * 形状照抄 26.1 原版 `husbandry/silk_touch_nest.json` 里的写法：
	 * ```json
	 * {"items":[{"items":"minecraft:elytra","predicates":{"minecraft:enchantments":[{"enchantments":"shenzhimod:flight","levels":{"min":1}}]}}]}
	 * ```
	 * 用组件条件而不是"代码授予"，是因为这一刻原版触发器表达得又准又稳：
	 * 附魔结果从铁砧取出、进入玩家身上的那一刻就会触发，不需要我们再往铁砧逻辑里插钩子。
	 */
	private fun enchantedItemConditions(itemId: String, enchantmentId: String): JsonObject {
		val levels = JsonObject()
		levels.addProperty("min", 1)

		val enchantment = JsonObject()
		enchantment.addProperty("enchantments", enchantmentId)
		enchantment.add("levels", levels)

		val enchantmentList = JsonArray()
		enchantmentList.add(enchantment)

		val predicates = JsonObject()
		predicates.add("minecraft:enchantments", enchantmentList)

		val predicate = JsonObject()
		predicate.addProperty("items", itemId)
		predicate.add("predicates", predicates)

		val array = JsonArray()
		array.add(predicate)
		val conditions = JsonObject()
		conditions.add("items", array)
		return conditions
	}

	private fun display(
		file: String,
		iconItemId: String,
		frame: String,
		background: String? = null,
		showToast: Boolean = true,
		announceToChat: Boolean = true,
	): JsonObject {
		val icon = JsonObject()
		icon.addProperty("id", iconItemId)

		val title = JsonObject()
		title.addProperty("translate", "advancements.shenzhimod.$file.title")
		val description = JsonObject()
		description.addProperty("translate", "advancements.shenzhimod.$file.description")

		val display = JsonObject()
		if (background != null) display.addProperty("background", background)
		display.addProperty("frame", frame)
		display.add("icon", icon)
		display.add("title", title)
		display.add("description", description)
		display.addProperty("show_toast", showToast)
		display.addProperty("announce_to_chat", announceToChat)
		display.addProperty("hidden", false)
		return display
	}

	private fun requirements(criterionKey: String): JsonArray {
		val group = JsonArray()
		group.add(criterionKey)
		val requirements = JsonArray()
		requirements.add(group)
		return requirements
	}

	private fun path(file: String): String = "data/${ShenzhiMod.MOD_ID}/advancement/$file.json"
}
