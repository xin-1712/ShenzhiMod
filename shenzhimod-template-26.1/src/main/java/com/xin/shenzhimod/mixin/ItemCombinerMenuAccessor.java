package com.xin.shenzhimod.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读取铁砧容器（`ItemCombinerMenu`）持有的玩家实例。
 *
 * `player` 字段声明在父类 `ItemCombinerMenu` 里（protected final），
 * 与其在子类 Mixin 里跨类 shadow，不如直接在**声明它的类**上开一个访问器——
 * 这样字段解析一定成功，也不会被上游重构影响。
 */
@Mixin(ItemCombinerMenu.class)
public interface ItemCombinerMenuAccessor {
	@Accessor("player")
	Player shenzhimod$getPlayer();
}
