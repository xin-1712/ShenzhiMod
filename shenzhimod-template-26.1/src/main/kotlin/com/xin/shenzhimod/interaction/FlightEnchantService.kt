package com.xin.shenzhimod.interaction

import com.mojang.serialization.Codec
import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.ability.FlightAbilityArbiter
import com.xin.shenzhimod.ability.FlightAction
import com.xin.shenzhimod.ability.FlightState
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.util.function.Consumer

/**
 * 鞘翅飞行（模块 I）的适配层：**服务端权威**地把"穿戴飞行鞘翅"翻译成玩家能力。
 *
 * 需求（§4.9）与方案（§6.3-I）：
 * - 穿戴带飞行附魔的鞘翅 → 服务端授予 `mayfly` 并同步（客户端自动获得"双击空格起飞"）；
 * - 卸下 / 死亡 / 换维度 / 重新登录 → 主动重算；
 * - 创造与旁观模式的原有能力**永不剥夺**；
 * - 只回收"本模组授予过"的能力（`granted_by_us` 标记，持久化到存档），
 *   其它模组给的飞行能力不会被误关；
 * - 飞行速度接近原版**生存**速度（配置项），而不是创造飞行速度。
 *
 * 触发方式是"每 [RECOMPUTE_INTERVAL_TICKS] tick 全量重算 + 关键事件立即重算"：
 * 装备变化、维度切换、重生、登录这些路径都能覆盖，且不依赖原版装备事件的内部实现细节。
 */
object FlightEnchantService {
	/** 全量重算间隔（tick）。10 tick = 0.5 秒，玩家感觉不到延迟，开销也只有"读一个装备槽"。 */
	const val RECOMPUTE_INTERVAL_TICKS = 10

	/** 玩家基础移动速度属性值（原版 4.317 m/s 对应 0.1）。 */
	private const val BASE_MOVEMENT_SPEED = 0.1

	/** "这条命是不是本模组给的飞行能力"标记，随存档持久化。 */
	private val GRANTED_BY_US: AttachmentType<Boolean> =
		AttachmentRegistry.createPersistent(ShenzhiMod.id("flight_granted"), Codec.BOOL)

	/**
	 * "上一次巡检时是否正在用本模组飞行"，随存档持久化。
	 *
	 * 为什么需要：原版**登录时会按游戏模式重设能力**（`GameType#updatePlayerAbilities`），
	 * 生存模式那一支里写着 `abilities.flying = false`（创造模式那一支不动 `flying`，所以创造不受影响），
	 * 于是我们把 `mayfly` 补回来之后，玩家仍然会"直接掉下来、要重新双击空格"。
	 * 有了这个标记，登录时就能把 `flying` 一起恢复——和创造模式的手感对齐。
	 */
	private val FLYING_BY_US: AttachmentType<Boolean> =
		AttachmentRegistry.createPersistent(ShenzhiMod.id("flight_flying"), Codec.BOOL)

	/** 飞行计时（tick），只用于按速率扣鞘翅耐久；纯运行时数据，不持久化。 */
	private val FLIGHT_TICKS: AttachmentType<Int> =
		AttachmentRegistry.createDefaulted(ShenzhiMod.id("flight_ticks")) { 0 }

	fun register() {
		ServerTickEvents.END_SERVER_TICK.register { server ->
			if (server.tickCount % RECOMPUTE_INTERVAL_TICKS != 0) return@register
			for (player in server.playerList.players) {
				recompute(player)
				chargeElytraDurability(player)
				rememberFlightState(player)
			}
		}
		// 登录后立即重算 + 恢复"退出前正在飞"的状态；重生后立即重算。
		ServerPlayerEvents.JOIN.register { player -> restoreFlightAfterLogin(player) }
		ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ -> recompute(newPlayer) }
		ShenzhiMod.logger.info("鞘翅飞行能力仲裁已接线（每 {} tick 重算一次，登录会恢复飞行状态）", RECOMPUTE_INTERVAL_TICKS)
	}

	/**
	 * 登录时的飞行恢复。
	 *
	 * 顺序很重要：先 [recompute]（原版登录会把 `mayfly` 也按生存模式清掉），
	 * 再看存档里"退出前是否在飞"，是的话把 `flying` 一起补上并同步给客户端。
	 */
	fun restoreFlightAfterLogin(player: ServerPlayer) {
		recompute(player)
		if (!player.getAttachedOrElse(FLYING_BY_US, false)) return
		if (player.abilities.flying) return
		if (!hasFlightEnchant(player.getItemBySlot(EquipmentSlot.CHEST))) return
		player.abilities.flying = true
		player.onUpdateAbilities()
		ShenzhiMod.logger.debug("已恢复 {} 的飞行状态（退出前正在飞行）", player.name.string)
	}

	/** 记录"当前是否正在用本模组飞行"，供下次登录恢复；只在状态变化时写，避免无意义的存档写入。 */
	private fun rememberFlightState(player: ServerPlayer) {
		val active = isModFlightActive(player)
		if (player.getAttachedOrElse(FLYING_BY_US, false) != active) {
			player.setAttached(FLYING_BY_US, active)
		}
	}

	/** 重算并同步某个玩家的飞行能力。 */
	fun recompute(player: ServerPlayer) {
		val abilities = player.abilities
		val grantedByUs = player.getAttachedOrElse(GRANTED_BY_US, false)
		val action = FlightAbilityArbiter.decide(
			FlightState(
				hasFlightEnchant = hasFlightEnchant(player.getItemBySlot(EquipmentSlot.CHEST)),
				isCreativeOrSpectator = player.isCreative || player.isSpectator,
				mayFly = abilities.mayfly,
				grantedByUs = grantedByUs,
			),
		)
		when (action) {
			FlightAction.Grant -> {
				abilities.mayfly = true
				player.setAttached(GRANTED_BY_US, true)
				player.onUpdateAbilities()
			}

			FlightAction.Revoke -> {
				abilities.mayfly = false
				abilities.flying = false
				player.setAttached(GRANTED_BY_US, false)
				player.onUpdateAbilities()
			}

			FlightAction.Keep -> Unit
		}
	}

	/**
	 * 飞行中的鞘翅耐久消耗（§4.9 的补充：飞行也要磨损）。
	 *
	 * 原版只在**滑翔**时按 1 点/秒扣耐久，本模组的飞行走的是创造式能力、不会碰耐久，
	 * 于是"飞一辈子也不掉耐久"。这里补上同样速率的消耗：
	 * - 速率由 `flight.durabilityTicksPerDamage` 控制（默认 20 tick = 1 点/秒，与原版滑翔一致）；
	 * - 走原版 `ItemStack#hurtAndBreak`，所以**耐久附魔、经验修补、创造模式不消耗**全部自动与原版一致；
	 * - 鞘翅耐久归零会按原版碎裂、胸甲槽变空，下一轮重算就会自动收回飞行能力。
	 */
	private fun chargeElytraDurability(player: ServerPlayer) {
		val config = ShenzhiMod.config.flight
		val ticksPerDamage = config.durabilityTicksPerDamage
		if (!config.consumeElytraDurability || ticksPerDamage <= 0 || !isModFlightActive(player)) {
			player.setAttached(FLIGHT_TICKS, 0)
			return
		}

		val ticks = player.getAttachedOrElse(FLIGHT_TICKS, 0) + RECOMPUTE_INTERVAL_TICKS
		val damage = ticks / ticksPerDamage
		player.setAttached(FLIGHT_TICKS, ticks % ticksPerDamage)
		if (damage <= 0) return

		// `ServerPlayer#level()` 是协变重写，直接就是 ServerLevel
		player.getItemBySlot(EquipmentSlot.CHEST).hurtAndBreak(damage, player.level(), player, Consumer { })
	}

	/** 物品是否带飞行附魔。 */
	fun hasFlightEnchant(stack: ItemStack): Boolean =
		ModEnchantments.has(stack, ModEnchantments.FLIGHT)

	/** 玩家当前是否"靠本模组飞行"（创造/旁观不算，走原版速度）。 */
	fun isModFlightActive(player: Player): Boolean {
		if (!player.abilities.flying) return false
		// 原版 `getFlyingSpeed` 在骑乘状态下也会退回走路速度，这里保持一致。
		if (player.isPassenger) return false
		if (player.isCreative || player.isSpectator) return false
		return hasFlightEnchant(player.getItemBySlot(EquipmentSlot.CHEST))
	}

	/**
	 * 飞行速度覆盖值（`Player#getFlyingSpeed` 注入用）。
	 *
	 * 基准取**原版生存移动速度**（§8-G10），并可选地跟随移动速度属性（速度 / 海豚恩惠等效果
	 * 都是通过 `MOVEMENT_SPEED` 属性生效的），最后乘总倍率 `speedScale`。
	 *
	 * @return `null` 表示"不管"，调用方保留原版返回值。
	 */
	fun flightSpeedOverride(player: Player): Float? {
		if (!isModFlightActive(player)) return null
		val config = ShenzhiMod.config.flight
		val base = if (player.isSprinting) config.sprintFlyingSpeed else config.baseFlyingSpeed
		val attributeScale = if (config.applyMovementSpeedAttributes) {
			player.getAttributeValue(Attributes.MOVEMENT_SPEED) / BASE_MOVEMENT_SPEED
		} else {
			1.0
		}
		return (base * attributeScale * config.speedScale).toFloat()
	}

}
