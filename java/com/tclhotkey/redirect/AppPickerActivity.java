package com.tclhotkey.redirect;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 应用选择界面：列出电视上所有可启动的应用，选一个作为某个键的目标。
 * 第一项是「恢复原厂行为」。
 */
public class AppPickerActivity extends Activity {

    private int key;
    private final List<Entry> items = new ArrayList<Entry>();
    private EntryAdapter adapter;

    static class Entry {
        String pkg;
        String act;
        String label;
        Drawable icon;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        key = getIntent() != null ? getIntent().getIntExtra("key", Config.DEF_KEY) : Config.DEF_KEY;
        loadApps();
        buildUi();
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    /** 收集 LAUNCHER 和 LEANBACK_LAUNCHER 两类入口，去重后按名称排序 */
    private void loadApps() {
        items.clear();

        // 特殊目标：恢复原厂行为，固定排第一项、不带图标
        Entry clear = new Entry();
        clear.pkg = null;
        clear.label = getString(R.string.picker_clear);
        clear.icon = null;
        items.add(clear);

        PackageManager pm = getPackageManager();
        Set<String> seen = new HashSet<String>();
        List<ResolveInfo> all = new ArrayList<ResolveInfo>();

        Intent q1 = new Intent(Intent.ACTION_MAIN);
        q1.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> r1 = pm.queryIntentActivities(q1, 0);
        if (r1 != null) {
            all.addAll(r1);
        }

        Intent q2 = new Intent(Intent.ACTION_MAIN);
        q2.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> r2 = pm.queryIntentActivities(q2, 0);
        if (r2 != null) {
            all.addAll(r2);
        }

        for (ResolveInfo ri : all) {
            if (ri.activityInfo == null || ri.activityInfo.packageName == null) {
                continue;
            }
            String pkg = ri.activityInfo.packageName;
            // 不选自己
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) {
                continue;
            }
            Entry e = new Entry();
            e.pkg = pkg;
            e.act = ri.activityInfo.name;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                CharSequence l = pm.getApplicationLabel(ai);
                e.label = (l != null && l.length() > 0) ? l.toString() : pkg;
                e.icon = pm.getApplicationIcon(ai);
            } catch (Throwable t) {
                e.label = pkg;
            }
            items.add(e);
        }

        // 第一项是特殊项（恢复原厂），之后的应用按名称排序
        if (items.size() > 1) {
            List<Entry> rest = new ArrayList<Entry>(items.subList(1, items.size()));
            Collections.sort(rest, new Comparator<Entry>() {
                @Override
                public int compare(Entry a, Entry b) {
                    return String.valueOf(a.label).compareToIgnoreCase(String.valueOf(b.label));
                }
            });
            items.subList(1, items.size()).clear();
            items.addAll(rest);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF14141E);
        root.setPadding(dp(48), dp(28), dp(48), dp(28));

        TextView title = new TextView(this);
        title.setText(getString(R.string.picker_title, Config.keyName(key)));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(0, 0, 0, dp(18));
        root.addView(title);

        ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(0x22FFFFFF));
        list.setDividerHeight(1);
        list.setSelector(new ColorDrawable(0xFF2F5FA8));
        adapter = new EntryAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Entry e = items.get(pos);
                if (e.pkg == null) {
                    Config.clear(AppPickerActivity.this, key);
                } else {
                    Config.setTarget(AppPickerActivity.this, key, e.pkg, e.act);
                }
                finish();
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView foot = new TextView(this);
        foot.setText(R.string.picker_foot);
        foot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        foot.setTextColor(0xFF6E7488);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(16), 0, 0);
        root.addView(foot);

        setContentView(root);
    }

    private class EntryAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int pos) {
            return items.get(pos);
        }

        @Override
        public long getItemId(int pos) {
            return pos;
        }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            Entry e = items.get(pos);

            LinearLayout row = (cv instanceof LinearLayout) ? (LinearLayout) cv : null;
            ImageView icon;
            TextView name;
            TextView pkg;

            if (row == null) {
                row = new LinearLayout(AppPickerActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dp(16), dp(12), dp(16), dp(12));
                row.setGravity(Gravity.CENTER_VERTICAL);

                icon = new ImageView(AppPickerActivity.this);
                icon.setId(1);
                int s = dp(44);
                row.addView(icon, new LinearLayout.LayoutParams(s, s));

                LinearLayout texts = new LinearLayout(AppPickerActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(dp(16), 0, 0, 0);

                name = new TextView(AppPickerActivity.this);
                name.setId(2);
                name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                name.setTextColor(0xFFFFFFFF);
                texts.addView(name);

                pkg = new TextView(AppPickerActivity.this);
                pkg.setId(3);
                pkg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                pkg.setTextColor(0xFF8A90A4);
                texts.addView(pkg);

                row.addView(texts);
            } else {
                icon = (ImageView) row.findViewById(1);
                name = (TextView) row.findViewById(2);
                pkg = (TextView) row.findViewById(3);
            }

            name.setText(e.label);
            if (e.pkg == null) {
                pkg.setText("");
                icon.setImageDrawable(null);
                name.setTextColor(0xFFFFB4A2);
            } else {
                pkg.setText(e.pkg);
                icon.setImageDrawable(e.icon);
                // 当前已生效的那个高亮成绿色
                boolean cur = e.pkg.equals(Config.pkg(AppPickerActivity.this, key));
                name.setTextColor(cur ? 0xFF8FE3B0 : 0xFFFFFFFF);
            }
            return row;
        }
    }
}