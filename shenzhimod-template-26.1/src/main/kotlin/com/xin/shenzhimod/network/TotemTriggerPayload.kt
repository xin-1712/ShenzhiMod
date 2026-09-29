package com.xin.shenzhimod.network

import com.xin.shenzhimod.ShenzhiMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * 服务端 → 客户端：**这次免死实际生效的是哪一只手的图腾**（模块 G 的表现层修正）。
 *
 * 为什么需要这个包：原版客户端播放"图腾浮现"动画时，只会调用
 * `ClientPacketListener#findTotem`——它的逻辑是"按手顺序取第一件带 `death_protection` 的物品"，
 * 根本不知道服务端这次跳过了哪一件（本模组的附魔图腾在冷却期会被跳过）。
 * 客户端也无法靠冷却状态反推：**附魔图腾一旦触发，自己也立刻进入冷却**，
 * 于是"刚触发的那件"和"上一轮就在冷却的那件"在客户端看起来一模一样。
 *
 * 所以这里由服务端在决定保护物品的那一刻（早于原版广播实体事件 35）直接告诉客户端手号，
 * 客户端只做一件事：动画改用那只手的物品。
 *
 * 只发一个 varint（0 = 主手、1 = 副手），每 60 秒最多几次，开销可忽略。
 */
data class TotemTriggerPayload(val handOrdinal: Int) : CustomPacketPayload {
	override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

	companion object {
		val TYPE: CustomPacketPayload.Type<TotemTriggerPayload> =
			CustomPacketPayload.Type(ShenzhiMod.id("totem_trigger"))

		val CODEC: StreamCodec<RegistryFriendlyByteBuf, TotemTriggerPayload> = CustomPacketPayload.codec(
			{ payload, buffer -> buffer.writeVarInt(payload.handOrdinal) },
			{ buffer -> TotemTriggerPayload(buffer.readVarInt()) },
		)
	}
}
