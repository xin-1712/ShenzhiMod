package com.xin.shenzhimod.interaction

import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.anvil.CoreEnchantDecision
import com.xin.shenzhimod.anvil.CoreEnchantDefaults
import com.xin.shenzhimod.anvil.CoreEnchantRequest
import com.xin.shenzhimod.anvil.CoreEnchantResolver
import com.xin.shenzhimod.anvil.CoreEnchantRule
import com.xin.shenzhimod.config.ShenzhiConfig
import com.xin.shenzhimod.util.ItemStacks
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagKey
import net.minecraft.util.StringUtil
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AnvilMenu
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.item.enchantment.ItemEnchantments

/**
 * 核心附魔（模块 F）的适配层。
 *
 * 分工与 `AnvilCompressionHandler` 一致：判定本身在纯逻辑层（`anvil/`），
 * 这里只做三件事：
 * 1. 把 `ItemStack` 翻译成 [CoreEnchantRequest]（物品 ID / 它命中的原版标签 / 已有附魔等级）；
 * 2. 用配置重建规则表；
 * 3. 把判定结果落成一个"已附魔副本"（含改名、互斥检查、图腾冷却组的写入）。
 *
 * **服务端与客户端都会跑这段**：原版 `AnvilMenu#createResult` 本来就双端各算一次
 * （客户端用于即时显示，服务端才是权威），本模组保持同样的语义，所以两条链路必须给出
 * 完全相同的结果——这也是判定逻辑放在纯逻辑层的原因。
 */
object AnvilEnchantHandler {
	/** 一次核心附魔的产出：结果物品 + 固定经验等级成本。 */
	data class Outcome(val result: ItemStack, val costLevels: Int)

	/** 规则表；由 [rebuildFromConfig] 在模组初始化时用配置重建。 */
	@Volatile
	var rules: List<CoreEnchantRule> = emptyList()
		private set

	/** 所有媒介物品 ID（与规则表无关，提前可用，客户端的显示判断要用）。 */
	val mediaItemIds: Set<String> = CoreEnchantDefaults.MEDIA_ITEM_IDS

	private var respectVanillaExclusivity: Boolean = true
	private var writeRepairCost: Boolean = false

	fun rebuildFromConfig(config: ShenzhiConfig = ShenzhiMod.config) {
		val section = config.coreEnchant
		rules = CoreEnchantDefaults.build(
			coreCostLevels = section.coreCostLevels,
			creationCoreCostLevels = section.creationCoreCostLevels,
			sharpnessLevel = section.sharpnessLevel,
			protectionLevel = section.protectionLevel,
			efficiencyLevel = section.efficiencyLevel,
			fortuneLevel = section.fortuneLevel,
		)
		respectVanillaExclusivity = section.respectVanillaExclusivity
		writeRepairCost = section.writeRepairCost
		ShenzhiMod.logger.info(
			"核心附魔规则已载入 {} 条（基础成本 {} 级 / 创世核心 {} 级；锋利 {} 级 / 保护 {} 级 / 效率 {} 级 / 时运 {} 级）",
			rules.size,
			section.coreCostLevels,
			section.creationCoreCostLevels,
			section.sharpnessLevel,
			section.protectionLevel,
			section.efficiencyLevel,
			section.fortuneLevel,
		)
	}

	/** 右槽物品是不是本模组的核心（= 是否是"终极附魔书"）。 */
	fun isCoreMedia(stack: ItemStack): Boolean {
		val id = ItemStacks.idOf(stack) ?: return false
		return mediaItemIds.contains(id)
	}

	/**
	 * 计算核心附魔结果。
	 *
	 * @return `null` 表示"不是核心附魔"——调用方必须原样交回原版 `AnvilMenu` 逻辑；
	 *         非 null 时调用方应当把结果写进结果槽、把成本写进 `cost` 数据槽。
	 */
	fun computeResult(target: ItemStack, media: ItemStack, player: Player, itemName: String?): Outcome? {
		if (target.isEmpty || media.isEmpty) return null
		if (!isCoreMedia(media)) return null
		// 原版对整摞附魔会额外收 40 级；这里直接拒绝整摞，避免"一次收费附魔一整摞"。
		if (target.count > 1) return null

		return try {
			computeUnchecked(target, media, player, itemName)
		} catch (error: Exception) {
			ShenzhiMod.logger.error("核心附魔结算失败，已退回原版铁砧逻辑", error)
			null
		}
	}

	private fun computeUnchecked(target: ItemStack, media: ItemStack, player: Player, itemName: String?): Outcome? {
		val mediaId = ItemStacks.idOf(media) ?: return null
		val targetId = ItemStacks.idOf(target) ?: return null
		val existing = EnchantmentHelper.getEnchantmentsForCrafting(target)

		val decision = CoreEnchantResolver.resolve(
			request = CoreEnchantRequest(
				targetItemId = targetId,
				targetTags = matchedTags(target),
				mediaItemId = mediaId,
				currentLevels = levelMap(existing),
			),
			rules = rules,
		)
		if (decision !is CoreEnchantDecision.Apply) return null

		// 附魔是数据驱动的动态注册表：必须从当前世界的注册表里取 Holder，不能写死。
		val registry = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
		val enchantmentId = Identifier.tryParse(decision.enchantmentId) ?: return null
		val key: ResourceKey<Enchantment> = ResourceKey.create(Registries.ENCHANTMENT, enchantmentId)
		val holder: Holder.Reference<Enchantment> = registry.get(key).orElse(null) ?: return null

		// §8-G9：不绕过原版互斥附魔——已有互斥附魔时直接不产出结果。
		if (respectVanillaExclusivity) {
			for (other in existing.keySet()) {
				if (other == holder) continue
				if (!Enchantment.areCompatible(holder, other)) return null
			}
		}

		val result = target.copy()
		val enchantments = ItemEnchantments.Mutable(existing)
		enchantments.set(holder, decision.level)
		EnchantmentHelper.setEnchantments(result, enchantments.toImmutable())

		// 图腾冷却附魔：把"冷却组"写进物品，原版冷却圆弧才知道该画在哪一组上（§4.7）。
		if (decision.enchantmentId == CoreEnchantDefaults.ENCHANT_TOTEM_COOLDOWN) {
			TotemCooldownService.tagEnchantedTotem(result)
		}

		applyName(result, target, itemName)
		applyRepairCost(result, target)
		return Outcome(result, decision.costLevels)
	}

	/**
	 * 铁砧改名与核心附魔可以同时发生（服务端会先收到改名包再重算结果）。
	 * 这里复刻原版 `AnvilMenu#createResult` 的改名分支，避免"附魔成功但名字被吞掉"。
	 */
	private fun applyName(result: ItemStack, target: ItemStack, itemName: String?) {
		if (itemName != null && !StringUtil.isBlank(itemName)) {
			if (itemName != target.hoverName.string) {
				result.set(DataComponents.CUSTOM_NAME, Component.literal(itemName))
			}
		} else if (result.has(DataComponents.CUSTOM_NAME)) {
			result.remove(DataComponents.CUSTOM_NAME)
		}
	}

	/**
	 * ⚠️ 默认关闭：写入 `repair_cost` 会让物品在后续普通铁砧操作里永久"过于昂贵"（§6.3-F）。
	 * 只有服务器管理员明确把 `coreEnchant.writeRepairCost` 打开时才写。
	 */
	private fun applyRepairCost(result: ItemStack, target: ItemStack) {
		if (!writeRepairCost) return
		val current = target.getOrDefault(DataComponents.REPAIR_COST, 0)
		result.set(DataComponents.REPAIR_COST, AnvilMenu.calculateIncreasedRepairCost(current))
	}

	/** 目标命中的规则标签（只查规则表里出现过的标签，避免无意义的标签解析开销）。 */
	private fun matchedTags(target: ItemStack): Set<String> {
		val matched = HashSet<String>()
		val holder = target.typeHolder()
		for (rule in rules) {
			for (tagId in rule.targetTags) {
				if (matched.contains(tagId)) continue
				val tag = itemTag(tagId) ?: continue
				if (holder.`is`(tag)) matched.add(tagId)
			}
		}
		return matched
	}

	private fun itemTag(tagId: String): TagKey<Item>? {
		val identifier = Identifier.tryParse(tagId) ?: return null
		return TagKey.create(Registries.ITEM, identifier)
	}

	private fun levelMap(enchantments: ItemEnchantments): Map<String, Int> {
		val levels = HashMap<String, Int>()
		for (entry in enchantments.entrySet()) {
			levels[entry.key.registeredName] = entry.intValue
		}
		return levels
	}

	/**
	 * 原版铁砧的"合书/合物品"分支会把等级夹到附魔的 `max_level`（锋利 = 5）。
	 * 对普通原版物品这是对的，但会把本模组写出来的超上限等级**降级**（§4.6 的"同种附魔取更高等级"），
	 * 因此这里做一次只增不减的修正：结果物品上每种附魔的等级不低于**输入物品**上的等级。
	 *
	 * 只会抬高"输入本来就有的等级"，不会凭空给新的附魔/等级，所以对原版物品完全没有影响。
	 *
	 * @return 是否真的改动了结果（调用方据此决定要不要再同步一次）
	 */
	fun enforceNoDowngrade(input: ItemStack, result: ItemStack): Boolean {
		if (input.isEmpty || result.isEmpty) return false
		val source = EnchantmentHelper.getEnchantmentsForCrafting(input)
		if (source.isEmpty) return false
		val mutable = ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(result))
		var changed = false
		for (entry in source.entrySet()) {
			val holder = entry.key
			val level = entry.intValue
			if (mutable.getLevel(holder) < level) {
				mutable.set(holder, level)
				changed = true
			}
		}
		if (changed) EnchantmentHelper.setEnchantments(result, mutable.toImmutable())
		return changed
	}

	/**
	 * 界面显示用：当前铁砧菜单是不是"核心附魔"状态。
	 *
	 * 判定只要"右槽是核心 + 结果槽有东西 + 成本 ≥ 40"就够了——结果槽的内容是服务端算出来同步过来的，
	 * 因此客户端不需要自己再跑一遍标签/注册表判定（`AnvilScreen` 也就不用碰原版标签系统）。
	 */
	fun isCoreEnchantResult(menu: AnvilMenu): Boolean {
		if (menu.cost < 40) return false
		if (!isCoreMedia(menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).item)) return false
		return menu.getSlot(AnvilMenu.RESULT_SLOT).hasItem()
	}

}
