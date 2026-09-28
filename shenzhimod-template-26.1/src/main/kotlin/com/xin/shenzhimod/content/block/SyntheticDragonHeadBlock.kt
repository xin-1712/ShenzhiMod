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

	override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

	override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
		builder.add(POWERED)
	}

	override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
		defaultBlockState().setValue(POWERED, context.level.hasNeighborSignal(context.clickedPos))

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

	override fun affectNeighborsAfterRemoval(state: BlockState, level: ServerLevel, pos: BlockPos, movedByPiston: Boolean) {
		val blockEntity = level.getBlockEntity(pos) as? SyntheticDragonHeadBlockEntity
		blockEntity?.dropStoredContents()
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston)
	}

	companion object {
		val POWERED: BooleanProperty = BlockStateProperties.POWERED
	}
}
