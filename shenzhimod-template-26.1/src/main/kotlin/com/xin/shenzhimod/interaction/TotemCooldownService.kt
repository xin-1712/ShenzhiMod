package com.xin.shenzhimod.interaction

import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.network.TotemTriggerPayload
import com.mojang.serialization.Codec
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.InteractionHand
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.UseCooldown
import java.util.Optional

/**
 * 不死图腾冷却（模块 G）的适配层。
 *
 * 需求（§4.7）与方案（§6.3-G）：
 * - **复用原版冷却系统**：26.1 的冷却按"冷却组 Identifier"管理，按玩家生效、自动同步客户端、
 *   自动画圆弧——因此不需要自研冷却状态与渲染；
 * - 附魔图腾触发免死时**不消耗物品**，改为进入 60 秒冷却；
 * - 冷却中的附魔图腾不触发、不消耗，原版逻辑继续往下找**普通**不死图腾；
 * - 玩家身上所有附魔图腾共享同一冷却（同一个冷却组）。
 *
 * 之所以把"附魔图腾"的判定写成"看 DEATH_PROTECTION 组件 + 本模组附魔"两部分，
 * 是因为 26.1 的原版免死逻辑已经改成组件驱动（`checkTotemDeathProtection` 只看
 * `DataComponents.DEATH_PROTECTION`），我们跟着组件走就不会错过任何"能免死的物品"。
 */
object TotemCooldownService {
	private const val TICKS_PER_SECOND = 20
	private const val DEFAULT_GROUP = "enchanted_totem"
	/** 冷却恢复巡检间隔（tick）：只用于"存档/换维度后把冷却补回去"，不参与计时本身。 */
	private const val RESTORE_INTERVAL_TICKS = 10

	/**
	 * 冷却结束时刻，**以主世界游戏刻为准**，随存档持久化。
	 *
	 * 为什么要有它：26.1 的冷却存在 `ItemCooldowns` 里，**只活在这条命的运行内存中**，
	 * 退出重进（或死亡重生）就没了 —— 于是"附魔图腾撞冷却 → 退出重进 → 又能免死"成了漏洞。
	 * 这里把结束时刻记在玩家附件里，登录时按剩余时间把原版冷却补回去。
	 *
	 * 用主世界游戏刻而不是玩家所在维度的游戏刻：下界/末地的 `fixed_time` 让它们的游戏刻不推进。
	 * 因此**离线时间也算数**（下线 5 分钟再上来，60 秒冷却自然已经结束）。
	 */
	private val COOLDOWN_END: AttachmentType<Long> =
		AttachmentRegistry.createPersistent(ShenzhiMod.id("totem_cooldown_end"), Codec.LONG)

	/** 冷却组 ID（可在配置里改；同一组 = 共享冷却）。 */
	fun cooldownGroupId(): Identifier =
		Identifier.tryParse(ShenzhiMod.config.totem.cooldownGroupId)
			?: Identifier.fromNamespaceAndPath(ShenzhiMod.MOD_ID, DEFAULT_GROUP)

	fun register() {
		// 登录后立刻补回冷却（客户端同时会收到冷却包，圆弧照常显示）
		ServerPlayerEvents.JOIN.register { player -> restoreCooldown(player) }
		// 兜底巡检：覆盖"换维度/其它把 ItemCooldowns 清掉"的路径；原版冷却还在跑时不做任何事
		ServerTickEvents.END_SERVER_TICK.register { server ->
			if (server.tickCount % RESTORE_INTERVAL_TICKS != 0) return@register
			for (player in server.playerList.players) restoreCooldown(player)
		}
		ShenzhiMod.logger.info("不死图腾冷却已接线（{} 秒，冷却会随存档保留）", ShenzhiMod.config.totem.cooldownSeconds)
	}

	/** 当前时刻（主世界游戏刻）；拿不到服务器时返回 null。 */
	private fun clock(player: ServerPlayer): Long = player.level().server.overworld().gameTime

	/**
	 * 按存档里的结束时刻把冷却补回玩家身上（幂等：原版冷却还在跑就什么都不做）。
	 */
	fun restoreCooldown(player: ServerPlayer) {
		val end = player.getAttachedOrElse(COOLDOWN_END, 0L)
		if (end <= 0L) return
		val now = clock(player)
		val remaining = end - now
		if (remaining <= 0L) {
			player.setAttached(COOLDOWN_END, 0L)
			return
		}
		// 已经在冷却就别重复 addCooldown：那会每 0.5 秒给客户端重发一次冷却包、圆弧一直重置。
		if (player.cooldowns.isOnCooldown(probeStack())) return
		player.cooldowns.addCooldown(cooldownGroupId(), remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
	}

	/** 探针物品：`ItemCooldowns` 的查询只接受物品，这里造一个"带本模组冷却组"的图腾来查询。 */
	private fun probeStack(): ItemStack {
		val stack = ItemStack(Items.TOTEM_OF_UNDYING)
		stack.set(
			DataComponents.USE_COOLDOWN,
			UseCooldown(ShenzhiMod.config.totem.cooldownSeconds.toFloat(), Optional.of(cooldownGroupId())),
		)
		return stack
	}

	/** 该物品是不是"带冷却附魔的不死图腾"（不含"是否冷却中"的判断）。 */
	fun hasCooldownEnchant(stack: ItemStack): Boolean =
		ModEnchantments.has(stack, ModEnchantments.TOTEM_COOLDOWN)

	/**
	 * 给附魔图腾写入冷却组。
	 *
	 * 原版圆弧是按**物品上的冷却组**画的，所以附魔时必须把组名写进物品（M6 的核心附魔会调这里）；
	 * 手动 `/give` 出来的附魔图腾没有这个组件，命中判定时会由 [ensureCooldownGroup] 补上。
	 */
	fun tagEnchantedTotem(stack: ItemStack) {
		val group = cooldownGroupId()
		val existing = stack.get(DataComponents.USE_COOLDOWN)
		if (existing != null && existing.cooldownGroup().map { it == group }.orElse(false)) return
		val seconds = ShenzhiMod.config.totem.cooldownSeconds.toFloat()
		stack.set(DataComponents.USE_COOLDOWN, UseCooldown(seconds, Optional.of(group)))
	}

	/**
	 * 免死判定时"原版应当看到的手牌"。
	 *
	 * - 冷却中的附魔图腾 → 返回空堆，原版循环会跳过它去找普通图腾；
	 * - 可触发的附魔图腾 → 返回**副本**（原版对副本 `shrink(1)`，真实物品不会消失），同时开始冷却；
	 * - 其它（普通图腾 / 空手 / 别的物品）→ 原样返回，走原版逻辑。
	 *
	 * 这个方法只由 `LivingEntity#checkTotemDeathProtection` 的注入调用，**只在服务端**执行，
	 * 客户端不会自己算能不能免死（§6.1 服务端权威）。
	 *
	 * 一旦确定"原版会用这一件"，就顺手给该玩家发一个 [TotemTriggerPayload]：
	 * 动画要显示的是**真正生效的那一件**，而客户端自己无法反推（见包内注释）。
	 * 这个包在方法体内发出，早于原版随后广播的实体事件 35，因此在同一 tick 内先到客户端。
	 */
	fun resolveTotemForDeathProtection(entity: LivingEntity, stack: ItemStack, hand: InteractionHand): ItemStack {
		if (stack.isEmpty) return stack
		// 只处理真正带"死亡保护"组件的物品：带附魔的其它物品不参与免死。
		if (stack.get(DataComponents.DEATH_PROTECTION) == null) return stack
		// 普通图腾：原版会用这一件，动画也报这一件。
		if (!hasCooldownEnchant(stack)) {
			notifyTriggeredHand(entity, hand)
			return stack
		}
		val player = entity as? Player ?: return stack

		ensureCooldownGroup(stack)
		if (player.cooldowns.isOnCooldown(stack)) return ItemStack.EMPTY
		// 配置为"仍然消耗"时不做任何特殊处理，完全走原版（扣物品 + 免死）。
		if (ShenzhiMod.config.totem.consumeOnTrigger) {
			notifyTriggeredHand(entity, hand)
			return stack
		}

		startCooldown(player)
		notifyTriggeredHand(entity, hand)
		// 副本：原版会对它 shrink(1)，真实物品因此不被消耗（§4.7 "不消耗该附魔图腾"）。
		return stack.copy()
	}

	/** 告诉玩家客户端"这次生效的是哪只手"（非玩家实体没有客户端，直接跳过）。 */
	private fun notifyTriggeredHand(entity: LivingEntity, hand: InteractionHand) {
		val player = entity as? ServerPlayer ?: return
		ServerPlayNetworking.send(player, TotemTriggerPayload(hand.ordinal))
	}

	private fun ensureCooldownGroup(stack: ItemStack) {
		val group = cooldownGroupId()
		val existing = stack.get(DataComponents.USE_COOLDOWN)
		if (existing != null && existing.cooldownGroup().map { it == group }.orElse(false)) return
		tagEnchantedTotem(stack)
	}

	private fun startCooldown(player: Player) {
		val ticks = ShenzhiMod.config.totem.cooldownSeconds * TICKS_PER_SECOND
		if (ticks <= 0) return
		// 显式按冷却组加冷却：即使物品上恰好没写组名，也不会污染普通图腾的冷却显示。
		player.cooldowns.addCooldown(cooldownGroupId(), ticks)
		// 记住结束时刻：退出重进后按剩余时间补回来（防"重登刷冷却"）
		val serverPlayer = player as? ServerPlayer ?: return
		serverPlayer.setAttached(COOLDOWN_END, clock(serverPlayer) + ticks)
	}

}
