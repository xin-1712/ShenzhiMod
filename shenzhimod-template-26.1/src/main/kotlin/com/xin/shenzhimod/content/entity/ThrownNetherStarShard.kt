package com.xin.shenzhimod.content.entity

import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.registry.ModEntities
import com.xin.shenzhimod.registry.ModItems
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

/**
 * 投掷出去的下界之星碎片（模块 H）。
 *
 * 行为逐行对齐原版 `ThrownExperienceBottle`（§4.8 "全部照抄附魔之瓶"）：
 * - 重力 0.07；
 * - 命中时播放 2002 号世界事件（碎裂音效 + 粒子）；
 * - 产出 `3 + rand(5) + rand(5)` 点经验，且按命中方向（方块法线 / 反向速度）散布；
 * - 命中后自身消失。
 */
class ThrownNetherStarShard : ThrowableItemProjectile {

	constructor(type: EntityType<out ThrownNetherStarShard>, level: Level) : super(type, level)

	constructor(level: Level, owner: LivingEntity, itemStack: ItemStack) : super(ModEntities.NETHER_STAR_SHARD, owner, level, itemStack)

	constructor(level: Level, x: Double, y: Double, z: Double, itemStack: ItemStack) : super(ModEntities.NETHER_STAR_SHARD, x, y, z, level, itemStack)

	override fun getDefaultItem(): Item = ModItems.NETHER_STAR_SHARD

	override fun getDefaultGravity(): Double = GRAVITY

	override fun onHit(hitResult: HitResult) {
		super.onHit(hitResult)
		val serverLevel = level() as? ServerLevel ?: return
		serverLevel.levelEvent(LEVEL_EVENT_BOTTLE_BREAK, blockPosition(), particleColor)
		val xpCount = 3 + random.nextInt(5) + random.nextInt(5)
		if (hitResult is BlockHitResult) {
			ExperienceOrb.awardWithDirection(serverLevel, hitResult.location, hitResult.direction.unitVec3, xpCount)
		} else {
			ExperienceOrb.awardWithDirection(serverLevel, hitResult.location, deltaMovement.scale(-1.0), xpCount)
		}

		discard()
	}

	private companion object {
		/** 与原版投掷物一致的重力。 */
		const val GRAVITY = 0.07

		/** 原版"药水碎裂"世界事件：碎裂音效 + 药水瓶碎片 + 一团带颜色的粒子。 */
		const val LEVEL_EVENT_BOTTLE_BREAK = 2002

		/**
		 * 碎裂粒子的颜色（0xRRGGBB，事件的 data 参数只取 RGB）。
		 *
		 * 默认 `E0D5FF`（白中带紫，贴近下界之星）——原版附魔之瓶用的是绿色，
		 * 这里改成自己的颜色以免和附魔之瓶混在一起。可由配置 `visual.netherStarShardParticleColor` 覆盖。
		 */
		const val DEFAULT_PARTICLE_COLOR = 0xE0D5FF

		private val particleColor: Int = resolveParticleColor()

		private fun resolveParticleColor(): Int {
			val raw = ShenzhiMod.config.visual.netherStarShardParticleColor.trim().removePrefix("#")
			val parsed = if (raw.length == 6) raw.toIntOrNull(16) else null
			if (parsed == null) {
				ShenzhiMod.logger.warn(
					"配置 visual.netherStarShardParticleColor 不是 6 位十六进制（当前 \"{}\"），已回退到默认颜色",
					raw,
				)
				return DEFAULT_PARTICLE_COLOR
			}
			return parsed
		}
	}
}
