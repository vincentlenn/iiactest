package com.iflytek.acptest

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.iflytek.acptest.MainActivity.Companion.ACTION
import com.iflytek.acptest.MainActivity.Companion.frequency_btn_clickable
import com.iflytek.acptest.MainActivity.Companion.frequency_btn_id
import com.iflytek.acptest.MainActivity.Companion.frequency_btn_isON
import com.iflytek.acptest.MainActivity.Companion.pkgName
import com.iflytek.acptest.MainActivity.Companion.record_not_begin
import com.iflytek.acptest.MainActivity.Companion.record_video
import com.iflytek.acptest.MainActivity.Companion.recording
import com.iflytek.acptest.MainActivity.Companion.moveIndicator
import com.iflytek.acptest.MainActivity.Companion.spectrogram_btn_clickable
import com.iflytek.acptest.MainActivity.Companion.spectrogram_btn_id
import com.iflytek.acptest.MainActivity.Companion.spectrogram_btn_isON
import com.iflytek.acptest.MainActivity.Companion.viewShown
import com.iflytek.acptest.utils.FileHandler
import org.jetbrains.anko.toast


class RobService : AccessibilityService() {
  @SuppressLint("SwitchIntDef")
  override fun onAccessibilityEvent(event: AccessibilityEvent) {
//      Log.i("ACCESSIBILITY", "$event")
    when (event.eventType) {
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
//                Log.i("ACCESSIBILITY", "$event")
//                FileHandler.logger(event.toString())
            if (event.packageName == pkgName && viewShown) {
                if (!foundView(event, "global_loading")) {
                    if (frequency_btn_isON && frequency_btn_clickable) {
                        if (tryClickBtn(frequency_btn_id)) {
                            frequency_btn_clickable = false
                            // 模拟拖动频率调整控件上滑块
                            execCmd(moveIndicator)
                        }
                    }
                    if (spectrogram_btn_isON && spectrogram_btn_clickable) {
                        if (tryClickBtn(spectrogram_btn_id)) {
                            spectrogram_btn_clickable = false
                        }
                    }
                    if (record_video && record_not_begin) {
                        record_not_begin = false
//                            sleep(1000)
                        // 模拟长按拍照键
                        execCmd(recording)
                        if (foundView(event, "record_state")) {
                            record_not_begin = false
                        }
                    }
                }
            }
        }
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
//                Log.i("ACCESSIBILITY", "$event")
//                FileHandler.logger(event.toString())
            when (event.packageName) {
                pkgName -> {
                    if (event.text.toString().contains("系统运行异常") || event.text.toString().contains("功能使用异常")) {
                        FileHandler.logger("main","Caught the alert dialog: ${event.text}.")
                        val intent = Intent(ACTION)
                        intent.putExtra("trigger", "error")
                        sendBroadcast(intent)
                    }
                    if (event.className == "com.iflytek.acp.view.AcousticActivity") { //DP200: com.iflytek.acp.view.AcousticActivity, DP100: com.iflytek.iiac.MainActivity
                        viewShown = true
                    }
                }
                "iflytek.testTech.propertytool" -> {
                    if (event.text.toString() == "[iTest]" && event.className.toString() == "iflytek.testTech.propertytool.activity.PermissionDialogActivity") {
                        Runtime.getRuntime().exec("input keyevent 4")
                        FileHandler.logger("main","Simulate to click BACK.")
                    }
                }
            }
        }
        AccessibilityEvent.TYPE_VIEW_CLICKED -> {
//                Log.i("ACCESSIBILITY", "$event")
            FileHandler.logger("main","${event.text} is clicked.")
            if (event.text.toString().contains("频率范围：")) {
//                execCmd("input keyevent --longpress 135")
            }

        }
        AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
//                Log.i("ACCESSIBILITY", "$event,\neventType:${event.eventType}")
//                FileHandler.logger(event.toString())
        }
    }
  }

  private fun execCmd(cmd: String) {
    Runtime.getRuntime().exec(cmd)
    FileHandler.logger("main","Simulate to take photo/video.")
  }

  private fun handleNotification(event: AccessibilityEvent) {
    val texts = event.text
    if (texts.isNotEmpty()) {
      for (text in texts) {
        val content = text.toString()
        println(content)
        if (content == "Let`s start monitoring.") {
          Runtime.getRuntime().exec("am broadcast --user 0 -a enableFloatWindow")
        }
      }
    }
  }

  // 模拟点击虚拟按键
  private fun tryClickBtn(id: String): Boolean {
    val root = rootInActiveWindow
    try {
      val node = root.findAccessibilityNodeInfosByViewId("$pkgName:id/$id")
      if (!node.isNullOrEmpty()) {
        node[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
        FileHandler.logger("main","${node[0].viewIdResourceName.split("/")[1]} button is clicked.")
        return true
      }
    } catch (e: NullPointerException) {
      FileHandler.logger("main","Throw exception when try to click button: \n$e")
    }
    return false
  }

  private fun foundView(event: AccessibilityEvent, id: String): Boolean {
    val root = rootInActiveWindow
    try {
      val node = root.findAccessibilityNodeInfosByViewId("$pkgName:id/$id")
      if (!node.isNullOrEmpty()) {
        FileHandler.logger("main","${event.eventType} Found the view: ${node[0].viewIdResourceName.split("/")[1]}")
        return true
      }
    } catch (e: NullPointerException) {
      FileHandler.logger("main","Throw exception when find view: $e")
    }
    return false
  }

  override fun onInterrupt() {
  }

}