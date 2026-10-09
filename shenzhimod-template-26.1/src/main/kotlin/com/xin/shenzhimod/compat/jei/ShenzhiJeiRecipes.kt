package com.xin.shenzhimod.compat.jei

import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.data.ShenzhiTables
import com.xin.shenzhimod.registry.ModItems
import com.xin.shenzhimod.util.ItemStacks
import mezz.jei.api.recipe.types.IRecipeType
import net.minecraft.world.item.ItemStack

/**
 * JEI 展示用的"配方"数据模型。
 *
 * 本模组的四类配方里没有一个是原版 `Recipe` 类型（压缩 / 核心合成 / 转化是自定义数据表，
 * 核心附魔是铁砧交互），所以 JEI 那边不能走原版配方管线，只能：
 * - 自己定义 `RecipeType` + 分类，把数据表翻译成"输入 → 输出"的展示模型；
 * - 核心附魔则复用 JEI **自带的铁砧分类**（左槽 + 右槽 → 产物），玩家一看就懂。
 *
 * 这些类只在装了 JEI 时才会被加载（入口点由 JEI 自己调用），没装 JEI 的玩家完全不受影响。
 */
data class CompressionJeiRecipe(
	/** 被压缩的原版物品。 */
	val source: ItemStack,
	/** 需要多少个。 */
	val required: Int,
	/** 压缩态产物。 */
	val output: ItemStack,
)

data class CoreAssemblyJeiRecipe(
	/** 合成出的核心。 */
	val output: ItemStack,
	/** 需要的全部压缩态（每种 1 个）。 */
	val ingredients: List<ItemStack>,
)

data class TransmutationJeiRecipe(
	val input: ItemStack,
	val inputCount: Int,
	val catalyst: ItemStack,
	val catalystCount: Int,
	val output: ItemStack,
	val durationTicks: Int,
)

object ShenzhiJeiRecipeTypes {
	// 用 `IRecipeType.create(...)` 而不是已被标记为过时的 `RecipeType.create(...)`：
	// 前者是 JEI 29 起推荐的入口，类型也更通用（分类只要求 `IRecipeType`）。
	val COMPRESSION: IRecipeType<CompressionJeiRecipe> =
		IRecipeType.create(ShenzhiMod.MOD_ID, "compression", CompressionJeiRecipe::class.java)

	val CORE_ASSEMBLY: IRecipeType<CoreAssemblyJeiRecipe> =
		IRecipeType.create(ShenzhiMod.MOD_ID, "core_assembly", CoreAssemblyJeiRecipe::class.java)

	val TRANSMUTATION: IRecipeType<TransmutationJeiRecipe> =
		IRecipeType.create(ShenzhiMod.MOD_ID, "transmutation", TransmutationJeiRecipe::class.java)

	/**
	 * 把运行时的数据表翻译成 JEI 展示模型。
	 *
	 * 直接读 [ShenzhiTables]（而不是代码里的默认表），所以服务器用数据包改了配方，
	 * JEI 里显示的也是改过之后的内容（客户端 `/reload` 或重进世界后刷新）。
	 */
	fun compressionRecipes(): List<CompressionJeiRecipe> = ShenzhiTables.compression.entries.mapNotNull { entry ->
		val source = ItemStacks.of(entry.sourceItemId)
		val output = ItemStacks.ofMod(entry.outputPath)
		if (source.isEmpty || output.isEmpty) null else CompressionJeiRecipe(source, entry.count, output)
	}

	fun coreAssemblyRecipes(): List<CoreAssemblyJeiRecipe> = ShenzhiTables.assembly.mapNotNull { recipe ->
		val output = ItemStacks.of(recipe.outputItemId)
		if (output.isEmpty) return@mapNotNull null
		val ingredients = recipe.requirements.map { ItemStacks.ofMod(it.ledgerKey) }
		if (ingredients.any { it.isEmpty }) null else CoreAssemblyJeiRecipe(output, ingredients)
	}

	fun transmutationRecipes(): List<TransmutationJeiRecipe> = ShenzhiTables.transmutation.entries.mapNotNull { entry ->
		val input = ItemStacks.of(entry.inputItemId)
		val catalyst = ItemStacks.of(entry.catalystItemId)
		val output = ItemStacks.of(entry.outputItemId)
		if (input.isEmpty || catalyst.isEmpty || output.isEmpty) {
			null
		} else {
			TransmutationJeiRecipe(input, entry.inputCount, catalyst, entry.catalystCount, output, entry.durationTicks)
		}
	}

	/** 压缩分类的图标：随便取一件压缩态；万一压缩表被数据包清空，退回模组主图标物。 */
	fun compressionIcon(): ItemStack =
		ModItems.COMPRESSED.values.firstOrNull()?.let { ItemStack(it) } ?: ItemStack(ModItems.CREATION_CORE)
}
