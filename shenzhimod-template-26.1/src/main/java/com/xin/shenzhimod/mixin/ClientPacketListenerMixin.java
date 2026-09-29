package com.xin.shenzhimod.mixin;

import com.xin.shenzhimod.client.TotemAnimationState;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 免死时那个"图腾浮现"动画的贴图修正（模块 G，仅客户端）。
 *
 * 原版客户端收到实体事件 35 后，用 `ClientPacketListener#findTotem` 决定动画显示哪一件物品——
 * 它的逻辑是"按手顺序找第一件带 `death_protection` 的物品"，**并不知道服务端实际用了哪一件**：
 * 本模组的附魔图腾在冷却期会被服务端跳过，而客户端无法反推（触发后它自己也立刻进冷却，
 * "刚触发的"和"上一轮就在冷却的"在客户端看起来完全一样）。
 *
 * 所以服务端在决定保护物品时会直接发一个 `TotemTriggerPayload` 说明手号（早于实体事件 35），
 * 这里只把返回值换成那只手上的物品；没有信号时原样交回原版。
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "findTotem", at = @At("RETURN"), cancellable = true)
	private static void shenzhimod$showTriggeringTotem(Player player, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack actual = TotemAnimationState.INSTANCE.consumeTriggeredTotem(player);
		if (actual != null) {
			cir.setReturnValue(actual);
		}
	}
}
