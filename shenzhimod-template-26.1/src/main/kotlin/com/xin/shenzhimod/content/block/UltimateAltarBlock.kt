package com.xin.shenzhimod.content.block

import com.mojang.serialization.MapCodec
import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.content.blockentity.UltimateAltarBlockEntity
import com.xin.shenzhimod.registry.ModBlockEntities
import com.xin.shenzhimod.util.ItemStacks
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.StringRepresentable
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.EnumProperty
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

	/**
	 * 视觉状态：底座不正确 / 空转 / 三核就位待启动 / 融合中。
	 *
	 * 服务端算，方块状态自动同步给客户端：玩家不点开也能一眼看出祭坛为什么没反应。
	 */
	enum class AltarState(private val id: String) : StringRepresentable {
		IDLE("idle"),
		INVALID("invalid"),
		READY("ready"),
		ACTIVE("active"),
		;

		override fun getSerializedName(): String = id
	}

	init {
		registerDefaultState(stateDefinition.any().setValue(STATE, AltarState.IDLE))
	}

	override fun codec(): MapCodec<out BaseEntityBlock> =
		simpleCodec { props -> UltimateAltarBlock(props) }

	override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
		builder.add(STATE)
	}

	override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

	/**
	 * 放下瞬间就判定一次底座：否则方块会先以默认的静止态出现，下一 tick 才被方块实体纠正成红环
	 * （和合成龙首"放下先闪一下充能色"是同一类问题）。
	 *
	 * 这里用 [setPlacedBy] 而不是 `getStateForPlacement`，因为它的 [pos] 就是方块最终所在位置，
	 * 不依赖放置上下文里"点击位置 / 相对位置"的语义；两次 setBlock 在同一 tick 内完成，
	 * 客户端只会看到最终状态。
	 */
	override fun setPlacedBy(
		level: Level,
		pos: BlockPos,
		state: BlockState,
		placer: LivingEntity?,
		stack: ItemStack,
	) {
		super.setPlacedBy(level, pos, state, placer, stack)
		// 登记"谁放的祭坛"，成就"地基打得牢"会优先发给他（底座可以晚一点再摆好）。
		if (placer is Player) {
			(level.getBlockEntity(pos) as? UltimateAltarBlockEntity)?.rememberPlacer(placer)
		}
		val visual = if (isBaseComplete(level, pos)) AltarState.IDLE else AltarState.INVALID
		if (state.getValue(STATE) !== visual) {
			level.setBlock(pos, state.setValue(STATE, visual), Block.UPDATE_ALL)
		}
	}

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

	companion object {
		val STATE: EnumProperty<AltarState> = EnumProperty.create("state", AltarState::class.java)

		/**
		 * 下方 3×3 是否为配置指定的底座方块（默认下界合金块）。
		 *
		 * 方块实体每 20 tick 校验一次用的是同一套判定；这里额外给"放下瞬间"用。
		 */
		fun isBaseComplete(level: BlockGetter, pos: BlockPos): Boolean {
			val config = ShenzhiMod.config.altar
			val expected = ItemStacks.of(config.baseBlockId).item as? BlockItem ?: return false
			val half = config.baseSize / 2
			for (dx in -half..half) {
				for (dz in -half..half) {
					if (!level.getBlockState(pos.offset(dx, -1, dz)).`is`(expected.block)) return false
				}
			}
			return true
		}
	}
}
