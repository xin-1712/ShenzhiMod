package com.xin.shenzhimod.content.block

import com.mojang.serialization.MapCodec
import com.xin.shenzhimod.content.blockentity.SyntheticDragonHeadBlockEntity
import com.xin.shenzhimod.registry.ModBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.core.Direction
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.BlockHitResult

/**
 * 合成龙首（模块 B）。
 *
 * 需求（§4.2）：
 * - 有红石信号时进入"充能"状态，吸收 3×3×3 内的压缩态；
 * - 非充能时把账本里的物品全部吐回；
 * - 右槽右键可查看已吸收清单与进度；
 * - 方块被破坏时掉落方块本身 + 内部全部物品（在 [affectNeighborsAfterRemoval] 里实现）。
 */
class SyntheticDragonHeadBlock(properties: Properties) : BaseEntityBlock(properties) {
	override fun codec(): MapCodec<out BaseEntityBlock> =
		simpleCodec { props -> SyntheticDragonHeadBlock(props) }

	// 头部外观由方块实体渲染器画（和原版龙首同一个模型），方块本身不画几何体，
	// 所以这里沿用 BaseEntityBlock 的 INVISIBLE（原版头颅方块也是这么做的）。
	override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE

	override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
		builder.add(POWERED, FACING, ACTIVE)
	}

	override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
		defaultBlockState()
			// 注意：要查**新方块所在位置**的信号，不能查被点击的那个方块——
			// 否则在充能装置旁边放下时会先被判定成"已充能"（闪一下亮色/金色），
			// 下一 tick 邻居更新才纠正回来（2026-10-02 反馈 #2）。
			.setValue(POWERED, context.level.hasNeighborSignal(context.clickedPos.relative(context.clickedFace)))
			// 头朝玩家（放下去正对着自己，方便当机器正面看）
			.setValue(FACING, context.horizontalDirection.opposite)

	override fun neighborChanged(
		state: BlockState,
		level: Level,
		pos: BlockPos,
		block: Block,
		orientation: Orientation?,
		movedByPiston: Boolean,
	) {
		super.neighborChanged(state, level, pos, block, orientation, movedByPiston)
		if (level.isClientSide) return
		val powered = level.hasNeighborSignal(pos)
		if (state.getValue(POWERED) != powered) {
			level.setBlockAndUpdate(pos, state.setValue(POWERED, powered))
		}
	}

	override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
		SyntheticDragonHeadBlockEntity(pos, state)

	override fun <T : BlockEntity> getTicker(
		level: Level,
		state: BlockState,
		type: BlockEntityType<T>,
	): BlockEntityTicker<T>? {
		if (level.isClientSide || type !== ModBlockEntities.SYNTHETIC_DRAGON_HEAD) return null
		return BlockEntityTicker { serverLevel, blockPos, blockState, blockEntity ->
			(blockEntity as? SyntheticDragonHeadBlockEntity)?.serverTick(serverLevel as ServerLevel, blockPos, blockState)
		}
	}

	override fun useWithoutItem(
		state: BlockState,
		level: Level,
		pos: BlockPos,
		player: Player,
		hitResult: BlockHitResult,
	): InteractionResult {
		if (level.isClientSide) return InteractionResult.SUCCESS
		val blockEntity = level.getBlockEntity(pos) as? SyntheticDragonHeadBlockEntity ?: return InteractionResult.PASS
		player.sendSystemMessage(blockEntity.describeStatus())
		return InteractionResult.SUCCESS
	}

	// 内容物掉落由方块实体的 preRemoveSideEffects() 负责：
	// 放在这里的 affectNeighborsAfterRemoval 实测太晚，那时拿不到 BlockEntity，账本会凭空消失。

	companion object {
		// @JvmField：渲染器是 Java（头颅模型包名含 Kotlin 关键字 object，Kotlin 无法 import），
		// 这样 Java 侧可以直接写 `SyntheticDragonHeadBlock.FACING`。
		@JvmField
		val POWERED: BooleanProperty = BlockStateProperties.POWERED

		/** 朝向（四个水平方向）：只影响渲染，决定龙头看向哪边。 */
		@JvmField
		val FACING: EnumProperty<Direction> = BlockStateProperties.HORIZONTAL_FACING

		/** 是否正在合成（账本已满足某个核心配方、正在倒计时）：驱动"下颚动着 + 眼睛发亮"的表现。 */
		@JvmField
		val ACTIVE: BooleanProperty = BooleanProperty.create("active")
	}
}
