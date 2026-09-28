package com.xin.shenzhimod.registry

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.sounds.SoundEvent

/**
 * 音效注册（模块 M9 使用）。
 *
 * ⚠️ 当前只注册 `SoundEvent`，**尚未提供 `.ogg` 音频文件与 `sounds.json`**——
 * 这是刻意的：在 M9 之前没有任何代码播放它们，所以既不会崩也不会刷警告。
 * 加音效时要同时补 `assets/shenzhimod/sounds.json` 与 `assets/shenzhimod/sounds` 目录下的 ogg 文件。
 */
object ModSounds {
	val DRAGON_HEAD_ABSORB: SoundEvent = register("dragon_head_absorb")
	val DRAGON_HEAD_CRAFT: SoundEvent = register("dragon_head_craft")
	val TRANSMUTATION_AMBIENT: SoundEvent = register("transmutation_ambient")
	val TRANSMUTATION_FINISH: SoundEvent = register("transmutation_finish")
	val ALTAR_CHARGE: SoundEvent = register("altar_charge")
	val ALTAR_COMPLETE: SoundEvent = register("altar_complete")

	val ALL: List<SoundEvent> =
		listOf(DRAGON_HEAD_ABSORB, DRAGON_HEAD_CRAFT, TRANSMUTATION_AMBIENT, TRANSMUTATION_FINISH, ALTAR_CHARGE, ALTAR_COMPLETE)

	private fun register(path: String): SoundEvent {
		val key: ResourceKey<SoundEvent> = ResourceKey.create(Registries.SOUND_EVENT, ModRegistry.id(path))
		return Registry.register(BuiltInRegistries.SOUND_EVENT, key, SoundEvent.createVariableRangeEvent(ModRegistry.id(path)))
	}
}
