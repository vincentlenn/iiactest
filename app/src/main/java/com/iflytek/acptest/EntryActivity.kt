package com.iflytek.acptest

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.text.TextUtils
import android.text.TextUtils.SimpleStringSplitter
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import com.iflytek.acptest.utils.ItestHelper


class EntryActivity : AppCompatActivity() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
    }

    private val permissionArray = arrayOf(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(null, "Entry activity create")
//        requestPermission()
    }

    override fun onStart() {
        super.onStart()
        requestPermission()
    }

    private fun requestPermission() {
        Log.i(null, "Request permission start")
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkStealFeature("com.iflytek.acptest/com.iflytek.acptest.RobService")) {
                if (haveAllPermission()) {
                    Log.i(null, "We have storage io permission , start main activity")
                    startMainWorkFlow()
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        permissionArray,
                        PERMISSION_REQUEST_CODE
                    )
                }
            } else {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
        } else {
            startMainWorkFlow()
        }
    }

    private fun grantAccessibility() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    }

    private fun haveAllPermission(): Boolean {
        permissionArray.forEach {
            if (PermissionChecker.checkSelfPermission(this, it) != PermissionChecker.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (i in permissions.indices) {
                if (grantResults[i] != PermissionChecker.PERMISSION_GRANTED) {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) { //用户选择了禁止不再询问
                        AlertDialog.Builder(this)
                            .setTitle("permission")
                            .setMessage("点击允许才可以使用应用")
                            .setPositiveButton("去允许") { _, _ ->
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                val uri = Uri.fromParts("package", packageName, null)
                                intent.data = uri
                                startActivityForResult(intent, PERMISSION_REQUEST_CODE)
                            }
                            .create()
                            .show()
                        return
                    } else { //选择禁止
                        AlertDialog.Builder(this)
                            .setTitle("permission")
                            .setMessage("点击允许才可以使用应用")
                            .setPositiveButton("去允许") { _, _ ->
                                ActivityCompat.requestPermissions(
                                    this,
                                    arrayOf(
                                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                        android.Manifest.permission.READ_PHONE_STATE,
                                        android.Manifest.permission.CAMERA
                                    ),
                                    PERMISSION_REQUEST_CODE
                                )
                            }
                            .create()
                            .show()
                        return
                    }
                }
            }
            // 用户允许了所有权限
            Log.i(null, "We get all permissions, start main activity")
            startMainWorkFlow()
        }
    }

    fun isAccessibilitySettingsOn(mContext: Context, clazz: Class<out AccessibilityService?>): Boolean {
        var accessibilityEnabled = 0
        val service: String = mContext.getPackageName().toString() + "/" + clazz.canonicalName
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                mContext.getApplicationContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: SettingNotFoundException) {
            e.printStackTrace()
        }
        val mStringColonSplitter = SimpleStringSplitter(':')
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                mContext.getApplicationContext().getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    if (accessibilityService.equals(service, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun checkStealFeature(service: String): Boolean {
        var ok = 0
        try {
            ok = Settings.Secure.getInt(applicationContext.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (e: SettingNotFoundException) {
            e.printStackTrace()
        }

        Log.i("CHECK", "Int for accessibility enabled: ${ok.toString()}")
        val ms = TextUtils.SimpleStringSplitter(':')
        if (ok == 1) {
            val settingValue = Settings.Secure.getString(
                applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            Log.i("CHECK", "enabled accessibility services: $settingValue")
            if (settingValue != null) {
                ms.setString(settingValue)
                while (ms.hasNext()) {
                    var accessibilityService = ms.next()
                    if (accessibilityService == service) {
                        return true;
                    }
                }
            }
        }
        return false
    }

    private fun startMainWorkFlow() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        // 去除activity切换动画
        overridePendingTransition(0, 0)
        // 结束掉自己
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(null, "entry activity destroy")
    }

}