package com.iflytek.acptest.utils

import android.text.TextUtils.join

class ItestHelper {

    //启动iTest后台服务
    fun runServer() {
        val p = Runtime.getRuntime().exec("dalvikvm -cp /sdcard/start.dex Start")
        p.waitFor()
        println("iTest is prepared.")
        FileHandler.logger("iTest is prepared.")
    }

    //启动iTest
    fun startItest() {
        /**
         * iTest启动首页的命令，如果应用未获得系统签名时调用则需带上参数：--user 0
         */
        Runtime.getRuntime().exec("am start --user 0 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n iflytek.testTech.propertytool/.activity.BootActivity")
        println("Launch iTest.")
        FileHandler.logger("Launch iTest.")
    }

    //开启iTest监控
    fun monitorStart(vararg args: String, pkg: String) {
        operateFloatWindow(1)
        /**
         * iTest开启监控的命令，如果应用未获得系统签名时调用则需带上参数：--user 0
         * adb shell am broadcast -a monitorStart --user 0 --es monitor cpu,pss,net,battery,cpuTemp,fps,response --es pkg com.example.test --ei interval 1000
         * monitor:监控指标
         * pkg:包名
         * interval:间隔(毫秒)
         */
        val items = join(",", args)
        Runtime.getRuntime().exec("am broadcast --user 0 -a monitorStart --es monitor $items --es pkg $pkg --ei interval 1000")
        println("iTest has started monitoring: $items.")
        FileHandler.logger("iTest has started monitoring: $items.")
    }

    //结束监控
    fun monitorFinish() {
        Runtime.getRuntime().exec("am broadcast --user 0 -a monitorFinish")
        println("Finish the iTest monitor.")
        FileHandler.logger("Finish the iTest monitor.")
    }

    //开启/关闭悬浮窗
    private fun operateFloatWindow(switch: Int) {
        when (switch) {
            0 -> Runtime.getRuntime().exec("am broadcast --user 0 -a disableFloatWindow")
            1 -> Runtime.getRuntime().exec("am broadcast --user 0 -a enableFloatWindow")
        }
    }

}