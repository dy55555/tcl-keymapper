package com.tclhotkey.redirect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机后拉起无障碍服务。
 *
 * 无障碍服务正常情况下会由系统自动绑定，但两点需要兜底：
 *   1. 包若因故处于 stopped 状态，系统不会实例化它
 *   2. 部分 ROM 厂商 ROM 会主动禁用后启的无障碍服务
 * 这里只做「确保包不是 stopped」，不重复设置 enabled_accessibility_services。
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "HotkeyRedirect";

    @Override
    public void onReceive(Context ctx, Intent in) {
        if (in == null || in.getAction() == null) {
            return;
        }
        if (!Intent.ACTION_BOOT_COMPLETED.equals(in.getAction())
                && !"android.intent.action.QUICKBOOT_POWERON".equals(in.getAction())
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(in.getAction())) {
            return;
        }
        try {
            Intent i = new Intent(ctx, LaunchActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            Log.i(TAG, "boot: kicked LaunchActivity to clear stopped state");
        } catch (Throwable t) {
            Log.e(TAG, "boot kick failed: " + t);
        }
    }
}