package com.xin.shenzhimod.compat.jei

import com.xin.shenzhimod.registry.ModItems
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.category.AbstractRecipeCategory
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.util.Locale

/**
 * JEI 配方分类（三个自定义类别）。
 *
 * 每个类别都继承 JEI 的 [AbstractRecipeCategory]：标题、图标、宽高由基类托管，
 * 我们只实现"怎么把一条配方摆到界面上"。
 *
 * 布局刻意保持"输入在左、箭头在中间、产物在右"的通用直觉；
 * 数量用文字标注（而不是把 `ItemStack` 的 count 设成 3456），
 * 这样既不会触发堆叠上限的显示怪相，也不会被其它模组的渲染逻辑误解。
 */

/** 模块 A：铁砧压缩。`基础物品 × N → 压缩态`。 */
class CompressionJeiCategory(guiHelper: IGuiHelper) : AbstractRecipeCategory<CompressionJeiRecipe>(
	TYPE,
	Component.translatable("jei.shenzhimod.category.compression"),
	guiHelper.createDrawableItemStack(ShenzhiJeiRecipeTypes.compressionIcon()),
	WIDTH,
	HEIGHT,
) {
	override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: CompressionJeiRecipe, focuses: IFocusGroup) {
		builder.addInputSlot(SLOT_X, SLOT_Y).add(recipe.source)
		builder.addOutputSlot(OUTPUT_X, SLOT_Y).add(recipe.output)
	}

	override fun createRecipeExtras(builder: IRecipeExtrasBuilder, recipe: CompressionJeiRecipe, focuses: IFocusGroup) {
		// "×3456"：直接告诉玩家要多少个，比一个看不到的小数字清楚。
		builder.addText(Component.literal("×${recipe.required}"), 1, 2)
		builder.addAnimatedRecipeArrow(ARROW_TICKS).setPosition(ARROW_X, ARROW_Y)
	}

	companion object {
		val TYPE = ShenzhiJeiRecipeTypes.COMPRESSION
		private const val SLOT_X = 1
		private const val SLOT_Y = 14
		private const val ARROW_X = 30
		private const val ARROW_Y = 15
		private const val OUTPUT_X = 62
		private const val ARROW_TICKS = 200
		const val WIDTH = 84
		const val HEIGHT = 42
	}
}

/** 模块 B：合成龙首的核心合成。一排压缩态（每种 1 个）→ 一个核心。 */
class CoreAssemblyJeiCategory(guiHelper: IGuiHelper) : AbstractRecipeCategory<CoreAssemblyJeiRecipe>(
	TYPE,
	Component.translatable("jei.shenzhimod.category.core_assembly"),
	guiHelper.createDrawableItemStack(ItemStack(ModItems.SYNTHETIC_DRAGON_HEAD)),
	WIDTH,
	HEIGHT,
) {
	override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: CoreAssemblyJeiRecipe, focuses: IFocusGroup) {
		// 材料最多 14 种（农业核心），按 4 列排布刚好 4 行放得下。
		recipe.ingredients.forEachIndexed { index, stack ->
			val column = index % COLUMNS
			val row = index / COLUMNS
			builder.addInputSlot(GRID_X + column * CELL, GRID_Y + row * CELL).add(stack)
		}
		builder.addOutputSlot(OUTPUT_X, OUTPUT_Y).add(recipe.output)
	}

	override fun createRecipeExtras(builder: IRecipeExtrasBuilder, recipe: CoreAssemblyJeiRecipe, focuses: IFocusGroup) {
		builder.addAnimatedRecipeArrow(ARROW_TICKS).setPosition(ARROW_X, ARROW_Y)
	}

	companion object {
		val TYPE = ShenzhiJeiRecipeTypes.CORE_ASSEMBLY
		private const val COLUMNS = 4
		private const val CELL = 18
		private const val GRID_X = 1
		private const val GRID_Y = 1
		private const val ARROW_X = 75
		private const val ARROW_Y = 28
		private const val OUTPUT_X = 98
		private const val OUTPUT_Y = 28
		private const val ARROW_TICKS = 200
		const val WIDTH = 118
		const val HEIGHT = 74
	}
}

/** 模块 C：转化核心。`输入 × N + 催化剂 × M → 产物`，附带耗时。 */
class TransmutationJeiCategory(guiHelper: IGuiHelper) : AbstractRecipeCategory<TransmutationJeiRecipe>(
	TYPE,
	Component.translatable("jei.shenzhimod.category.transmutation"),
	guiHelper.createDrawableItemStack(ItemStack(ModItems.TRANSMUTATION_CORE)),
	WIDTH,
	HEIGHT,
) {
	override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: TransmutationJeiRecipe, focuses: IFocusGroup) {
		builder.addInputSlot(INPUT_X, SLOT_Y).add(recipe.input)
		builder.addInputSlot(CATALYST_X, SLOT_Y).add(recipe.catalyst)
		builder.addOutputSlot(OUTPUT_X, SLOT_Y).add(recipe.output)
	}

	override fun createRecipeExtras(builder: IRecipeExtrasBuilder, recipe: TransmutationJeiRecipe, focuses: IFocusGroup) {
		builder.addText(Component.literal("×${recipe.inputCount}"), 2, 2)
		builder.addText(Component.literal("×${recipe.catalystCount}"), CATALYST_X + 1, 2)
		builder.addRecipePlusSign().setPosition(PLUS_X, PLUS_Y)
		builder.addAnimatedRecipeArrow(ARROW_TICKS).setPosition(ARROW_X, ARROW_Y)
		// 耗时用"秒"更直观（数据表里存的是 tick）。
		val seconds = String.format(Locale.ROOT, "%.1fs", recipe.durationTicks / 20.0)
		builder.addText(Component.literal(seconds), 2, DURATION_Y)
	}

	companion object {
		val TYPE = ShenzhiJeiRecipeTypes.TRANSMUTATION
		private const val INPUT_X = 1
		private const val CATALYST_X = 41
		private const val SLOT_Y = 14
		private const val PLUS_X = 21
		private const val PLUS_Y = 15
		private const val ARROW_X = 63
		private const val ARROW_Y = 15
		private const val OUTPUT_X = 85
		private const val DURATION_Y = 34
		private const val ARROW_TICKS = 200
		const val WIDTH = 106
		const val HEIGHT = 46
	}
}
