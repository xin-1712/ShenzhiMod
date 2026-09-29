package com.xin.shenzhimod.content.item

import com.xin.shenzhimod.content.entity.ThrownNetherStarShard
import net.minecraft.core.Direction
import net.minecraft.core.Position
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ProjectileItem
import net.minecraft.world.level.Level

/**
 * 下界之星碎片（模块 H）。
 *
 * 需求（§4.8）：右键直接投掷，无需蓄力，每次消耗 1 个；**参数全部照抄附魔之瓶**——
 * 投掷力度/角度（-20° / 0.7 / 1.0 散布）、重力（0.07）、音效、命中后的经验球数量与散布、
 * 发射器行为。因此这里直接复用原版的 `Projectile.spawnProjectileFromRotation` 与同样的常量，
 * 只把产物与外观换成"下界之星碎片"。
 *
 * "使用冷却"也照抄：附魔之瓶本身没有 `use_cooldown` 组件，所以碎片同样没有冷却，
 * 连点右键的节奏与原版一致（§12 第 13 条）。
 *
 * 差异只有两处：物品本身（名字、贴图、堆叠数）与投掷物实体。
 */
class NetherStarShardItem(properties: Properties) : Item(properties), ProjectileItem {

	override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
		val itemStack = player.getItemInHand(hand)
		level.playSound(
			null,
			player.x,
			player.y,
			player.z,
			SoundEvents.EXPERIENCE_BOTTLE_THROW,
			SoundSource.NEUTRAL,
			0.5f,
			0.4f / (level.random.nextFloat() * 0.4f + 0.8f),
		)
		if (level is ServerLevel) {
			Projectile.spawnProjectileFromRotation(
				{ serverLevel, owner, stack -> ThrownNetherStarShard(serverLevel, owner, stack) },
				level,
				itemStack,
				player,
				THROW_ANGLE,
				THROW_POWER,
				THROW_UNCERTAINTY,
			)
		}

		player.awardStat(Stats.ITEM_USED.get(this))
		itemStack.consume(1, player)
		return InteractionResult.SUCCESS
	}

	override fun asProjectile(level: Level, position: Position, itemStack: ItemStack, direction: Direction): Projectile =
		ThrownNetherStarShard(level, position.x(), position.y(), position.z(), itemStack)

	/** 发射器行为也与附魔之瓶一致（散布减半、力度 1.25 倍）。 */
	override fun createDispenseConfig(): ProjectileItem.DispenseConfig =
		ProjectileItem.DispenseConfig.builder()
			.uncertainty(ProjectileItem.DispenseConfig.DEFAULT.uncertainty() * 0.5f)
			.power(ProjectileItem.DispenseConfig.DEFAULT.power() * 1.25f)
			.build()

	private companion object {
		/** 与 `ExperienceBottleItem` 完全相同的投掷参数。 */
		const val THROW_ANGLE = -20.0f
		const val THROW_POWER = 0.7f
		const val THROW_UNCERTAINTY = 1.0f
	}
}
