package com.silnoone.chagok.demo

import android.content.Context

/** The public demo does not schedule jobs or request notification permissions. */
object ReminderJob {
    fun schedule(@Suppress("UNUSED_PARAMETER") context: Context) = Unit
    fun cancel(@Suppress("UNUSED_PARAMETER") context: Context) = Unit
}
