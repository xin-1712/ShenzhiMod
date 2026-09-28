package com.xin.shenzhimod.content.block

import com.mojang.serialization.MapCodec
import com.xin.shenzhimod.content.blockentity.TransmutationCoreBlockEntity
import com.xin.shenzhimod.registry.ModBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Containers
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

/**
 * 转化核心（模块 C）。
 *
 * 三槽容器（输入 / 催化剂 / 输出），支持手工与漏斗交互：
 * 上=输入、侧=催化剂、下=抽产物（需求 §4.3、难点 D10）。
 *
 * GUI（转化进度界面）属于 M3 的后半段工作，当前框架先保证：
 * 方块可放置、容器语义正确、进度可推进、产物可被漏斗抽走。
 */
class TransmutationCoreBlock(properties: Properties) : BaseEntityBlock(properties) {
	override fun codec(): MapCodec<out BaseEntityBlock> =
		simpleCodec { props -> TransmutationCoreBlock(props) }

	override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

	override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
		TransmutationCoreBlockEntity(pos, state)

	override fun <T : BlockEntity> getTicker(
		level: Level,
		state: BlockState,
		type: BlockEntityType<T>,
	): BlockEntityTicker<T>? {
		if (level.isClientSide || type !== ModBlockEntities.TRANSMUTATION_CORE) return null
		return BlockEntityTicker { serverLevel, blockPos, blockState, blockEntity ->
			(blockEntity as? TransmutationCoreBlockEntity)?.serverTick(serverLevel as ServerLevel, blockPos, blockState)
		}
	}

	override fun affectNeighborsAfterRemoval(state: BlockState, level: ServerLevel, pos: BlockPos, movedByPiston: Boolean) {
		val blockEntity = level.getBlockEntity(pos) as? TransmutationCoreBlockEntity
		blockEntity?.let { Containers.dropContents(level, pos, it) }
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston)
	}
}
