package com.iflytek.acptest.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static android.content.ContentValues.TAG;

public class javaKillApp {

    public static void killPackage(String pkg, Context context) {
        Log.d("KILL", "Start trying to kill app");

        try {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            activityManager.killBackgroundProcesses(pkg);
//            activityManager.forceStopPackage(pkg);
            Log.d("KILL", "Kill background: " + pkg + " successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("KILL", "Kill " + pkg + " error!");
        }

//        try {
//            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
//            Method forceStopPackage = activityManager.getClass().getDeclaredMethod("forceStopPackage", String.class);
//            forceStopPackage.setAccessible(true);
//            forceStopPackage.invoke(activityManager, pkg);
//        } catch (NoSuchMethodException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        } catch (IllegalArgumentException e) {
//            e.printStackTrace();
//        } catch (InvocationTargetException e) {
//            e.printStackTrace();
//        }
    }
}

