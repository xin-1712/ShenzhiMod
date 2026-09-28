package com.xin.shenzhimod.registry

import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.content.menu.TransmutationCoreMenu
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.inventory.MenuType

/**
 * 容器菜单类型注册。
 *
 * 26.1 的 `MenuType` 构造函数是**私有**的，模组无法直接 new；Fabric 的
 * `fabric-menu-api-v1` 提供了 [ExtendedMenuType]（公开构造，且能把额外数据从服务端同步到客户端），
 * 我们用它把"方块坐标"随打开界面的包一起发过去。
 */
object ModMenus {
	val TRANSMUTATION_CORE: ExtendedMenuType<TransmutationCoreMenu, BlockPos> = register("transmutation_core") {
		ExtendedMenuType(
			{ containerId, inventory, pos -> TransmutationCoreMenu(containerId, inventory, pos) },
			BlockPos.STREAM_CODEC,
		)
	}

	private fun <T : MenuType<*>> register(path: String, factory: () -> T): T {
		val key: ResourceKey<MenuType<*>> = ResourceKey.create(Registries.MENU, ModRegistry.id(path))
		@Suppress("UNCHECKED_CAST")
		return Registry.register(BuiltInRegistries.MENU, key, factory()) as T
	}

	internal fun bootstrap() {
		ShenzhiMod.logger.debug("已注册 {} 个菜单类型", 1)
	}
}
