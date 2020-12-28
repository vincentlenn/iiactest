package com.iflytek.acptest

import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.icu.text.NumberFormat
import android.os.*
import android.os.SystemClock.sleep
import android.text.TextUtils
import android.util.Log
import android.widget.TabHost
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.google.gson.Gson
import com.iflytek.acptest.utils.ItestHelper
import com.iflytek.acptest.utils.*
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.lang.ArithmeticException
import java.text.SimpleDateFormat
import java.util.*
import java.util.function.ToDoubleBiFunction


class MainActivity : AppCompatActivity() {

    private var thread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_main)
//        MyActivityUI().setContentView(this@MainActivity)
        val tab = findViewById<TabHost>(android.R.id.tabhost)
        tab.setup()
        tab.addTab(tab.newTabSpec("tab1").setIndicator("性能指标", null).setContent(R.id.tab1))
        tab.addTab(tab.newTabSpec("tab2").setIndicator("冷启动时间", null).setContent(R.id.tab2))
        tab.addTab(tab.newTabSpec("tab3").setIndicator("稳定性测试", null).setContent(R.id.tab3))

        findViewById<TextView>(R.id.pck_name).text = pkgName
        findViewById<TextView>(R.id.pck_version).text = getPkgVer(pkgName)
        findViewById<TextView>(R.id.android_version).text = Build.VERSION.RELEASE
        findViewById<TextView>(R.id.sys_version).text = Build.DISPLAY
        findViewById<TextView>(R.id.kernel_version).text = "( ${getKernelVer()} )"
        version.text = "${BuildConfig.BUILD_TYPE} - ${BuildConfig.VERSION_NAME}"

        // 注册广播
        val filter = IntentFilter()
        filter.addAction(ACTION)
        registerReceiver(MyReceiver(), filter)

        // 创建数据存储目录
        Log.i("DEBUG", "app_path: $op_path")
        mkFolder(op_path)
        mkFolder(itest_store_path)
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        curDate = simpleDateFormat.format(Date())
        mkFile(op_path + File.separatorChar + "log-$curDate$fileSuffix")
        FileHandler.logger("perf", "Launch performance test tool.")

        // 启动iTest后台服务
        itestManager.runServer()
        itestManager.startItest()

        launch_btn.setOnClickListener {
            // 获取执行小时数
            val hour: Long = if (TextUtils.isEmpty(hour_setting.text)) {
                hour_setting.hint.toString().toLong()
            } else {
                hour_setting.text.toString().toLong()
            }

            // 获取执行分钟数
            val minute: Long = if (TextUtils.isEmpty(minute_setting.text)) {
                minute_setting.hint.toString().toLong()
            } else {
                minute_setting.text.toString().toLong()
            }

            // 计算单次执行时长 ms
            duration = (hour * 60 + minute) * 60000

            // 获取执行次数
            loop = if (TextUtils.isEmpty(loop_setting.text)) {
                loop_setting.hint.toString().toInt()
            } else {
                loop_setting.text.toString().toInt()
            }
            mkFile(op_path + File.separatorChar + "log-$curDate$fileSuffix")
            FileHandler.logger("perf", "User setting: run $loop times, ${hour * 60 + minute} min for each time, frequency is $frequency_btn_isON, spectrogram is $spectrogram_btn_isON, video is $record_video.")

            // 检查电池信息并判断是否执行测试
            batteryLevel = examiner.batteryLevel(this)!!.toInt()
            batteryStatus = examiner.batteryIsCharging(this)
            if (batteryLevel < 80) {
                FileHandler.logger("perf", "Battery is low, refuse to execute.")
                if (batteryStatus["unPlugged"]!!)
                    showFeedback("当前电量$batteryLevel%，请插上电源，并等待电池充满后再执行")
                else
                    showFeedback("当前电量$batteryLevel%，请等待电池充满后再执行")
            } else {
                duration_bar.requestFocus()
                thread = object: Thread() {
                    override fun run() {
                        super.run()
                        var i = 1
                        loop@ while (i <= loop) {
                            resetBtnStatus()
                            itestManager.monitorStart("cpu", "pss", pkg = pkgName)
                            FileHandler.logger("perf", "iTest begin to monitor.")
                            val startLv = examiner.batteryLevel(this@MainActivity)!!.toInt()
                            entryApp("perf")
//                            try {
//                                sleep(duration)
//                            } catch (e: InterruptedException) {
//                                /**
//                                 * 线程sleep时被中断，sleep方法会抛出异常并清除中断标识位，然后执行后续代码
//                                 */
//                                FileHandler.logger("Target app threw an error at $i of $loop, let`s try again.")
//                                exceptionFlag = true
//                            }
                            val startTime = SystemClock.elapsedRealtime()
                            var coreGroup = mapCpu()
                            while ((SystemClock.elapsedRealtime() - startTime) < duration) {
                                sampleCpuRate(coreGroup)
                                try {
                                    sleep(1000)
                                } catch (e: InterruptedException) {
                                    /**
                                     * 线程sleep时被中断，sleep方法会抛出异常并清除中断标识位，然后执行后续代码
                                     */
                                    FileHandler.logger("perf", "Target app threw an error at $i of $loop, let`s try again.")
                                    exceptionFlag = true
                                    break
                                }
                            }
                            itestManager.monitorFinish()
                            if (!record_not_begin) {
                                Runtime.getRuntime().exec(recording)
                                sleep(5000)
                            }
//                            exitApp()
                            cmdKill("perf")
                            val endLv = examiner.batteryLevel(this@MainActivity)!!.toInt()
                            resetBtnStatus()
                            if (exceptionFlag) {
                                exceptionFlag = false
                                continue@loop
                            }
                            FileHandler.logger("perf", "Job($i of $loop) is done.")
                            println(coreGroup)
                            FileHandler.copyDirectory(itest_data_path, itest_store_path)
                            curTime = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.CHINA).format(Date())
                            changeItestDirName()
                            changeIflytekDirName()
                            processData(i, startLv, endLv, findMainFreq(coreGroup), coreGroup)
                            i += 1
                        }
                        myHandler.sendEmptyMessage(0)
                    }
                }
                (thread as Thread).start()
            }
        }

        // 校验用户输入的分钟数
        minute_setting.addTextChangedListener {
            val min = minute_setting.text
            if (!min.toString().matches(Regex("""^[0-5]?\d?"""))) {
                minute_setting.text.clear()
            }
        }

        // 校验用户输入的执行次数
        loop_setting.addTextChangedListener {
            val times = loop_setting.text
            if (!times.toString().matches(Regex("""^[1-9]\d*$"""))) {
                loop_setting.text.clear()
            }
        }

        frequency_define.setOnClickListener {
            frequency_default.isChecked = false
            frequency_btn_isON = true
        }

        frequency_default.setOnClickListener {
            frequency_define.isChecked = false
            frequency_btn_isON = false
        }

        spectrogram_on.setOnClickListener {
            spectrogram_off.isChecked = false
            spectrogram_btn_isON = true
        }

        spectrogram_off.setOnClickListener {
            spectrogram_on.isChecked = false
            spectrogram_btn_isON = false
        }

        record_on.setOnClickListener {
            record_off.isChecked = false
            record_video = true
        }

        record_off.setOnClickListener {
            record_on.isChecked = false
            record_video = false
        }

        // 调试按钮
        test_btn.setOnClickListener {
            batteryLevel = examiner.batteryLevel(this)!!.toInt()
            batteryStatus = examiner.batteryIsCharging(this)
            if (batteryStatus["isCharging"]!!) {
                if (batteryStatus["acCharge"]!!) {
                    toast("当前电量$batteryLevel%, 正在使用电源充电")
                } else if (batteryStatus["usbCharge"]!!) {
                    toast("当前电量$batteryLevel%, 正在使用PC充电, 请使用AC电源")
                }
            } else if (batteryLevel < 80) {
                toast("当前电量$batteryLevel%, 请充电")
            } else {
                toast("当前电量$batteryLevel%")
            }

            val p = Runtime.getRuntime().exec("dalvikvm -cp /sdcard/start.dex Assist showLog")
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            var line = ""
            while (true) {
                line = reader.readLine()
                if (line == null) {
                    break
                }
                FileHandler.logger("perf", line)
            }
            p.waitFor()
            p.inputStream.close()
            reader.close()
            p.destroy()
        }

        version.setOnClickListener {
            if (!testBtnOn) {
                test_btn.isVisible = true
                testBtnOn = true
            } else {
                test_btn.isVisible = false
                testBtnOn = false
            }
        }

        // 冷启动时间测试方法1
        btn_method1.setOnClickListener {
            it.isEnabled = false
            if (am_result.text != null) {
                am_result.text = ""
            }
            val tmpFile = op_path + File.separatorChar + "startup_temp_1.txt"
            dltFile(tmpFile)
            mkFile(tmpFile)
            Runtime.getRuntime().exec("logcat -c")
            Runtime.getRuntime().exec("logcat -f $tmpFile -b main -s ActivityManager:I")
            for (i in 1..5) {
                entryApp("coldboot")
                sleep(10000)
                cmdKill("coldboot")
                sleep(1000)
            }
            Runtime.getRuntime().exec("logcat -c")
            var re = ""
            var count = 0
            var sum = 0
            val rf = BufferedReader(FileReader(File(tmpFile)))
            var line = rf.readLine()
            while (line != null) {
                /***
                 * 有时 logcat 输出中的 Displayed 行中会包含一个总时间的附加字段 total
                 * total 时间测量值仅在单个 Activity 的时间和总启动时间之间存在差异时才会显示
                 */
                val l = if (line.contains("total")) {
                    line.substring(line.lastIndexOf("total") + 7, line.lastIndexOf(")")) } else {
                    line.substring(line.lastIndexOf(":") + 3)
                }
                re += "[${count + 1}]: $l "
                var uptime = if (l.substring(0, l.lastIndexOf("ms")).contains("s")) {
                    l.substring(0, l.indexOf("s")).toInt() * 1000 + l.substring(l.indexOf("s") +1, l.lastIndexOf("ms")).toInt()
                } else {
                    l.substring(0, l.lastIndexOf("ms")).toInt()
                }
                sum += uptime
                count ++
                line = rf.readLine()
            }
            try {
                val nf = NumberFormat.getNumberInstance()
                nf.maximumFractionDigits = 2
                am_result.text = re + " [Average]: ${nf.format((sum/count).toFloat())}ms"
            } catch (e: ArithmeticException) {
                am_result.text = re
            }
            it.isEnabled = true
        }

        // 冷启动时间测试方法2
        btn_method2.setOnClickListener {
            it.isEnabled = false
            if (tt_result.text != null) {
                tt_result.text = ""
            }
            val tmpFile = op_path + File.separatorChar + "startup_temp_2.txt"
            dltFile(tmpFile)
            mkFile(tmpFile)
            for (i in 1..5) {
                val p = Runtime.getRuntime().exec("am start -W -S $pkgMainActivity")
                val reader = BufferedReader(InputStreamReader(p.inputStream))
                var line = reader.readLine()
                while (line != null) {
                    if (line.contains("TotalTime:")) {
                        FileHandler.writeContents(tmpFile, line)
                    }
                    line = reader.readLine()
                }
                p.waitFor()
                reader.close()
                p.inputStream.close()
                p.destroy()
            }
            cmdKill("coldboot")
            sleep(1000)
            Runtime.getRuntime().exec("am start com.iflytek.acptest/.MainActivity")
            var re = ""
            var count = 0
            var sum = 0
            val rf = BufferedReader(FileReader(File(tmpFile)))
            var s = rf.readLine()
            while (s != null) {
                val l = s.substring(s.lastIndexOf(":") + 2)
                re += "[${count + 1}]: ${l}ms  "
                sum += l.toInt()
                count ++
                s = rf.readLine()
            }
            try {
                val nf = NumberFormat.getNumberInstance()
                nf.maximumFractionDigits = 2
                tt_result.text = re + "[Average]: ${nf.format((sum/count).toFloat())}ms"
            } catch (e: ArithmeticException) {
                tt_result.text = re
            }
            it.isEnabled = true
        }

        // 稳定性测试按钮
        stable_btn.setOnClickListener {
            it.isEnabled = false
            // 检查设备电量
            batteryLevel = examiner.batteryLevel(this)!!.toInt()
            batteryStatus = examiner.batteryIsCharging(this)
            if (batteryLevel < 100) {
                FileHandler.logger("stable", "Battery is low, refuse to execute.")
                if (batteryStatus["unPlugged"]!!) {
                    showFeedback("当前电量$batteryLevel%，请插上电源，并等待电池充满后再执行")
                } else {
                    showFeedback("当前电量$batteryLevel%，请等待电池充满后再执行")
                }
                it.isEnabled = true
            } else {

            }
        }

    }

    override fun onStop() {
        super.onStop()
        Log.i("UI Thread", "main activity onStop")
        FileHandler.logger("main", "main activity onStop.")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(MyReceiver())
        } catch (e: RuntimeException) {
            e.printStackTrace()
        }
        myHandler.removeMessages(1)
        Log.i("UI Thread", "main activity onDestroy")
        FileHandler.logger("main", "main activity onDestroy.")
    }

    // 启动被测应用
    private fun entryApp(tag: String) {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        intent.component = ComponentName(pkgName, pkgActivity)
        startActivity(intent)
        FileHandler.logger(tag, "Target app is running.")
    }

    // 退出被测应用
    private fun exitApp() {
        FileHandler.logger("perf", "Exit the target activity now.")
        /**
         *  通过新建已存在于栈底的activity,将该activity调到栈顶，
         *  配合它的launch mode=singleTask，将调起的被测应用activity出栈，实现退出被测应用
         */
        val intent = Intent()
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.component = ComponentName(this, ".MainActivity")
        startActivity(intent)
    }

    private fun cmdKill(tag: String) {
        val cmd = "am force-stop $pkgName \n"
        try {
            var process = Runtime.getRuntime().exec(cmd)
//            var out = process.outputStream
//            out.write(cmd.toByteArray())
//            out.flush()
            process.outputStream.close()
            FileHandler.logger(tag, "Exit target app.")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun getPkgUID(pkg: String): Int {
        val mpm = packageManager as PackageManager
        val appInfo = mpm.getApplicationInfo(pkg, 0)
        if (appInfo != null) {
            Log.d("CHECK", appInfo.uid.toString())
            return appInfo.uid
        }
        return -1
    }

    // 获取被测应用的版本信息
    private fun getPkgVer(pkg: String): String {
        val mpm = packageManager as PackageManager
        try {
            val appInfo = mpm.getPackageInfo(pkg, 0)
            if (appInfo != null) {
                return appInfo.versionName
            }
        } catch (e: PackageManager.NameNotFoundException) {
            FileHandler.logger("main", "${e.printStackTrace()}")
            return "Not install"
        }
        return "Unknown"
    }

    private fun processData(index: Int, startLv: Int, endLv: Int, freq: String, map: MutableMap<String, MutableMap<String, Int>>) {
//        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.CHINA)
//        curTime = simpleDateFormat.format(Date())
//        changeItestDirName()
//        changeIflytekDirName()
        val rFileName = op_path + File.separatorChar + "Results-$curTime$fileSuffix"
        mkFile(rFileName)

        val frequencyStatus = if (!frequency_btn_isON) {
            "频率为默认范围"
        } else {
            "频率为最大范围"
        }
        val spectrogramStatus = if (spectrogram_btn_isON) {
            "时频图为开启"
        } else {
            "时频图为关闭"
        }
        val recordStatus = if (record_video) {
            "录像开启"
        } else {
            "不录像"
        }
        val scenario = "本次测试场景配置: $frequencyStatus, $spectrogramStatus, $recordStatus."
        FileHandler.writeContents(rFileName, "$scenario\nThis is the $index of $loop. 开始电量: $startLv, 结束电量: $endLv.")

        if (calEngineTime.cal(rFileName, log_path, curTime)) {
            Log.i("Data processor", "Calculate engine time is done.")
            FileHandler.logger("perf", "Calculate engine time is done.")
            // 统计iTest数据
            dataProcessor.calPerfData(rFileName, curTime)
            Log.i("Data processor", "Calculate performance data is done.")
            FileHandler.logger("perf", "Calculate performance data is done.")
        }

        FileHandler.writeContents(rFileName, freq)

        val obj = Gson().toJson(map)
        FileHandler.writeContents(rFileName, obj.toString())
    }

    private fun isExternalStorageExist(): Boolean {
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            return true
        }
        return false
    }

    private fun mkFolder(path: String) {
        if (isExternalStorageExist()) {
            val dir = File(path)
            if (!dir.exists()) {
                dir.mkdirs()
//                Log.i("Data processor", "Folder $path is created.")
                FileHandler.logger("main", "Folder $path is created.")
            }
        }
    }

    private fun mkFile(fileName: String) {
        val file = File(fileName)
        try {
            if (!file.exists()) {
//                file.parentFile.mkdirs()
                file.createNewFile()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dltFile(fileName: String) {
        val file = File(fileName)
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun changeIflytekDirName() {
        val androidLog = log_path + File.separatorChar + "android"
        val nativeLog = log_path + File.separatorChar + "native"
        if (File(androidLog).exists()) {
            File(androidLog).renameTo(File("$androidLog-$curTime"))
            mkFolder(androidLog)
        }
        if (File(nativeLog).exists()) {
            File(nativeLog).renameTo(File("$nativeLog-$curTime"))
            mkFolder(nativeLog)
        }
    }

    private fun changeItestDirName() {
        val file = File(itest_store_path + File.separatorChar + "handTest")
        if (file.exists()) {
            file.renameTo(File("$file-$curTime"))
        }
    }

    private fun showFeedback(myMessage: String) {
        AlertDialog.Builder(this)
            .setMessage(myMessage)
            .setPositiveButton("OK") { _, _ ->  }.create().show()
    }

    private fun getKernelVer(): String {
        var knVer = ""
        try {
            val br = BufferedReader(InputStreamReader(FileInputStream(File("/proc/version"))))
            val line = br.readLine()

            if (line != null) {
                Log.i("UI Thread", line)
                val index = line.indexOf("#")
                knVer = line.substring(index)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return knVer
        }
        return knVer
    }

    private fun executor(index: Int) {
        itestManager.monitorStart("cpu", "pss", pkg = pkgName)
        FileHandler.logger("perf", "iTest begin to monitor.")
        entryApp("perf")
        Thread.sleep(duration)
        itestManager.monitorFinish()
        FileHandler.copyDirectory(itest_data_path, itest_store_path)
        exitApp()
        val myMessage = Message.obtain()
        val myBundle = Bundle()
        myBundle.putInt("index", index)
        myMessage.data = myBundle
        myMessage.what = 1
        myHandler.sendMessage(myMessage)
    }

    private fun resetBtnStatus() {
        record_not_begin = true
        frequency_btn_clickable = true
        spectrogram_btn_clickable = true
        viewShown = false
    }

    // 恢复默认测试配置
    private fun resetBtnSetting() {
        frequency_default.isChecked = true
        frequency_define.isChecked = false
        frequency_btn_isON = false
        spectrogram_off.isChecked = true
        spectrogram_on.isChecked = false
        spectrogram_btn_isON = false
        record_off.isChecked = true
        record_on.isChecked = false
        record_video = false
    }

    private fun mapCpu(): MutableMap<String, MutableMap<String, Int>> {
        var cores = mutableMapOf<String, MutableMap<String, Int>>()
        val br = BufferedReader(InputStreamReader(FileInputStream(File("/sys/devices/system/cpu/present"))))
        val line = br.readLine()
        val size = line.last().toString().toInt()
        for (i in 0..size) {
            cores["cpu$i"] = mutableMapOf()
        }
        return cores
    }

    private fun sampleCpuRate(cores: MutableMap<String, MutableMap<String, Int>>) {
        cores.forEach { (t, u) ->
            val freq = BufferedReader(InputStreamReader(FileInputStream(File("/sys/devices/system/cpu/$t/cpufreq/scaling_cur_freq")))).readLine()
            if (freq in u.keys) {
                u[freq] = u[freq]!! + 1
            } else {
                u[freq] = 1
            }
        }
    }

    private fun findMainFreq(core: MutableMap<String, MutableMap<String, Int>>): String {
        var line = "\n[CPU主要运行频率]\n"
        core.forEach { (t, u) ->
            line += "$t: ${u.entries.sortedByDescending { it.value }[0].key}\n"
        }
        return line
    }

    val myHandler = object : Handler() {
        @SuppressLint("HandlerLeak")
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
//            println(msg)
            when (msg.what) {
                0 -> {
                    resetBtnSetting()
                    resetBtnStatus()
                    showFeedback("测试执行完毕.")
                }
//                1 -> {
//                    try {
//                        val i = msg.data.getInt("index")
//                        FileHandler.logger("Start to process data")
//                        processData(i)
//                    } catch (e: Throwable) {
//                        e.printStackTrace()
//                    }
//                }
                else -> {
                }
            }
        }
    }

    inner class MyReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action.equals(ACTION)) {
                val state = intent?.getStringExtra("trigger")
                if (state.equals("error")) {
                    FileHandler.logger("perf", "Received broadcast of error trigger.")
                    thread!!.interrupt()
                }
            }
        }
    }

    inner class MyThread(file: String): Thread() {
        private val f = file
        override fun run() {
            super.run()
            Runtime.getRuntime().exec("logcat -c")
            val p = Runtime.getRuntime().exec("logcat -f $f -b main -s ActivityManager:I")
//            val reader = BufferedReader(InputStreamReader(p.inputStream))
//            var line = reader.readLine()
//            while (!currentThread().isInterrupted) {
//                if (line != null && line.contains("Displayed $pkgMainActivity")) {
//                    Log.i("TEST", line)
//                    val startup = line.substring(line.lastIndexOf(":") + 3, line.lastIndexOf("("))
//                    FileHandler.writeContents(f, startup)
//                }
//                line = reader.readLine()
//            }
            p.waitFor()
            p.inputStream.close()
//            reader.close()
            p.destroy()
        }
    }

    companion object {
        const val pkgName = "com.iflytek.acp"
        const val pkgActivity = "$pkgName.EntryActivity"
        const val pkgMainActivity = "$pkgName/.view.AcousticActivity"
        const val fileSuffix = ".txt"
        var curTime = ""
        var curDate = ""
        val op_path = Environment.getExternalStorageDirectory().absolutePath + File.separatorChar + "perfData"
        val itest_store_path = op_path + File.separatorChar + "itest"
        val log_path = Environment.getExternalStorageDirectory().absolutePath + File.separatorChar + pkgName + File.separatorChar + "logs"
        val itest_data_path = Environment.getExternalStorageDirectory().absolutePath + File.separatorChar + "AndroidPropertyTool4" + File.separatorChar + "handTest"
        val itestManager = ItestHelper()
        val dataProcessor = CalPerformance()
        val examiner = ConditionCheck()
        var duration: Long = 0
        var loop: Int = 0
        var batteryLevel = 0 //设备电量
        var batteryStatus: MutableMap<String, Boolean> = mutableMapOf() //电池信息
        const val frequency_btn_id = "function_frequency"
        const val spectrogram_btn_id = "function_spectrogram"
        var record_video = false
        var frequency_btn_isON = false
        var spectrogram_btn_isON = false
        var record_not_begin = true
        var frequency_btn_clickable = true
        var spectrogram_btn_clickable = true
        const val moveIndicator = "input swipe 1700 672 1700 420"
        const val recording = "input keyevent --longpress 135" //DP200使用AI功能键键:135
        const val ACTION = "interrupt signal"
        var viewShown = false
        var exceptionFlag = false
        var testBtnOn = false
    }

}

