package com.xin.shenzhimod.client

import com.xin.shenzhimod.network.TotemTriggerPayload
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/**
 * 客户端侧的"这次免死是谁触发的"暂存（模块 G 动画修正）。
 *
 * 服务端在决定保护物品时发来 [TotemTriggerPayload]，这里存一下手号 + 当前 tick；
 * 随后原版的实体事件 35 会调用 `ClientPacketListener#findTotem`，我们的注入从这里取用一次。
 *
 * - **取用即清**：避免上一次的信号影响下一次（例如之后换成普通图腾触发）。
 * - **限时有效**（[VALID_TICKS]）：信号与动画本来就在同一 tick 内，留一点余量防止异常情况下串味。
 * - 只是表现层数据，丢了最多回到原版行为，不影响任何玩法判定（服务端权威）。
 */
object TotemAnimationState {
	private const val VALID_TICKS = 10

	@Volatile
	private var hand: InteractionHand? = null

	@Volatile
	private var stampTick: Int = Int.MIN_VALUE

	/** 收到服务端信号（在客户端主线程调用）。 */
	fun onServerTrigger(player: Player, handOrdinal: Int) {
		val hands = InteractionHand.values()
		hand = hands.getOrNull(handOrdinal) ?: return
		stampTick = player.tickCount
	}

	/**
	 * 取用一次：返回"这次真正生效的那件图腾"；没有有效信号时返回 `null`（交回原版逻辑）。
	 */
	fun consumeTriggeredTotem(player: Player): ItemStack? {
		val signaled = hand ?: return null
		hand = null
		if (player.tickCount - stampTick > VALID_TICKS) return null
		val stack = player.getItemInHand(signaled)
		// 已经不在手上（例如被别的机制拿走）就交回原版，不做额外假设。
		return if (stack.isEmpty || stack.get(DataComponents.DEATH_PROTECTION) == null) null else stack
	}
}
