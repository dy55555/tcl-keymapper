package com.tclhotkey.redirect;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

/**
 * 免重装改目标入口（adb 专用）。
 *
 * 可改的三个键（--ei key）：
 *   4171  哔哩哔哩键（默认）
 *   4176  奇异果TV键
 *   4168  应用商店键
 *
 * 用法：
 *   查看全部键的当前配置
 *     adb shell am start -n com.tclhotkey.redirect/.SetTargetActivity
 *   改默认键（B站键）
 *     adb shell am start -n .../.SetTargetActivity --es pkg com.xxx.yyy
 *   改指定键
 *     adb shell am start -n .../.SetTargetActivity --ei key 4176 --es pkg com.xxx.yyy
 *   手动指定 Activity
 *     adb shell am start -n .../.SetTargetActivity --es pkg com.x --es act com.x.MainActivity
 *   某个键恢复原厂行为
 *     adb shell am start -n .../.SetTargetActivity --ei key 4176 --es pkg __clear__
 *   全部恢复出厂（只保留 B站键的默认改道）
 *     adb shell am start -n .../.SetTargetActivity --es pkg __reset__
 *
 * 结果走 logcat (tag=HotkeyRedirect)，屏幕上不显示任何东西。
 */
public class SetTargetActivity extends Activity {

    private static final String TAG = "HotkeyRedirect";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            handle(getIntent());
        } catch (Throwable t) {
            Log.e(TAG, "SetTarget failed: " + t);
        } finally {
            // 透明瞬时页，立即销毁，不留任何窗口
            finish();
            overridePendingTransition(0, 0);
        }
    }

    private void handle(android.content.Intent in) {
        if (in == null) {
            dump();
            return;
        }

        String pkg = in.getStringExtra("pkg");
        int key = in.getIntExtra("key", Config.DEF_KEY);

        // 全部恢复出厂
        if ("__reset__".equals(pkg)) {
            Config.resetAll(this);
            Log.i(TAG, "SET_TARGET_OK reset all\n" + Config.describe(this));
            return;
        }

        // 主动关闭：把自己从系统的无障碍服务列表里摘掉。
        // 只删自己那一段，别的服务（比如系统语音服务）必须保留。
        if ("__disable__".equals(pkg)) {
            String cur = android.provider.Settings.Secure.getString(
                    getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String me = ServiceEnable.flat(this);
            StringBuilder sb = new StringBuilder();
            if (cur != null) {
                for (String part : cur.split(":")) {
                    String t = part.trim();
                    if (t.length() == 0 || t.equals(me)) {
                        continue;
                    }
                    if (sb.length() > 0) {
                        sb.append(":");
                    }
                    sb.append(t);
                }
            }
            String now = sb.toString();
            android.provider.Settings.Secure.putString(
                    getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, now);
            Log.i(TAG, "SET_TARGET_OK disabled, services now = " + now);
            return;
        }

        // 只查不写
        if (pkg == null) {
            dump();
            return;
        }

        // 某个键恢复原厂行为
        if ("__clear__".equals(pkg)) {
            Config.clear(this, key);
            Log.i(TAG, "SET_TARGET_OK " + Config.keyName(key)
                    + " -> (原厂)\n" + Config.describe(this));
            return;
        }

        String err = Config.setTarget(this, key, pkg, in.getStringExtra("act"));
        if (err != null) {
            Log.e(TAG, "SET_TARGET_FAIL " + err);
            setResult(RESULT_CANCELED, new android.content.Intent().putExtra("error", err));
            return;
        }
        Log.i(TAG, "SET_TARGET_OK " + Config.keyName(key)
                + " -> " + pkg + "\n" + Config.describe(this));
        setResult(RESULT_OK);
    }

    private void dump() {
        Log.i(TAG, "SET_TARGET_RESULT\n" + Config.describe(this));
    }
}