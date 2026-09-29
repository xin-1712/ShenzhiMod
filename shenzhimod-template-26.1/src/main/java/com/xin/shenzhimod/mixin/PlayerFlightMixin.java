package com.xin.shenzhimod.mixin;

import com.xin.shenzhimod.interaction.FlightEnchantService;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 鞘翅飞行（模块 I）的飞行速度，对应需求 §4.9 与 §6.3-I。
 *
 * 能力授予本身走 `Abilities.mayfly`（见 `FlightEnchantService`），但原版飞行速度是创造模式的 0.05，太快（§8-G10）。
 * 这里在"本模组飞行生效"时换成配置里的生存速度基准；其余情况（普通行走、创造/旁观飞行）原样返回。
 *
 * 注意：**不拦截 `tryToStartFallFlying`**——2026-09-29 反馈确认"能跳劈滑翔"是想要的手感，
 * 因此生存模式与创造模式保持一致：既能双击空格飞行，也能在空中按一次空格开始滑翔。
 */
@Mixin(Player.class)
public abstract class PlayerFlightMixin {
	@Inject(method = "getFlyingSpeed", at = @At("RETURN"), cancellable = true)
	private void shenzhimod$flightSpeed(CallbackInfoReturnable<Float> cir) {
		Float override = FlightEnchantService.INSTANCE.flightSpeedOverride((Player) (Object) this);
		if (override != null) {
			cir.setReturnValue(override);
		}
	}
}
