package com.tclhotkey.redirect;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

/**
 * 透明占位 Activity。
 *
 * 两个作用：
 *   1. 给系统一个「可启动的入口」，解除 stopped=true
 *      （stopped 的包不会被实例化，无障碍服务也就无法绑定）
 *   2. 顺手尝试把无障碍服务打开 —— 恢复出厂/重装后，只要启动过一次本 app 就能自愈，
 *      不用用户翻设置去找开关。前提是 adb 授予过 WRITE_SECURE_SETTINGS。
 *
 * 起来后立刻 finish，用户完全看不到。
 */
public class LaunchActivity extends Activity {

    private static final String TAG = "HotkeyRedirect";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Log.i(TAG, "LaunchActivity fired");
        try {
            Log.i(TAG, "auto enable result=" + ServiceEnable.tryEnable(this));
        } catch (Throwable t) {
            Log.e(TAG, "auto enable failed", t);
        }
        finish();
        moveTaskToBack(true);
    }
}