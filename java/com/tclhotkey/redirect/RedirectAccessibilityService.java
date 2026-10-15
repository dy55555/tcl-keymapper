package com.tclhotkey.redirect;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;

/**
 * 遥控器 B站键改键服务。
 *
 * 链路：IR 扫描码 0x0fdb -> 内核 keycode 551 -> Generic.kl 映射成安卓 keyCode 4171
 *      -> 本服务 onKeyEvent 拦截 -> 启动目标 app（默认 dev.frost819.newbv）
 *
 * 只拦 4171，其余按键一律放行，不影响遥控器其他功能。
 * 目标包名/Activity 存在 SharedPreferences，改目标不用重装 APK。
 */
public class RedirectAccessibilityService extends AccessibilityService {

    private static final String TAG = "HotkeyRedirect";

    // 实际拦哪些键看 Config.KEYS，这里不再单独定义一份 —— 两处数字对不上是隐患。

    /**
     * 记一条状态日志到应用私有目录。
     *
     * 为什么不用 Log 就完事：TCL ROM 会过滤第三方 Log.i，落到文件才看得见。
     * 但这个文件**只在服务连上/出错时写一次**，而且是覆盖写不是追加 ——
     * 不能让每按一次遥控器就往磁盘写一行，那是纯诊断噪音，还会无限涨。
     */
    private void trace(String msg) {
        Log.i(TAG, msg);
        try {
            File f = new File(getDir("trace", MODE_PRIVATE), "trace.log");
            Writer w = new FileWriter(f, false);
            w.write(System.currentTimeMillis() + " " + msg + "\n");
            w.close();
        } catch (Throwable t) {
            Log.e(TAG, "trace file failed: " + t);
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        trace("service connected, watching keyCodes=" + java.util.Arrays.toString(Config.KEYS));
        try {
            android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) {
                info = new android.accessibilityservice.AccessibilityServiceInfo();
            }
            info.eventTypes = android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            info.feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                    | android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.notificationTimeout = 100;
            setServiceInfo(info);
            trace("setServiceInfo ok, capabilities=" + info.getCapabilities());
        } catch (Throwable t) {
            trace("setServiceInfo failed: " + t);
        }
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
        // 不处理事件，只要按键
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event == null) {
            return false;
        }
        int code = event.getKeyCode();

        // 只处理支持改的三个键；其余一律放行，不打扰遥控器其他功能
        if (!Config.isSupported(code)) {
            return false;
        }

        // 该键没配目标 -> 放行，走电视原厂行为
        if (!Config.isSet(this, code)) {
            return false;
        }

        // 只认 ACTION_UP（抬起）触发一次，避免按下/抬起各启动一遍
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return true;   // 按下这下吃掉，不让原厂逻辑跑
        }

        // 按键路径不写日志：每次按键都开关一次文件既吵又会无限涨，
        // 真要查原因看这一条就够了 —— 配了目标却没跳转，多半是目标 app 没装。
        String pkg = Config.pkg(this, code);
        String act = Config.act(this, code);
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClassName(pkg, act);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            i.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            startActivity(i);
        } catch (Throwable t) {
            Log.e(TAG, "redirect " + code + " -> " + pkg + " failed: " + t);
        }
        return true;   // 吃掉抬起事件
    }

}
