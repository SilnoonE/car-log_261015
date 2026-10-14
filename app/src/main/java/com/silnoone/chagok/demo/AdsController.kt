package com.silnoone.chagok.demo

import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

enum class AdPlacement { RECORD_SAVED, STATS_ENTERED }

/** UI integration seam only. No ad SDK, network request, IDs or monetization logic. */
class AdsController(
    @Suppress("UNUSED_PARAMETER") activity: AppCompatActivity,
    @Suppress("UNUSED_PARAMETER") host: FrameLayout,
    @Suppress("UNUSED_PARAMETER") disabled: Boolean = true
) {
    val privacyOptionsRequired = false
    fun start() = Unit
    fun resume() = Unit
    fun pause() = Unit
    fun destroy() = Unit
    fun transition(@Suppress("UNUSED_PARAMETER") placement: AdPlacement) = Unit
    fun privacyOptions() = Unit
}
