package com.xin.shenzhimod.mixin;

import com.xin.shenzhimod.config.ConfigManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把猪灵蛮兵的放置类型强制为 ON_GROUND（模块 J）。
 *
 * 与 Carpet 的实现字面一致（需求 §6.8.4）：
 * 原版对 PIGLIN_BRUTE 没有注册放置数据，默认是 NO_RESTRICTIONS，
 * 会让蛮兵刷在半空/方块里，与空岛装置的手感对不上。
 *
 * 目标方法：{@code public static SpawnPlacementType net.minecraft.world.entity.SpawnPlacements.getPlacementType(EntityType<?>)}
 *
 * 注意：这里只做"读配置 + 返回枚举"，不含任何状态；真正决定能不能刷的
 * 是 {@code PiglinBruteSpawnPredicate}（生成谓词）。
 */
@Mixin(SpawnPlacements.class)
public abstract class SpawnPlacementsGetPlacementTypeMixin {
	@Inject(method = "getPlacementType", at = @At("HEAD"), cancellable = true)
	private static void shenzhimod$forcePiglinBruteOnGround(
			EntityType<?> entityType,
			CallbackInfoReturnable<SpawnPlacementType> cir) {
		if (entityType == EntityType.PIGLIN_BRUTE
				&& ConfigManager.INSTANCE.getConfig().getPiglinBrute().getSpawningInBastions()) {
			cir.setReturnValue(SpawnPlacementTypes.ON_GROUND);
		}
	}
}
