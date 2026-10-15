package com.tclhotkey.redirect;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/**
 * 主界面：显示在电视应用列表里，用遥控器操作。
 *
 * 三个键各一行，显示当前指向。选中某行按确定键 -> 进 AppPickerActivity 挑应用。
 *
 * 版面结构：
 *   root(竖)
 *     ├ top(横，占满中间)           ── 遥控器图跟标题同一水平线起步
 *     │   ├ col(竖) 标题 / 提示 / 警告条 / 键列表
 *     │   └ 遥控器图 remote.png
 *     ├ 版权（整屏居中）
 *     ├ 操作说明（整屏居中）
 *     └ 免责声明按钮（整屏居中）
 *
 * 底下三行故意挂在 root 而不是 col 上：挂 col 的话它们只会在"列表那一栏"里居中，
 * 会被右边的遥控器图挤得偏左。
 */
public class MainActivity extends Activity {

    private static final int REQ_PICK = 100;
    private KeyAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // 补一次「自愈」写设置。
        // 起因：只有 LaunchActivity 会去写系统无障碍启用列表，主界面原来只读不写。
        // 覆盖安装（adb install -r / 点 APK 装）之后系统会把本服务从启用列表里踢掉，
        // 用户从电视桌面点进改键器却什么都不生效，得重启电视才行。
        // 在这里补一次写，从桌面打开一次就恢复。已启用时 isOn() 直接返回，不会多等。
        try {
            ServiceEnable.tryEnable(this);
        } catch (Throwable ignore) {
        }
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        refreshStatus();
    }

    /** 本服务的无障碍是否真的开了。没开的话改配置也不会生效 */
    private boolean serviceOn() {
        return ServiceEnable.isOn(this);
    }

    private void refreshStatus() {
        View box = findViewById(101);
        TextView warn = (TextView) findViewById(102);
        if (box == null || warn == null) {
            return;
        }
        if (serviceOn()) {
            box.setVisibility(View.GONE);
        } else {
            warn.setText(getString(R.string.warn_not_enabled));
            box.setVisibility(View.VISIBLE);
        }
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    /** 撑满整行宽度，让内容以整个界面为基准水平居中 */
    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF14141E);
        root.setPadding(dp(48), dp(32), dp(48), dp(32));

        // ---- 中间那一大块：左边文字和列表，右边遥控器图 ----
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(0, 0, 0, dp(16));
        col.addView(title);

        TextView sub = new TextView(this);
        sub.setText(R.string.main_hint);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        sub.setTextColor(0xFF9AA0B4);
        sub.setPadding(0, 0, 0, dp(8));
        col.addView(sub);

        // 无障碍服务没开的话，改了配置也不生效。
        // 用「深底+ 琥珀色竖条 + 浅琥珀字」，醒目但不刺眼，也不用大红底吓人。
        LinearLayout warnBox = new LinearLayout(this);
        warnBox.setId(101);
        warnBox.setOrientation(LinearLayout.HORIZONTAL);
        warnBox.setBackgroundColor(0xFF2A2620);          // 暖灰深底
        warnBox.setPadding(dp(14), dp(12), dp(14), dp(12));
        warnBox.setVisibility(View.GONE);

        View bar = new View(this);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(5), ViewGroup.LayoutParams.MATCH_PARENT);
        bar.setLayoutParams(barLp);
        bar.setBackgroundColor(0xFFE8A33D);              // 琥珀色竖条
        warnBox.addView(bar);

        TextView warn = new TextView(this);
        warn.setId(102);
        warn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        warn.setTextColor(0xFFF2D9B0);                   // 浅琥珀字，柔和
        warn.setLineSpacing(dp(3), 1f);
        warn.setPadding(dp(12), 0, 0, 0);
        warnBox.addView(warn);

        col.addView(warnBox);

        ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(0x22FFFFFF));
        list.setDividerHeight(1);
        list.setSelector(new ColorDrawable(0xFF2F5FA8));
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        adapter = new KeyAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                // 最后一项是「全部恢复出厂」
                if (pos == Config.KEYS.length) {
                    Config.resetAll(MainActivity.this);
                    adapter.notifyDataSetChanged();
                    return;
                }
                Intent i = new Intent(MainActivity.this, AppPickerActivity.class);
                i.putExtra("key", Config.KEYS[pos]);
                startActivityForResult(i, REQ_PICK);
            }
        });
        col.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        top.addView(col, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        // 遥控器图：740x1280 竖图，自带三个键的红虚线框。
        // 背景是 (20,20,30)，跟主界面底色 0xFF14141E 完全一致，贴上去看不出边界。
        // FIT_START = 按原图比例缩到装得下，然后**贴顶对齐**；
        // 用 FIT_CENTER 会被竖直居中，看着掉在很下面，跟标题对不上。
        ImageView remote = new ImageView(this);
        remote.setImageResource(R.drawable.remote);
        remote.setScaleType(ImageView.ScaleType.FIT_START);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                dp(230), ViewGroup.LayoutParams.MATCH_PARENT);
        // 上边距 dp(45) = 标题那一行的高度，正好把图的上沿压到标题行盒的下沿（y≈115）。
        // 为什么非要这个边距：remote.png 最顶上就是「开机键」三个字（原图 y=5 起，缩放后
        // 距图上沿约 2px），图又只有 740x1280 这一种尺寸，没法单独挪那几个字。
        // 所以只能整体下移 —— 让「开机键」的字顶（≈117）落在「按键改键器」字底（≈102）下面。
        rlp.setMargins(dp(28), dp(45), 0, 0);
        top.addView(remote, rlp);

        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ---- 底部三行：挂在 root 上，以整个界面为基准居中 ----
        TextView about = new TextView(this);
        about.setText(R.string.about);
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        about.setTextColor(0xFF8A90A4);
        about.setGravity(Gravity.CENTER);
        about.setPadding(0, dp(14), 0, 0);
        root.addView(about, fullWidth());

        TextView foot = new TextView(this);
        foot.setText(R.string.main_foot);
        foot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        foot.setTextColor(0xFF6E7488);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(6), 0, 0);
        root.addView(foot, fullWidth());

        // 免责声明入口
        LinearLayout discRow = new LinearLayout(this);
        discRow.setOrientation(LinearLayout.HORIZONTAL);
        discRow.setGravity(Gravity.CENTER);
        discRow.setPadding(0, dp(12), 0, 0);

        TextView discBtn = new TextView(this);
        discBtn.setText(R.string.disc_btn);
        discBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        discBtn.setTextColor(0xFFFFC24D);
        discBtn.setGravity(Gravity.CENTER);
        discBtn.setBackgroundColor(0x33FFC24D);
        discBtn.setPadding(dp(22), dp(8), dp(22), dp(8));
        discBtn.setFocusable(true);
        discBtn.setFocusableInTouchMode(true);
        discBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DisclaimerDialog.show(MainActivity.this);
            }
        });
        discBtn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackgroundColor(hasFocus ? 0xFF2F5FA8 : 0x33FFC24D);
            }
        });
        discRow.addView(discBtn);
        root.addView(discRow, fullWidth());

        setContentView(root);
    }

    /** 把包名翻译成应用名，没装就显示包名 */
    String labelOf(String pkg) {
        if (pkg == null || pkg.length() == 0) {
            return getString(R.string.original);
        }
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence l = pm.getApplicationLabel(ai);
            if (l != null && l.length() > 0) {
                return l.toString();
            }
        } catch (Throwable ignore) {
        }
        return pkg;
    }

    private class KeyAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return Config.KEYS.length + 1;   // 三个键 + 恢复出厂
        }

        @Override
        public Object getItem(int pos) {
            return null;
        }

        @Override
        public long getItemId(int pos) {
            return pos;
        }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            TextView tv = cv instanceof TextView ? (TextView) cv : null;
            if (tv == null) {
                tv = new TextView(MainActivity.this);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                tv.setTextColor(0xFFFFFFFF);
                tv.setPadding(dp(20), dp(18), dp(20), dp(18));
                tv.setBackgroundColor(Color.TRANSPARENT);
            }
            if (pos == Config.KEYS.length) {
                tv.setText(getString(R.string.reset_all));
                tv.setTextColor(0xFFFFB4A2);
            } else {
                int k = Config.KEYS[pos];
                String name = Config.KEY_NAMES[pos];
                if (Config.isSet(MainActivity.this, k)) {
                    tv.setText(name + "\n  → " + labelOf(Config.pkg(MainActivity.this, k)));
                    tv.setTextColor(0xFF8FE3B0);
                } else {
                    tv.setText(name + "\n  → " + getString(R.string.original));
                    tv.setTextColor(0xFF9AA0B4);
                }
            }
            return tv;
        }
    }
}
