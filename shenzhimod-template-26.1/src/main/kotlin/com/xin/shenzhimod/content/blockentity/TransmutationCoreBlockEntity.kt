package com.xin.shenzhimod.content.blockentity

import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.content.block.TransmutationCoreBlock
import com.xin.shenzhimod.content.menu.TransmutationCoreMenu
import com.xin.shenzhimod.data.ShenzhiTables
import com.xin.shenzhimod.machine.TransmutationEntry
import com.xin.shenzhimod.machine.TransmutationProcess
import com.xin.shenzhimod.registry.ModBlockEntities
import com.xin.shenzhimod.util.ItemStacks
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.NonNullList
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/**
 * 转化核心的方块实体（模块 C）。
 *
 * 三槽容器 + 漏斗语义（难点 D10）：
 * - 上（UP）→ 输入槽
 * - 侧（水平）→ 催化剂槽
 * - 下（DOWN）→ 抽输出槽
 *
 * 进度用整数 tick 计（[TransmutationProcess]），配方不匹配即归零；
 * 产物只在"输入与催化剂都够"时结算，保证不复制不丢失。
 */
class TransmutationCoreBlockEntity(pos: BlockPos, state: BlockState) :
	BlockEntity(ModBlockEntities.TRANSMUTATION_CORE, pos, state),
	WorldlyContainer,
	ExtendedMenuProvider<BlockPos> {

	private var items: NonNullList<ItemStack> = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY)

	/** 纯逻辑状态机，不含任何 Minecraft 类型。 */
	val process: TransmutationProcess = TransmutationProcess()

	fun serverTick(level: ServerLevel, pos: BlockPos, state: BlockState) {
		val recipe = currentRecipe()
		// 工作状态外观：输入与催化剂都匹配就把 lit 打开（方块状态自带客户端同步，
		// 因此不需要额外发包，也不违反"状态服务端算"的约定）。
		val lit = recipe != null
		if (state.getValue(TransmutationCoreBlock.LIT) != lit) {
			level.setBlock(pos, state.setValue(TransmutationCoreBlock.LIT, lit), Block.UPDATE_ALL)
		}
		if (process.tick(recipe) && recipe != null) {
			finishCraft(recipe)
		}
	}

	/** 当前输入 + 催化剂能匹配到的配方；数量不足或催化剂不对返回 null。 */
	fun currentRecipe(): TransmutationEntry? {
		val input = items[SLOT_INPUT]
		if (input.isEmpty) return null
		val inputId = ItemStacks.idOf(input) ?: return null
		val recipe = ShenzhiTables.transmutation.entryForInput(inputId) ?: return null
		if (input.count < recipe.inputCount) return null

		val catalyst = items[SLOT_CATALYST]
		if (catalyst.isEmpty) return null
		if (ItemStacks.idOf(catalyst) != recipe.catalystItemId) return null
		if (catalyst.count < recipe.catalystCount) return null
		return recipe
	}

	private fun finishCraft(recipe: TransmutationEntry) {
		val output = ItemStacks.of(recipe.outputItemId, recipe.outputCount)
		if (output.isEmpty) {
			ShenzhiMod.logger.warn("转化配方 {} 的产物 {} 未注册，已跳过", recipe.id, recipe.outputItemId)
			process.complete()
			return
		}
		val existing = items[SLOT_OUTPUT]
		if (!existing.isEmpty && !ItemStack.isSameItemSameComponents(existing, output)) {
			// 输出槽被别的物品占着：停在完成态等待玩家/漏斗腾空，避免覆盖。
			process.restore(recipe.id, recipe.durationTicks)
			return
		}
		// ⚠️ 输出槽为空时 `ItemStack.EMPTY.maxStackSize` 是 **1**（不是 64），
		// 曾经因此把"产出 4 个"的配方永久判成"放不下"：进度条能走完，但产物不写、输入也不扣。
		// 所以空槽要按**产物自己**的最大堆叠数算空间。
		val space = (if (existing.isEmpty) output.maxStackSize else existing.maxStackSize) - existing.count
		if (space < recipe.outputCount) {
			process.restore(recipe.id, recipe.durationTicks)
			return
		}

		// 先扣输入与催化剂，再写产物——顺序固定，异常时不会凭空多出产物。
		items[SLOT_INPUT].shrink(recipe.inputCount)
		items[SLOT_CATALYST].shrink(recipe.catalystCount)
		if (items[SLOT_INPUT].isEmpty) items[SLOT_INPUT] = ItemStack.EMPTY
		if (items[SLOT_CATALYST].isEmpty) items[SLOT_CATALYST] = ItemStack.EMPTY
		items[SLOT_OUTPUT] = if (existing.isEmpty) output else existing.apply { grow(recipe.outputCount) }

		process.complete()
		setChanged()
	}

	// ---- Container ----

	override fun getContainerSize(): Int = SLOT_COUNT

	override fun isEmpty(): Boolean = items.all { it.isEmpty }

	override fun getItem(slot: Int): ItemStack = items[slot]

	override fun removeItem(slot: Int, count: Int): ItemStack {
		val removed = ContainerHelper.removeItem(items, slot, count)
		if (!removed.isEmpty) setChanged()
		return removed
	}

	override fun removeItemNoUpdate(slot: Int): ItemStack = ContainerHelper.takeItem(items, slot)

	override fun setItem(slot: Int, itemStack: ItemStack) {
		items[slot] = itemStack
		if (itemStack.count > maxStackSize) itemStack.count = maxStackSize
		setChanged()
	}

	override fun stillValid(player: Player): Boolean = Container.stillValidBlockEntity(this, player)

	override fun clearContent() {
		items.clear()
		setChanged()
	}

	/** 催化剂槽只接受"配方里出现过的催化剂"；输出槽谁都不能放。 */
	override fun canPlaceItem(slot: Int, itemStack: ItemStack): Boolean = when (slot) {
		SLOT_OUTPUT -> false
		SLOT_CATALYST -> isCatalyst(itemStack)
		else -> true
	}

	/** 该物品是不是本模组转化配方认可的催化剂。 */
	fun isCatalyst(itemStack: ItemStack): Boolean {
		val id = ItemStacks.idOf(itemStack) ?: return false
		return ShenzhiTables.transmutation.isCatalyst(id)
	}

	// ---- WorldlyContainer（漏斗语义） ----

	override fun getSlotsForFace(direction: Direction): IntArray = when (direction) {
		Direction.UP -> intArrayOf(SLOT_INPUT)
		Direction.DOWN -> intArrayOf(SLOT_OUTPUT)
		else -> intArrayOf(SLOT_CATALYST)
	}

	override fun canPlaceItemThroughFace(slot: Int, itemStack: ItemStack, direction: Direction?): Boolean {
		if (!ShenzhiMod.config.transmutation.hopperInteractionEnabled) return false
		return when (direction) {
			Direction.UP -> slot == SLOT_INPUT
			Direction.DOWN -> false
			null -> slot != SLOT_OUTPUT && (slot != SLOT_CATALYST || isCatalyst(itemStack))
			else -> slot == SLOT_CATALYST && isCatalyst(itemStack)
		}
	}

	override fun canTakeItemThroughFace(slot: Int, itemStack: ItemStack, direction: Direction): Boolean =
		direction == Direction.DOWN && slot == SLOT_OUTPUT

	// ---- 界面（GUI）----

	/**
	 * 菜单用的进度数据：0 = 已运行 tick，1 = 本次配方总时长。
	 * 客户端只读；服务端每 tick 由原版菜单同步机制推给客户端。
	 */
	fun createContainerData(): ContainerData = object : ContainerData {
		override fun get(index: Int): Int = when (index) {
			0 -> process.progressTicks
			1 -> currentRecipe()?.durationTicks ?: ShenzhiMod.config.transmutation.defaultDurationTicks
			else -> 0
		}

		override fun set(index: Int, value: Int) = Unit

		override fun getCount(): Int = 2
	}

	override fun getDisplayName(): Component = Component.translatable("block.${ShenzhiMod.MOD_ID}.transmutation_core")

	override fun createMenu(containerId: Int, inventory: Inventory, player: Player): AbstractContainerMenu =
		TransmutationCoreMenu(containerId, inventory, worldPosition)

	override fun getScreenOpeningData(player: ServerPlayer): BlockPos = worldPosition

	// ---- 持久化 ----

	override fun saveAdditional(output: ValueOutput) {
		super.saveAdditional(output)
		ContainerHelper.saveAllItems(output, items)
		output.putInt("Progress", process.progressTicks)
		process.activeRecipeId?.let { output.putString("Recipe", it) }
	}

	override fun loadAdditional(input: ValueInput) {
		super.loadAdditional(input)
		items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY)
		ContainerHelper.loadAllItems(input, items)
		val recipe = input.getStringOr("Recipe", "")
		process.restore(recipe.ifEmpty { null }, input.getIntOr("Progress", 0))
	}

	companion object {
		const val SLOT_INPUT = 0
		const val SLOT_CATALYST = 1
		const val SLOT_OUTPUT = 2
		const val SLOT_COUNT = 3
	}
}
