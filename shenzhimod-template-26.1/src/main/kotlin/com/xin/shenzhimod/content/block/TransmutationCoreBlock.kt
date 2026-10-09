package com.xin.shenzhimod.content.block

import com.mojang.serialization.MapCodec
import com.xin.shenzhimod.content.blockentity.TransmutationCoreBlockEntity
import com.xin.shenzhimod.registry.ModBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
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
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.BlockHitResult

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
	init {
		// 默认静止态；工作时由方块实体把 LIT 打开（熔炉那套做法，客户端靠方块状态自动同步）
		registerDefaultState(stateDefinition.any().setValue(LIT, false))
	}

	override fun codec(): MapCodec<out BaseEntityBlock> =
		simpleCodec { props -> TransmutationCoreBlock(props) }

	override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
		builder.add(LIT)
	}

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

	// 三个槽位的掉落交给原版机制：BlockEntity#preRemoveSideEffects 会对实现了 Container 的方块实体
	// 自动调用 Containers.dropContents（本类是 WorldlyContainer，所以无需自己写，写了反而会掉两次）。

	/** 空手右键打开界面（服务端权威：只有服务端真正开菜单）。 */
	override fun useWithoutItem(
		state: BlockState,
		level: Level,
		pos: BlockPos,
		player: Player,
		hitResult: BlockHitResult,
	): InteractionResult {
		if (level.isClientSide) return InteractionResult.SUCCESS
		val blockEntity = level.getBlockEntity(pos) as? TransmutationCoreBlockEntity ?: return InteractionResult.PASS
		val serverPlayer = player as? ServerPlayer ?: return InteractionResult.PASS
		serverPlayer.openMenu(blockEntity)
		return InteractionResult.SUCCESS
	}

	companion object {
		/** 是否正在转化（输入与催化剂都匹配、进度在走）。 */
		val LIT: BooleanProperty = BooleanProperty.create("lit")
	}
}
