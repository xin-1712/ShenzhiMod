package com.xin.shenzhimod.advancement

import com.xin.shenzhimod.ShenzhiMod
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * 成就（advancements）的授予入口。
 *
 * 本模组的成就分两类：
 * 1. **原版触发器就能表达**的：三核心"进入玩家物品栏"用 `minecraft:inventory_changed`，
 *    数据生成里写死条件即可，代码完全不用管（时刻 = 物品真正进背包的那一刻）；
 * 2. **原版触发器表达不了**的：压缩成功、祭坛底座校验通过、祭坛落雷出创世核心——
 *    这些时刻必须由服务端逻辑精确触发，因此这些成就的条件写成 `minecraft:impossible`
 *    （永远不会被原版触发），由本对象在**代码里那一刻**调用 [award] 直接授予。
 *
 * 成就 ID 与数据生成（`datagen/ModAdvancementProvider.kt`）共用同一份常量，
 * 避免"改了 JSON 文件名忘了改代码"。
 */
object ModAdvancements {
	/**
	 * `impossible` 条件的**条件名**（JSON 里 `criteria.code` 的键）。
	 * 授予时用的键必须和它完全一致，否则原版 `PlayerAdvancements#award` 会返回 false。
	 */
	const val CODE_CRITERION: String = "code"

	/** 根节点：拿到第一块石灰岩（模组内容的起点）。 */
	val ROOT: Identifier = id("root")

	/** 第一次压缩成功（铁砧 + 重锤）。 */
	val COMPRESSION: Identifier = id("compression")

	/** 获得农业 / 矿物 / 战斗核心（进背包那一刻，走原版 `inventory_changed`）。 */
	val CORE_AGRICULTURE: Identifier = id("core_agriculture")
	val CORE_MINERAL: Identifier = id("core_mineral")
	val CORE_COMBAT: Identifier = id("core_combat")

	/** 终极祭坛的 3×3 底座第一次摆对（贴图从"底座不正确"切到正常那一档）。 */
	val ALTAR_BASE: Identifier = id("altar_base")

	/** 终极祭坛融合出创世核心、落雷劈下。 */
	val CREATION_CORE: Identifier = id("creation_core")

	/** 为鞘翅附魔上"飞行"（带飞行附魔的鞘翅进入玩家手中）。 */
	val ELYTRA_FLIGHT: Identifier = id("elytra_flight")

	/**
	 * 给玩家授予成就。
	 *
	 * @return 是否真的授予成功（重复调用、成就被数据包删掉都会返回 false，不会抛异常）
	 */
	fun award(player: ServerPlayer, advancement: Identifier): Boolean {
		val holder = runCatching { player.level().server.advancements.get(advancement) }.getOrNull()
		if (holder == null) {
			ShenzhiMod.logger.debug("成就 {} 不存在（数据包已覆盖？），跳过授予", advancement)
			return false
		}
		return runCatching { player.advancements.award(holder, CODE_CRITERION) }
			.onFailure { ShenzhiMod.logger.warn("授予成就 {} 失败", advancement, it) }
			.getOrDefault(false)
	}

	/**
	 * 优先授予"应该得到它的那个玩家"（放下祭坛 / 启动祭坛的人），
	 * 他不在线时退化成"把附近 [radius] 格内的玩家都发一遍"。
	 *
	 * 之所以要退化：祭坛的流程可能在上线/下线之间完成（例如底座是别人补的、
	 * 或者玩家中途退出），此时成就不能凭空消失。
	 *
	 * @return 是否**找到了发放对象**（哪怕对方早就拿过这个成就）。
	 *   调用方据此决定"要不要过一会儿再试"——目标玩家不在线且附近没人时返回 false，
	 *   那时先别把"已发放"标记写死，等有人在场再发。
	 */
	fun awardPreferredOrNearby(
		level: ServerLevel,
		preferredPlayer: UUID?,
		pos: BlockPos,
		radius: Double,
		advancement: Identifier,
	): Boolean {
		val preferred = preferredPlayer?.let { level.server.playerList.getPlayer(it) }
		if (preferred != null) {
			award(preferred, advancement)
			return true
		}
		val box = AABB.ofSize(Vec3.atCenterOf(pos), radius * 2, radius * 2, radius * 2)
		val nearby = level.getEntitiesOfClass(ServerPlayer::class.java, box)
		if (nearby.isEmpty()) return false
		nearby.forEach { award(it, advancement) }
		return true
	}

	private fun id(path: String): Identifier = ShenzhiMod.id(path)
}
