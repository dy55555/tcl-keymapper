package com.tclhotkey.redirect;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 多键改键配置。
 *
 * 遥控器上可改的三个专用键（红框那排），安卓 keyCode 由 Generic.kl 从内核扫描码映射而来：
 *   4171  哔哩哔哩键   内核 551 (0x227)
 *   4176  奇异果TV键  内核 558 (0x22e)
 *   4168  应用商店键  内核 548 (0x224)
 *
 * 设计原则：
 *   只有「配置了目标」的键才拦截；没配置的键直接放行，保持电视原厂行为。
 *   这样用户只改想改的键，其他键完全不受影响。
 */
public final class Config {

    private Config() {
    }

    private static final String SP_NAME = "hotkey_redirect";

    /** 支持改的三个键 */
    public static final int[] KEYS = {4171, 4176, 4168};

    /** 各键的中文名，日志里好看懂 */
    public static final String[] KEY_NAMES = {"哔哩哔哩键", "奇异果TV键", "应用商店键"};

    /** 默认键：不带 --ei key 时操作这个 */
    public static final int DEF_KEY = 4171;

    /**
     * 恢复出厂 = 清空全部配置，一个键都不拦。
     * 清空后按键不经过本服务，电视自己打开原厂目标（B站键就是云视听小电视）。
     *
     * ⚠️ 这里曾经往 B站键写死过 new BV，那是错的 —— 那等于把改道固化成了出厂值。
     */
    public static void resetAll(Context c) {
        sp(c).edit().clear().apply();
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    public static String keyName(int key) {
        return nameOf(key) + "(" + key + ")";
    }

    private static String nameOf(int key) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i] == key) {
                return KEY_NAMES[i];
            }
        }
        return "未知键";
    }

    public static boolean isSupported(int key) {
        for (int k : KEYS) {
            if (k == key) {
                return true;
            }
        }
        return false;
    }

    /** 该键是否配置了目标。未配置就放行，走原厂逻辑 */
    public static boolean isSet(Context c, int key) {
        String p = sp(c).getString("k" + key + ".pkg", null);
        String a = sp(c).getString("k" + key + ".act", null);
        return p != null && p.length() > 0 && a != null && a.length() > 0;
    }

    public static String pkg(Context c, int key) {
        return sp(c).getString("k" + key + ".pkg", "");
    }

    public static String act(Context c, int key) {
        return sp(c).getString("k" + key + ".act", "");
    }

    /** 清掉某个键的配置，恢复该键的原厂行为 */
    public static void clear(Context c, int key) {
        sp(c).edit().remove("k" + key + ".pkg").remove("k" + key + ".act").apply();
    }

    /**
     * 写入目标。只传包名时自动查启动入口。
     *
     * @return null 成功；否则返回错误信息
     */
    public static String setTarget(Context c, int key, String pkg, String act) {
        if (!isSupported(key)) {
            return "不支持的按键 " + key + "，可用: " + java.util.Arrays.toString(KEYS);
        }
        if (pkg == null || pkg.trim().length() == 0) {
            return "缺少包名参数 --es pkg";
        }
        pkg = pkg.trim();

        android.content.pm.PackageManager pm = c.getPackageManager();
        try {
            // 没装会抛 NameNotFoundException，不会返回 null，所以只接异常就够
            pm.getApplicationInfo(pkg, 0);
        } catch (Exception e) {
            return "未安装: " + pkg;
        }

        if (act == null || act.trim().length() == 0) {
            act = resolveLauncher(pm, pkg);
            if (act == null) {
                return "找不到启动入口，请手动指定 --es act";
            }
        } else {
            act = act.trim();
            try {
                pm.getActivityInfo(new android.content.ComponentName(pkg, act), 0);
            } catch (Exception e) {
                return "Activity 不存在: " + act;
            }
        }

        sp(c).edit()
                .putString("k" + key + ".pkg", pkg)
                .putString("k" + key + ".act", act)
                .apply();
        return null;
    }

    /** 依次尝试 LAUNCHER 和 LEANBACK_LAUNCHER（TV 应用常只注册后者） */
    private static String resolveLauncher(android.content.pm.PackageManager pm, String pkg) {
        android.content.Intent probe =
                new android.content.Intent(android.content.Intent.ACTION_MAIN);
        probe.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
        probe.setPackage(pkg);
        android.content.pm.ResolveInfo ri = pm.resolveActivity(probe, 0);

        if (ri == null || ri.activityInfo == null) {
            probe = new android.content.Intent(android.content.Intent.ACTION_MAIN);
            probe.addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER);
            probe.setPackage(pkg);
            ri = pm.resolveActivity(probe, 0);
        }
        return (ri != null && ri.activityInfo != null) ? ri.activityInfo.name : null;
    }

    /** 全部键的当前状态，多行文本 */
    public static String describe(Context c) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < KEYS.length; i++) {
            int k = KEYS[i];
            sb.append(KEY_NAMES[i]).append(" -> ");
            if (isSet(c, k)) {
                sb.append(pkg(c, k));
            } else {
                sb.append("(原厂)");
            }
            if (i < KEYS.length - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
