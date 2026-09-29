package com.xin.shenzhimod.mixin;

import com.xin.shenzhimod.interaction.TotemCooldownService;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 不死图腾冷却（模块 G）的注入点，对应需求 §4.7 与 §6.3-G。
 *
 * 26.1 的原版免死逻辑（`LivingEntity#checkTotemDeathProtection`）是"按手遍历 →
 * 看物品上有没有 `death_protection` 组件 → 有则复制一份、扣掉原件、生效"。
 *
 * 我们只替换**取手牌这一步**，把"原版应当看到的物品"换掉：
 * - 冷却中的附魔图腾 → 换成空堆，原版会当作没有图腾继续遍历下一只手，
 *   于是"冷却期间普通不死图腾仍然可用"这条需求自动成立；
 * - 可触发的附魔图腾 → 换成一个副本，原版扣的是副本，真实物品不被消耗，同时写入 60 秒冷却；
 * - 其它情况原样返回，原版行为一字未改。
 *
 * 只在服务端生效（`checkTotemDeathProtection` 由 `hurtServer` 调用），客户端不参与免死判定。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTotemCooldownMixin {
	@Redirect(
			method = "checkTotemDeathProtection",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"
			)
	)
	private ItemStack shenzhimod$resolveDeathProtectionItem(LivingEntity self, InteractionHand hand) {
		// @Redirect 的处理器签名 = 被重定向调用的接收者 + 原参数（Mixin 0.8.7 实测要求）
		return TotemCooldownService.INSTANCE.resolveTotemForDeathProtection(self, self.getItemInHand(hand), hand);
	}
}
