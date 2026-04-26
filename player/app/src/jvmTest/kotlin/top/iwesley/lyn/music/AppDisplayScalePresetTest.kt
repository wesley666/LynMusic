package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.appDisplayScalePresetOrDefault
import top.iwesley.lyn.music.core.model.effectiveAppDisplayDensity
import top.iwesley.lyn.music.core.model.navidromeAudioQualityOrDefault

class AppDisplayScalePresetTest {
    @Test
    fun `preset scales stay fixed`() {
        assertEquals(0.9f, AppDisplayScalePreset.Compact.scale)
        assertEquals(1.0f, AppDisplayScalePreset.Default.scale)
        assertEquals(1.1f, AppDisplayScalePreset.Large.scale)
    }

    @Test
    fun `invalid preset name falls back to default`() {
        assertEquals(AppDisplayScalePreset.Default, appDisplayScalePresetOrDefault(null))
        assertEquals(AppDisplayScalePreset.Default, appDisplayScalePresetOrDefault("unknown"))
        assertEquals(AppDisplayScalePreset.Compact, appDisplayScalePresetOrDefault("Compact"))
    }

    @Test
    fun `effective density multiplies base density by preset scale`() {
        assertEquals(2.7f, effectiveAppDisplayDensity(3f, AppDisplayScalePreset.Compact), 0.0001f)
        assertEquals(3.0f, effectiveAppDisplayDensity(3f, AppDisplayScalePreset.Default), 0.0001f)
        assertEquals(3.3f, effectiveAppDisplayDensity(3f, AppDisplayScalePreset.Large), 0.0001f)
    }

    @Test
    fun `navidrome audio quality names and labels stay fixed`() {
        assertEquals(null, NavidromeAudioQuality.Original.maxBitRateKbps)
        assertEquals(320, NavidromeAudioQuality.Kbps320.maxBitRateKbps)
        assertEquals(192, NavidromeAudioQuality.Kbps192.maxBitRateKbps)
        assertEquals(128, NavidromeAudioQuality.Kbps128.maxBitRateKbps)
        assertEquals("原始", navidromeAudioQualityLabel(NavidromeAudioQuality.Original))
        assertEquals("320kbps", navidromeAudioQualityLabel(NavidromeAudioQuality.Kbps320))
    }

    @Test
    fun `invalid navidrome audio quality name falls back to supplied default`() {
        assertEquals(
            NavidromeAudioQuality.Kbps192,
            navidromeAudioQualityOrDefault(null, NavidromeAudioQuality.Kbps192),
        )
        assertEquals(
            NavidromeAudioQuality.Kbps128,
            navidromeAudioQualityOrDefault("unknown", NavidromeAudioQuality.Kbps128),
        )
        assertEquals(
            NavidromeAudioQuality.Kbps320,
            navidromeAudioQualityOrDefault("Kbps320", NavidromeAudioQuality.Kbps192),
        )
    }
}
