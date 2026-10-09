package com.xin.shenzhimod.compat.jei

import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.anvil.CoreEnchantDefaults
import com.xin.shenzhimod.anvil.CoreEnchantRule
import com.xin.shenzhimod.interaction.AnvilEnchantHandler
import com.xin.shenzhimod.interaction.TotemCooldownService
import com.xin.shenzhimod.registry.ModItems
import com.xin.shenzhimod.util.ItemStacks
import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.constants.RecipeTypes
import mezz.jei.api.recipe.category.IRecipeCategory
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe
import mezz.jei.api.registration.IRecipeCatalystRegistration
import mezz.jei.api.registration.IRecipeCategoryRegistration
import mezz.jei.api.registration.IRecipeRegistration
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.item.enchantment.ItemEnchantments

/**
 * JEI 集成（可选依赖）。
 *
 * 玩家侧效果：按 R/U 就能查到本模组的四类"配方"——
 * 1. 铁砧压缩（37 条，`基础物品 × N → 压缩态`）；
 * 2. 合成龙首的核心合成（3 条，`全部压缩态 → 三核心`）；
 * 3. 转化核心（3 条，`输入 + 催化剂 → 产物`，带耗时）；
 * 4. 核心附魔：注册进 JEI **自带的铁砧分类**（`左槽 + 右槽 → 附魔后的物品`），
 *    并在四个核心的物品信息页里列出"能给什么附魔、写几级、固定花费几级"。
 *
 * 设计要点：
 * - **可选依赖**：本类只被 JEI 通过 `jei_mod_plugin` 入口点加载；没装 JEI 的玩家
 *   根本不会加载这个类，模组其它功能完全不受影响（`build.gradle` 里也是 `compileOnly`）。
 * - **不硬编码数值**：压缩 / 合成 / 转化读运行时数据表，附魔规则读
 *   [AnvilEnchantHandler.rules]（由配置生成），所以服务器改了配置或数据包，
 *   JEI 里看到的也是真实数值。
 */
@JeiPlugin
class ShenzhiJeiPlugin : IModPlugin {
	override fun getPluginUid(): Identifier = ShenzhiMod.id("jei_plugin")

	override fun registerCategories(registration: IRecipeCategoryRegistration) {
		val guiHelper = registration.jeiHelpers.guiHelper
		val categories: Array<IRecipeCategory<*>> = arrayOf(
			CompressionJeiCategory(guiHelper),
			CoreAssemblyJeiCategory(guiHelper),
			TransmutationJeiCategory(guiHelper),
		)
		registration.addRecipeCategories(*categories)
	}

	override fun registerRecipes(registration: IRecipeRegistration) {
		registration.addRecipes(ShenzhiJeiRecipeTypes.COMPRESSION, ShenzhiJeiRecipeTypes.compressionRecipes())
		registration.addRecipes(ShenzhiJeiRecipeTypes.CORE_ASSEMBLY, ShenzhiJeiRecipeTypes.coreAssemblyRecipes())
		registration.addRecipes(ShenzhiJeiRecipeTypes.TRANSMUTATION, ShenzhiJeiRecipeTypes.transmutationRecipes())
		registerCoreEnchantRecipes(registration)
		registerCoreInfoPages(registration)
	}

	override fun registerRecipeCatalysts(registration: IRecipeCatalystRegistration) {
		// "在哪台机器上用"：压缩是"重锤 + 铁砧"，另外两个是各自的方块。
		registration.addCraftingStation(ShenzhiJeiRecipeTypes.COMPRESSION, Items.ANVIL, Items.MACE)
		registration.addCraftingStation(ShenzhiJeiRecipeTypes.CORE_ASSEMBLY, ModItems.SYNTHETIC_DRAGON_HEAD)
		registration.addCraftingStation(ShenzhiJeiRecipeTypes.TRANSMUTATION, ModItems.TRANSMUTATION_CORE)
	}

	/**
	 * 核心附魔：每条规则 × 每个合法目标物品各注册一条"铁砧配方"。
	 *
	 * 为什么一条目标物品一条配方（而不是把整个标签塞进一个槽）：
	 * 铁砧分类的左右槽是**各自独立循环**的，把几十件物品塞一个槽、再塞几十个产物，
	 * 会出现"左边是剑、右边循环到镐"的错配。一条一个目标虽然条目多，但完全精确，
	 * 玩家按 U 看某件装备时也能直接聚焦到对应条目。
	 */
	private fun registerCoreEnchantRecipes(registration: IRecipeRegistration) {
		val factory = registration.vanillaRecipeFactory
		val recipes = mutableListOf<IJeiAnvilRecipe>()

		for (rule in AnvilEnchantHandler.rules) {
			val enchantmentId = Identifier.tryParse(rule.enchantmentId) ?: continue
			val media = ItemStacks.of(rule.mediaItemId)
			if (media.isEmpty) continue
			for (target in targetItems(rule)) {
				val base = ItemStack(target)
				val result = applyEnchantment(base, rule, enchantmentId)
				recipes += factory.createAnvilRecipe(
					listOf(base),
					listOf(media),
					listOf(result),
					ShenzhiMod.id("core_enchant/${rule.id}/${BuiltInRegistries.ITEM.getKey(target).path}"),
				)
			}
		}

		registration.addRecipes(RecipeTypes.ANVIL, recipes)
	}

	/**
	 * 四个核心的物品信息页：给出"能附什么、几级、固定花多少级"。
	 *
	 * 文本全部由运行时规则表生成，所以改了 `coreEnchant.*` 配置或新增规则后
	 * 这页内容会自动跟着变。
	 */
	private fun registerCoreInfoPages(registration: IRecipeRegistration) {
		val mediaItems = mapOf(
			CoreEnchantDefaults.COMBAT_CORE to ModItems.COMBAT_CORE,
			CoreEnchantDefaults.MINERAL_CORE to ModItems.MINERAL_CORE,
			CoreEnchantDefaults.AGRICULTURE_CORE to ModItems.AGRICULTURE_CORE,
			CoreEnchantDefaults.CREATION_CORE to ModItems.CREATION_CORE,
		)
		for ((mediaId, item) in mediaItems) {
			val rules = AnvilEnchantHandler.rules.filter { it.mediaItemId == mediaId }
			if (rules.isEmpty()) continue
			val lines = mutableListOf<Component>()
			for (rule in rules) {
				lines += Component.literal("• ")
					.append(Component.translatable(enchantmentKey(rule.enchantmentId)))
					.append(Component.literal(" " + roman(rule.grantedLevel)))
					.append(Component.literal(" → "))
					.append(targetText(rule))
			}
			lines += Component.translatable("jei.shenzhimod.info.anvil_cost", rules.first().costLevels.toString())
			registration.addItemStackInfo(ItemStack(item), *lines.toTypedArray())
		}
	}

	/** 规则命中的全部目标物品：物品 ID 直接取，标签展开成成员。 */
	private fun targetItems(rule: CoreEnchantRule): List<Item> {
		val items = LinkedHashSet<Item>()
		for (id in rule.targetItemIds) {
			val stack = ItemStacks.of(id)
			if (!stack.isEmpty) items.add(stack.item)
		}
		for (tagId in rule.targetTags) {
			val identifier = Identifier.tryParse(tagId) ?: continue
			val tag: TagKey<Item> = TagKey.create(Registries.ITEM, identifier)
			BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach { holder -> items.add(holder.value()) }
		}
		return items.toList()
	}

	/**
	 * 把附魔写进展示用的物品。
	 *
	 * 附魔是数据驱动的动态注册表，只能在**客户端已经进入世界**（JEI 注册配方时通常已经进世界）
	 * 才拿得到 `Holder`；拿不到时退回未附魔的物品——配方本身照样能看，只是产物没有附魔光效。
	 */
	private fun applyEnchantment(base: ItemStack, rule: CoreEnchantRule, enchantmentId: Identifier): ItemStack {
		val registry = Minecraft.getInstance().level?.registryAccess()?.lookupOrThrow(Registries.ENCHANTMENT)
			?: return base
		val key: ResourceKey<Enchantment> = ResourceKey.create(Registries.ENCHANTMENT, enchantmentId)
		val holder = registry.get(key).orElse(null) ?: return base

		val result = base.copy()
		val enchantments = ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(result))
		enchantments.set(holder, rule.grantedLevel)
		EnchantmentHelper.setEnchantments(result, enchantments.toImmutable())
		// 冷却附魔的图腾还会带"冷却组"组件，展示用的物品也照原样写一份，免得 tooltip 与实物不一致。
		if (rule.enchantmentId == CoreEnchantDefaults.ENCHANT_TOTEM_COOLDOWN) {
			TotemCooldownService.tagEnchantedTotem(result)
		}
		return result
	}

	/** 附魔名走原版/模组的语言键：`minecraft:sharpness` → `enchantment.minecraft.sharpness`。 */
	private fun enchantmentKey(enchantmentId: String): String = "enchantment." + enchantmentId.replace(':', '.')

	/** 目标类型的可读文本：标签走模组语言键，单个物品直接用物品名。 */
	private fun targetText(rule: CoreEnchantRule): Component {
		rule.targetItemIds.firstOrNull()?.let { id ->
			val stack = ItemStacks.of(id)
			if (!stack.isEmpty) return stack.hoverName
			return Component.literal(id)
		}
		val tag = rule.targetTags.firstOrNull() ?: return Component.empty()
		val key = "jei.shenzhimod.target." + tag.substringAfter(':').replace('/', '.')
		return Component.translatable(key)
	}

	/** 等级显示：1~20 用罗马数字（和物品 tooltip 一致），再往上直接写阿拉伯数字。 */
	private fun roman(level: Int): String = ROMAN.getOrNull(level - 1) ?: level.toString()

	private companion object {
		val ROMAN = listOf(
			"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
			"XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX",
		)
	}
}
