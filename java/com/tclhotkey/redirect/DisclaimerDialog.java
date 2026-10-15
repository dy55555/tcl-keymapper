package com.tclhotkey.redirect;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 免责声明弹窗。
 *
 * 三段固定文案（一、关于这个应用 / 二、关于打赏 / 三、源码地址）都放在
 * res/values/strings.xml 的 disc_* 里，改文案不用动这个文件。
 *
 * 收款码图片是**可选**的：
 *   把 qr_wechat.png / qr_alipay.png 丢进 res/drawable/ 重新打包就会显示；
 *   没放的话自动降级成一行提示文字，不会崩、也不会编译不过。
 *   （用的是 getIdentifier 运行时查找，所以加图不用改一行代码。）
 *
 * 纯展示：不联网、不接任何支付 SDK、不需要任何密钥，断网也能用。
 *
 * 高度预算：电视是 1920x1080 / 240dpi，即 1dp = 1sp = 1.5px。
 *   面板宽 min(dp(1000), 屏宽-dp(80)) = 1500px，正文一行约 58 个汉字。
 *
 *   ★ 实测（2026-10-11，电视上 uiautomator dump + 截图逐像素量）：
 *     三个正文段各排 3 / 3 / 1 行，单行高约 39.5px（系统字体行高是 1.458×字号，
 *     不是 Windows 上那套 msyh 的 1.36×，所以早先的效果图把总高估小了）。
 *     第一版收款码 190dp=285px 时内容总高 **1081px**，超过窗口上限 1015px，
 *     ScrollView 会自动上滚 36px 去露出「关闭」按钮 —— 结果标题被切掉一截。
 *
 *   ⇒ 现在的取值把内容压到 ≈982px：
 *     窗口上限 = 屏高 × 0.94 = 1015px，内容 982px 装得下，不滚动、不留大片空白。
 *     收款码 152dp=228px（实测降到 160px 都还扫得出，228px 很富裕）。
 *   ⚠️ 再改文案的话记得回量一遍；ScrollView + 「窗口取内容高与上限的较小值」
 *      这层兜底一直都在，超了能滚、不超就没有多余空白。
 */
public final class DisclaimerDialog {

    private DisclaimerDialog() {
    }

    private static final int C_PANEL = 0xFF14141E;
    private static final int C_AMBER = 0xFFFFC24D;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_SOFT = 0xFF9AA0B4;
    private static final int C_SOFT_DIM = 0xFF6E7488;
    private static final int C_LINK = 0xFF7FC4FF;
    private static final int C_KEY = 0x1FFFFFFF;
    private static final int C_KEY_FOCUS = 0xFF2F5FA8;

    private static int dp(Activity a, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                a.getResources().getDisplayMetrics());
    }

    /** 找不到就返回 0，界面自动降级 */
    private static int drawableId(Activity a, String name) {
        try {
            return a.getResources().getIdentifier(name, "drawable", a.getPackageName());
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void show(final Activity act) {
        final Dialog d = new Dialog(act);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_PANEL);
        root.setPadding(dp(act, 36), dp(act, 20), dp(act, 36), dp(act, 12));

        TextView title = new TextView(act);
        title.setText(R.string.disc_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTextColor(C_AMBER);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(act, 12));
        root.addView(title);

        // 一、关于这个应用 —— 原文（开头那句适用范围是 2026-10-11 后补的）
        section(act, root, R.string.disc_p1_head, R.string.disc_p1_body, C_SOFT, 8);
        // 二、关于打赏 —— 用户提供的原文，一字未改
        section(act, root, R.string.disc_p2_head, R.string.disc_p2_body, C_SOFT, 8);
        // 三、源码地址 —— 说明和地址**排在同一行**（用户要求，2026-10-11）
        // 两行并一行省下 51px 高度；地址那一段用 Spannable 单独染成浅蓝色，
        // 既保持「一行」又还看得出是链接。
        TextView h3 = new TextView(act);
        h3.setText(R.string.disc_p3_head);
        h3.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        h3.setTextColor(C_AMBER);
        h3.setPadding(0, 0, 0, dp(act, 6));
        root.addView(h3);

        SpannableStringBuilder line3 = new SpannableStringBuilder();
        line3.append(act.getString(R.string.disc_p3_body));
        int linkAt = line3.length();
        line3.append(act.getString(R.string.disc_repo));
        line3.setSpan(new ForegroundColorSpan(C_LINK), linkAt, line3.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        TextView b3 = new TextView(act);
        b3.setText(line3);
        b3.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b3.setTextColor(C_SOFT);
        b3.setLineSpacing(dp(act, 3), 1f);
        b3.setPadding(0, 0, 0, dp(act, 12));
        root.addView(b3);

        int wxId = drawableId(act, "qr_wechat");
        int aliId = drawableId(act, "qr_alipay");
        boolean two = wxId != 0 && aliId != 0;

        LinearLayout qrRow = new LinearLayout(act);
        qrRow.setOrientation(LinearLayout.HORIZONTAL);
        qrRow.setGravity(Gravity.CENTER);

        if (wxId == 0 && aliId == 0) {
            TextView ph = new TextView(act);
            ph.setText(R.string.disc_noimg);
            ph.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            ph.setTextColor(C_SOFT_DIM);
            ph.setGravity(Gravity.CENTER);
            ph.setBackgroundColor(0x14FFFFFF);
            ph.setPadding(dp(act, 20), dp(act, 40), dp(act, 20), dp(act, 40));
            qrRow.addView(ph, new LinearLayout.LayoutParams(
                    dp(act, 360), ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            int sw = act.getResources().getDisplayMetrics().widthPixels;
            // 152dp -> 228px。收款码只中间一小块是自定义图标，剩下全是原始模块，
            // 实测降到 160px 都还扫得出来，228px 在电视上完全够用。
            // 再大就会把弹窗顶到 1081px、超过窗口上限 1015px，ScrollView 一滚标题就被切。
            int side = Math.min(dp(act, two ? 152 : 270), sw / (two ? 6 : 3));
            if (wxId != 0) {
                qrRow.addView(cell(act, R.string.disc_wechat, wxId, side));
            }
            if (aliId != 0) {
                qrRow.addView(cell(act, R.string.disc_alipay, aliId, side));
            }
        }
        root.addView(qrRow, centered());

        // 版权 + 作者邮箱：主界面底部那行同一条字符串（R.string.about），只写一处
        TextView author = new TextView(act);
        author.setText(R.string.about);
        author.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        author.setTextColor(C_SOFT_DIM);
        author.setGravity(Gravity.CENTER);
        author.setPadding(0, dp(act, 10), 0, 0);
        root.addView(author, centered());

        final TextView close = new TextView(act);
        close.setText(R.string.disc_close);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        close.setTextColor(C_TEXT);
        close.setGravity(Gravity.CENTER);
        close.setBackgroundColor(C_KEY);
        close.setFocusable(true);
        close.setFocusableInTouchMode(true);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        close.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackgroundColor(hasFocus ? C_KEY_FOCUS : C_KEY);
            }
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(act, 150), dp(act, 48));
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        clp.setMargins(0, dp(act, 8), 0, 0);
        root.addView(close, clp);

        // 正文是整段文字，必须给死宽度让它换行；不然 LinearLayout 会按单行宽度撑开
        int sw = act.getResources().getDisplayMetrics().widthPixels;
        int panelW = Math.min(dp(act, 1000), sw - dp(act, 80));
        int maxH = (int) (act.getResources().getDisplayMetrics().heightPixels * 0.94f);

        ScrollView sv = new ScrollView(act);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(root);
        d.setContentView(sv);

        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // 先按面板宽度量一次内容高度，再取「内容高」和「屏高 94%」里的小值当窗口高。
            // 内容装得下 -> 窗口正好包住内容，没有多余空白；
            // 装不下 -> 窗口封顶，ScrollView 自己会滚。
            root.measure(View.MeasureSpec.makeMeasureSpec(panelW, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            w.setLayout(panelW, Math.min(root.getMeasuredHeight(), maxH));
        }
        d.setCanceledOnTouchOutside(true);
        d.show();

        // 焦点落到「关闭」，不然遥控器方向键在弹窗里没有起点
        close.requestFocus();
        // 兜底：万一以后文案加长、内容顶到窗口上限，requestFocus 会让 ScrollView
        // 自动上滚去露出「关闭」，标题就被切没了。这里强制回到顶部，
        // 至少「免责声明」四个字和第一段是完整的，用户按方向键再往下看。
        // 内容装得下时这句是个空操作。
        sv.post(new Runnable() {
            @Override
            public void run() {
                sv.scrollTo(0, 0);
            }
        });
    }

    /** 小标题 + 一整段正文 */
    private static void section(Activity act, LinearLayout root,
                                int headRes, int bodyRes, int bodyColor, int bottomDp) {
        TextView h = new TextView(act);
        h.setText(headRes);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        h.setTextColor(C_AMBER);
        h.setPadding(0, 0, 0, dp(act, 6));
        root.addView(h);

        TextView b = new TextView(act);
        b.setText(bodyRes);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTextColor(bodyColor);
        b.setLineSpacing(dp(act, 3), 1f);
        b.setPadding(0, 0, 0, dp(act, bottomDp));
        root.addView(b);
    }

    private static LinearLayout.LayoutParams centered() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
    }

    /** 一张收款码 + 底下的小字标签 */
    private static View cell(Activity act, int labelRes, int resId, int side) {
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        ImageView iv = new ImageView(act);
        iv.setImageResource(resId);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // 收款码是白底黑块，深色面板上必须自己垫一块白，不然扫不出来
        iv.setBackgroundColor(0xFFFFFFFF);
        int p = dp(act, 6);
        iv.setPadding(p, p, p, p);
        col.addView(iv, new LinearLayout.LayoutParams(side, side));

        TextView lb = new TextView(act);
        lb.setText(labelRes);
        lb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        lb.setTextColor(C_SOFT);
        lb.setGravity(Gravity.CENTER);
        lb.setPadding(0, dp(act, 8), 0, 0);
        col.addView(lb);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // 两张收款码之间的间距（用户要求拉开一点）：每边 30dp=45px，中间合计 90px
        lp.setMargins(dp(act, 30), 0, dp(act, 30), 0);
        col.setLayoutParams(lp);
        return col;
    }
}
