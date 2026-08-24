package com.jiaozi.sz.xiaomi

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** 小米线性马达振动反馈（精准短振，接近系统手感） */
object Haptic {
    fun tick(context: Context) {
        val vib = getVibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(20)
        }
    }

    fun success(context: Context) {
        val vib = getVibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 18, 40, 18), -1))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(40)
        }
    }

    private fun getVibrator(ctx: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
