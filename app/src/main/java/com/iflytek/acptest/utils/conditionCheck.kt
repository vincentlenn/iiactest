package com.iflytek.acptest.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.iflytek.acptest.MainActivity

class ConditionCheck {
    fun batteryLevel(context: MainActivity): Float? {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val batteryPct: Float? = batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        }
        return batteryPct
    }

    fun batteryIsCharging(context: Context): MutableMap<String, Boolean> {
        var info: MutableMap<String, Boolean> = mutableMapOf()
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        info["isCharging"] = status == BatteryManager.BATTERY_STATUS_CHARGING
        // How are we charging?
        val chargePlug: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        info["unPlugged"] = chargePlug == 0
        info["usbCharge"] = chargePlug == BatteryManager.BATTERY_PLUGGED_USB
        info["acCharge"] = chargePlug == BatteryManager.BATTERY_PLUGGED_AC
        return info
    }

}