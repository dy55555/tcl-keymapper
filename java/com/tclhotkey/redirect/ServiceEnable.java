package com.tclhotkey.redirect;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

/**
 * 无障碍服务的开启状态查询与开启。
 *
 * 两条路：
 *   1. 直接写 Settings.Secure —— 需要 android.permission.WRITE_SECURE_SETTINGS，
 *      这是签名级权限，普通装拿不到，但 adb 可以授予：
 *        adb shell pm grant com.tclhotkey.redirect android.permission.WRITE_SECURE_SETTINGS
 *      授予后本 app 就能自己开启，恢复出厂/重装后也不用翻设置。
 *   2. 写不了就返回 false，由界面跳系统无障碍设置页让用户手动开。
 */
public final class ServiceEnable {

    private ServiceEnable() {
    }

    private static final String TAG = "HotkeyRedirect";

    public static String flat(Context c) {
        return c.getPackageName() + "/" + RedirectAccessibilityService.class.getName();
    }

    /** 服务是否已在系统的启用列表里 */
    public static boolean isOn(Context c) {
        String s = readList(c);
        if (s == null || s.length() == 0) {
            return false;
        }
        String want = flat(c);
        for (String part : s.split(":")) {
            if (want.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String readList(Context c) {
        try {
            return Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        } catch (Throwable t) {
            Log.e(TAG, "read enabled services failed", t);
            return null;
        }
    }

    /**
     * 尝试把本服务写进系统启用列表（追加，不覆盖别的服务）。
     *
     * @return true 写成功且已生效
     */
    public static boolean tryEnable(Context c) {
        if (isOn(c)) {
            return true;
        }
        String want = flat(c);
        String cur = readList(c);
        // 追加而不是覆盖，别把系统语音之类的其他服务挤掉
        String next = (cur == null || cur.trim().length() == 0)
                ? want
                : cur.trim() + ":" + want;

        try {
            // 注意：Settings.Secure.putString 返回 boolean，不是受影响行数
            boolean put = Settings.Secure.putString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next);
            // 总开关也要开
            Settings.Secure.putString(c.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, "1");
            Log.i(TAG, "tryEnable putString ok=" + put + " -> " + next);
        } catch (Throwable t) {
            Log.e(TAG, "tryEnable failed (多半是没有 WRITE_SECURE_SETTINGS)", t);
            return false;
        }

        // 写完了再读一次确认，写不进去的话 putString 也可能不报错
        try {
            Thread.sleep(400);
        } catch (InterruptedException ignore) {
        }
        boolean ok = isOn(c);
        Log.i(TAG, "tryEnable result=" + ok);
        return ok;
    }
}