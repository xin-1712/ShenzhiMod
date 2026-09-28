package com.xin.shenzhimod.content.block

import com.mojang.serialization.MapCodec
import com.xin.shenzhimod.content.blockentity.UltimateAltarBlockEntity
import com.xin.shenzhimod.registry.ModBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.BlockHitResult

/**
 * 终极祭坛（模块 E）。
 *
 * 需求（§4.5）：放在 3×3 下界合金块底座上方才能激活；
 * 把三核心扔到底座范围内、Shift+右键启动 30 秒融合流程。
 *
 * 底座校验放在方块实体里每 tick 做一次廉价检查（先比坐标再比方块），
 * 状态机本身是无 Minecraft 依赖的 [com.xin.shenzhimod.machine.AltarFusionState]。
 */
class UltimateAltarBlock(properties: Properties) : BaseEntityBlock(properties) {
	override fun codec(): MapCodec<out BaseEntityBlock> =
		simpleCodec { props -> UltimateAltarBlock(props) }

	override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

	override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
		UltimateAltarBlockEntity(pos, state)

	override fun <T : BlockEntity> getTicker(
		level: Level,
		state: BlockState,
		type: BlockEntityType<T>,
	): BlockEntityTicker<T>? {
		if (level.isClientSide || type !== ModBlockEntities.ULTIMATE_ALTAR) return null
		return BlockEntityTicker { serverLevel, blockPos, blockState, blockEntity ->
			(blockEntity as? UltimateAltarBlockEntity)?.serverTick(serverLevel as ServerLevel, blockPos, blockState)
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
		val blockEntity = level.getBlockEntity(pos) as? UltimateAltarBlockEntity ?: return InteractionResult.PASS
		return if (player.isShiftKeyDown) {
			blockEntity.tryStart(player)
			InteractionResult.SUCCESS
		} else {
			player.sendSystemMessage(blockEntity.describeStatus())
			InteractionResult.SUCCESS
		}
	}

	override fun neighborChanged(
		state: BlockState,
		level: Level,
		pos: BlockPos,
		block: Block,
		orientation: Orientation?,
		movedByPiston: Boolean,
	) {
		super.neighborChanged(state, level, pos, block, orientation, movedByPiston)
		(level.getBlockEntity(pos) as? UltimateAltarBlockEntity)?.markBaseDirty()
	}
}
