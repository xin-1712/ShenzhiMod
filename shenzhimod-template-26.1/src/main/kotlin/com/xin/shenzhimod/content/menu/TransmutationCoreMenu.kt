package com.xin.shenzhimod.content.menu

import com.xin.shenzhimod.content.blockentity.TransmutationCoreBlockEntity
import com.xin.shenzhimod.content.blockentity.TransmutationCoreBlockEntity.Companion.SLOT_CATALYST
import com.xin.shenzhimod.content.blockentity.TransmutationCoreBlockEntity.Companion.SLOT_COUNT
import com.xin.shenzhimod.content.blockentity.TransmutationCoreBlockEntity.Companion.SLOT_INPUT
import com.xin.shenzhimod.content.blockentity.TransmutationCoreBlockEntity.Companion.SLOT_OUTPUT
import com.xin.shenzhimod.registry.ModMenus
import net.minecraft.core.BlockPos
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/**
 * 转化核心的容器界面（模块 C 的 GUI）。
 *
 * 布局：左 = 输入、箭头**上方** = 催化剂、右 = 产物，箭头即 10 秒进度条。
 *
 * 客户端打开时方块实体可能还没同步到数据，因此：
 * - 找不到方块实体时用 [SimpleContainer] 占位（槽位内容由原版菜单同步机制填充）；
 * - 进度用 [ContainerData] 同步，服务端来自方块实体，客户端是 [SimpleContainerData]。
 */
class TransmutationCoreMenu(
	containerId: Int,
	playerInventory: Inventory,
	private val corePos: BlockPos,
) : AbstractContainerMenu(ModMenus.TRANSMUTATION_CORE, containerId) {

	/**
	 * ⚠️ **只有服务端才去绑定真实方块实体**。
	 *
	 * 客户端如果也去读方块实体，就会读到"客户端那份永远不会更新的进度"（方块实体没有增量同步），
	 * 界面上的箭头就会一直是 0——这是实测踩到的坑。
	 * 原版熔炉也是同样处理：客户端用占位容器 + 由包同步的 [SimpleContainerData]，
	 * 槽位里的物品由原版菜单的槽位同步机制填充。
	 */
	private val core: TransmutationCoreBlockEntity? =
		if (playerInventory.player.level().isClientSide) {
			null
		} else {
			playerInventory.player.level().getBlockEntity(corePos) as? TransmutationCoreBlockEntity
		}

	private val container: Container = core ?: SimpleContainer(SLOT_COUNT)
	private val data: ContainerData = core?.createContainerData() ?: SimpleContainerData(DATA_COUNT)

	init {
		addSlot(Slot(container, SLOT_INPUT, INPUT_X, INPUT_Y))
		addSlot(Slot(container, SLOT_CATALYST, CATALYST_X, CATALYST_Y))
		addSlot(Slot(container, SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y))

		// 玩家背包 3×9
		for (row in 0 until 3) {
			for (column in 0 until 9) {
				addSlot(Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18))
			}
		}
		// 快捷栏
		for (column in 0 until 9) {
			addSlot(Slot(playerInventory, column, 8 + column * 18, 142))
		}

		addDataSlots(data)
	}

	/** 0.0~1.0 的进度，供界面画箭头。 */
	fun progressFraction(): Float {
		val duration = data.get(DATA_DURATION)
		if (duration <= 0) return 0f
		return (data.get(DATA_PROGRESS).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
	}

	/** 输入与催化剂是否都已就位（界面据此决定箭头是否高亮）。 */
	fun isRunning(): Boolean = data.get(DATA_PROGRESS) > 0

	override fun stillValid(player: Player): Boolean =
		core?.let { net.minecraft.world.Container.stillValidBlockEntity(it, player) } ?: true

	override fun quickMoveStack(player: Player, index: Int): ItemStack {
		val slot = slots.getOrNull(index) ?: return ItemStack.EMPTY
		if (!slot.hasItem()) return ItemStack.EMPTY

		val stack = slot.item
		val original = stack.copy()
		val moved = when (index) {
			SLOT_INPUT, SLOT_CATALYST, SLOT_OUTPUT -> moveItemStackTo(stack, PLAYER_SLOTS_START, slots.size, true)
			else -> moveItemStackTo(stack, SLOT_INPUT, SLOT_OUTPUT + 1, false)
		}
		if (!moved) return ItemStack.EMPTY
		if (stack.isEmpty) slot.set(ItemStack.EMPTY) else slot.setChanged()
		return original
	}

	companion object {
		/** 槽位坐标（与 176×166 的熔炉式背景对齐）。 */
		const val INPUT_X = 56
		const val INPUT_Y = 17

		/** 箭头上方：催化剂。 */
		const val CATALYST_X = 79
		const val CATALYST_Y = 16

		const val OUTPUT_X = 116
		const val OUTPUT_Y = 35

		/** 箭头进度条的绘制位置与尺寸（与熔炉的 burn_progress 精灵一致）。 */
		const val ARROW_X = 79
		const val ARROW_Y = 34
		const val ARROW_WIDTH = 24
		const val ARROW_HEIGHT = 16

		private const val PLAYER_SLOTS_START = SLOT_COUNT

		private const val DATA_PROGRESS = 0
		private const val DATA_DURATION = 1
		private const val DATA_COUNT = 2
	}
}
