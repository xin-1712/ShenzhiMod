package com.xin.shenzhimod.content.item

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.TooltipDisplay
import java.util.function.Consumer

/**
 * 压缩态物品（模块 A）。
 *
 * 需求（§4.1）：
 * - 堆叠 64、名称"压缩+基础名"、Tooltip 写"由 N 个 X 压缩而成"；
 * - **不能解压**，它不是存储形式，只是合成物。
 *
 * 这里只带"数据"（被压缩物 ID + 需求数量），显示名与贴图都由数据生成产出，
 * 因此新增压缩态不需要改代码。
 */
class CompressedItem(
	properties: Properties,
	val sourceItemId: String,
	val requiredCount: Int,
) : Item(properties) {

	@Suppress("OVERRIDE_DEPRECATION")
	override fun appendHoverText(
		itemStack: ItemStack,
		context: TooltipContext,
		display: TooltipDisplay,
		builder: Consumer<Component>,
		tooltipFlag: TooltipFlag,
	) {
		builder.accept(Component.translatable("tooltip.shenzhimod.compressed", requiredCount, sourceDisplayName()))
	}

	private fun sourceDisplayName(): Component {
		val identifier = Identifier.tryParse(sourceItemId) ?: return Component.literal(sourceItemId)
		val item = BuiltInRegistries.ITEM.getValue(identifier)
		val stack = ItemStack(item)
		return if (stack.isEmpty) Component.literal(sourceItemId) else stack.hoverName
	}

	companion object {
		/** 压缩态的基础属性：堆叠 64 + 火焰抗性（避免被岩浆烧掉，符合"合成物不该被环境吞掉"的直觉）。 */
		fun properties(): Properties = Properties()
			.stacksTo(64)
			.fireResistant()
			.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
	}
}
