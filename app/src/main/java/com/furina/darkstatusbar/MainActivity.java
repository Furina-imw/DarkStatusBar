package com.furina.darkstatusbar;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;

/** Màn hình cài đặt: kéo thanh trượt, thay đổi áp dụng ngay lên thanh trạng thái. */
public class MainActivity extends Activity {

    private SharedPreferences sp;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable notifyRunnable = new Runnable() {
        @Override
        public void run() {
            getContentResolver().notifyChange(ConfigProvider.URI, null);
        }
    };

    private final ArrayList<SeekBar> bars = new ArrayList<>();
    private final ArrayList<Integer> barDefaults = new ArrayList<>();
    private Switch swEnabled;
    private Switch swNumber;
    private Switch swSkipDark;
    private Switch swSkipLand;
    private Switch swShade;
    private Switch swApp;
    private TextView pvIcons;
    private TextView pvNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences(ConfigProvider.PREFS, MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(32));
        scroll.addView(root);
        setContentView(scroll);

        root.addView(label("Dark Status Bar", 24));
        root.addView(label("Chỉnh độ mờ icon thanh trạng thái. Thay đổi áp dụng ngay.", 14));

        swEnabled = new Switch(this);
        swEnabled.setText("Bật hiệu ứng");
        swEnabled.setChecked(sp.getBoolean(ConfigProvider.K_ENABLED, ConfigProvider.D_ENABLED));
        swEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                sp.edit().putBoolean(ConfigProvider.K_ENABLED, checked).apply();
                changed();
            }
        });
        root.addView(swEnabled);

        swSkipDark = new Switch(this);
        swSkipDark.setText("Không làm mờ khi thanh trạng thái sáng (icon đen)");
        swSkipDark.setChecked(sp.getBoolean(ConfigProvider.K_SKIP_DARK, ConfigProvider.D_SKIP_DARK));
        swSkipDark.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                sp.edit().putBoolean(ConfigProvider.K_SKIP_DARK, checked).apply();
                changed();
            }
        });
        root.addView(swSkipDark);

        swSkipLand = new Switch(this);
        swSkipLand.setText("Tự tắt làm mờ khi xoay ngang");
        swSkipLand.setChecked(sp.getBoolean(ConfigProvider.K_SKIP_LAND, ConfigProvider.D_SKIP_LAND));
        swSkipLand.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                sp.edit().putBoolean(ConfigProvider.K_SKIP_LAND, checked).apply();
                changed();
            }
        });
        root.addView(swSkipLand);

        // Xem trước (xấp xỉ, màu thật phụ thuộc nền thanh trạng thái)
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.rgb(0x27, 0x4A, 0x7B));
        box.setPadding(dp(16), dp(20), dp(16), dp(20));
        pvIcons = new TextView(this);
        pvIcons.setTextSize(20);
        pvIcons.setText("▂▄▆  Wi-Fi  6,5 KB/s   ");
        pvNumber = new TextView(this);
        pvNumber.setTextSize(20);
        pvNumber.setText("57");
        pvNumber.setPadding(dp(14), dp(2), dp(14), dp(2));
        box.addView(pvIcons);
        box.addView(pvNumber);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.topMargin = dp(12);
        boxLp.bottomMargin = dp(8);
        root.addView(box, boxLp);

        root.addView(label("Icon thường (Wi-Fi, sóng, tốc độ mạng...)", 17));
        addSlider(root, "Độ đục", ConfigProvider.K_ICON_ALPHA, 100,
                ConfigProvider.D_ICON_ALPHA, "%");
        addSlider(root, "Độ sáng xám", ConfigProvider.K_ICON_GRAY, 255,
                ConfigProvider.D_ICON_GRAY, "");

        root.addView(label("Màu pin khi sạc / pin yếu", 17));
        addSlider(root, "Độ đục", ConfigProvider.K_FILL_ALPHA, 100,
                ConfigProvider.D_FILL_ALPHA, "%");
        addSlider(root, "Độ sáng màu", ConfigProvider.K_FILL_BRIGHT, 100,
                ConfigProvider.D_FILL_BRIGHT, "%");

        root.addView(label("Số phần trăm trong pin", 17));
        swNumber = new Switch(this);
        swNumber.setText("Làm mờ số phần trăm");
        swNumber.setChecked(sp.getBoolean(ConfigProvider.K_NUM_DIM, ConfigProvider.D_NUM_DIM));
        swNumber.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                sp.edit().putBoolean(ConfigProvider.K_NUM_DIM, checked).apply();
                changed();
            }
        });
        root.addView(swNumber);
        addSlider(root, "Độ đục", ConfigProvider.K_NUM_ALPHA, 100,
                ConfigProvider.D_NUM_ALPHA, "%");
        addSlider(root, "Độ sáng số", ConfigProvider.K_NUM_BRIGHT, 100,
                ConfigProvider.D_NUM_BRIGHT, "%");

        root.addView(label("Icon ứng dụng trên thanh thông báo", 17));
        swApp = new Switch(this);
        swApp.setText("Hiện icon ứng dụng màu (kiểu Android)");
        swApp.setChecked(sp.getBoolean(ConfigProvider.K_APP_ON, ConfigProvider.D_APP_ON));
        swApp.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                sp.edit().putBoolean(ConfigProvider.K_APP_ON, checked).apply();
                changed();
            }
        });
        root.addView(swApp);
        addSlider(root, "Độ đục", ConfigProvider.K_APP_ALPHA, 100,
                ConfigProvider.D_APP_ALPHA, "%");
        addSlider(root, "Độ sáng", ConfigProvider.K_APP_BRIGHT, 100,
                ConfigProvider.D_APP_BRIGHT, "%");
        root.addView(label("Trung tâm thông báo", 17));
        swShade = new Switch(this);
        swShade.setText("Icon ứng dụng vuông bo góc trong thẻ thông báo");
        swShade.setChecked(sp.getBoolean(ConfigProvider.K_SHADE_ON, ConfigProvider.D_SHADE_ON));
        swShade.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                sp.edit().putBoolean(ConfigProvider.K_SHADE_ON, checked).apply();
                changed();
            }
        });
        root.addView(swShade);
        root.addView(label("Icon ứng dụng khi thanh trạng thái sáng (icon đen)", 17));
        addSlider(root, "Độ đục (100% = không mờ)", ConfigProvider.K_APPL_ALPHA, 100,
                ConfigProvider.D_APPL_ALPHA, "%");
        addSlider(root, "Độ sáng", ConfigProvider.K_APPL_BRIGHT, 100,
                ConfigProvider.D_APPL_BRIGHT, "%");

        Button reset = new Button(this);
        reset.setText("Đặt lại mặc định");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetAll();
            }
        });
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resetLp.topMargin = dp(20);
        root.addView(reset, resetLp);

        root.addView(label("Nếu thanh trạng thái không đổi: bật module trong LSPosed, "
                + "chọn phạm vi System UI rồi khởi động lại System UI.", 13));

        updatePreview();
    }

    private void addSlider(LinearLayout parent, final String title, final String key,
                           int max, int def, final String unit) {
        final TextView text = label("", 15);
        final SeekBar bar = new SeekBar(this);
        bar.setMax(max);
        int cur = sp.getInt(key, def);
        bar.setProgress(cur);
        text.setText(title + ": " + cur + unit);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                sp.edit().putInt(key, progress).apply();
                text.setText(title + ": " + progress + unit);
                changed();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        bars.add(bar);
        barDefaults.add(def);
        parent.addView(text);
        parent.addView(bar);
    }

    private void resetAll() {
        sp.edit().clear().apply();
        swEnabled.setChecked(ConfigProvider.D_ENABLED);
        swNumber.setChecked(ConfigProvider.D_NUM_DIM);
        swSkipDark.setChecked(ConfigProvider.D_SKIP_DARK);
        swSkipLand.setChecked(ConfigProvider.D_SKIP_LAND);
        swShade.setChecked(ConfigProvider.D_SHADE_ON);
        swApp.setChecked(ConfigProvider.D_APP_ON);
        for (int i = 0; i < bars.size(); i++) {
            bars.get(i).setProgress(barDefaults.get(i));
        }
        changed();
    }

    private void changed() {
        updatePreview();
        handler.removeCallbacks(notifyRunnable);
        handler.postDelayed(notifyRunnable, 80);
    }

    private void updatePreview() {
        if (pvIcons == null || pvNumber == null) return;
        boolean enabled = sp.getBoolean(ConfigProvider.K_ENABLED, ConfigProvider.D_ENABLED);

        int iconA = percent(sp.getInt(ConfigProvider.K_ICON_ALPHA, ConfigProvider.D_ICON_ALPHA));
        int gray = sp.getInt(ConfigProvider.K_ICON_GRAY, ConfigProvider.D_ICON_GRAY);
        int iconColor = enabled ? Color.argb(iconA, gray, gray, gray) : Color.WHITE;

        int fillColor = Color.rgb(0x2E, 0xCC, 0x71);
        int numColor = Color.WHITE;
        if (enabled) {
            fillColor = scaled(0x2E, 0xCC, 0x71,
                    sp.getInt(ConfigProvider.K_FILL_BRIGHT, ConfigProvider.D_FILL_BRIGHT),
                    sp.getInt(ConfigProvider.K_FILL_ALPHA, ConfigProvider.D_FILL_ALPHA));
            if (sp.getBoolean(ConfigProvider.K_NUM_DIM, ConfigProvider.D_NUM_DIM)) {
                numColor = scaled(255, 255, 255,
                        sp.getInt(ConfigProvider.K_NUM_BRIGHT, ConfigProvider.D_NUM_BRIGHT),
                        sp.getInt(ConfigProvider.K_NUM_ALPHA, ConfigProvider.D_NUM_ALPHA));
            }
        }

        pvIcons.setTextColor(iconColor);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(8));
        shape.setColor(fillColor);
        pvNumber.setBackground(shape);
        pvNumber.setTextColor(numColor);
    }

    private static int percent(int p) {
        return Math.round(Math.max(0, Math.min(100, p)) * 255f / 100f);
    }

    private static int scaled(int r, int g, int b, int brightPct, int alphaPct) {
        float k = Math.max(0, Math.min(100, brightPct)) / 100f;
        return Color.argb(percent(alphaPct),
                Math.round(r * k), Math.round(g * k), Math.round(b * k));
    }

    private TextView label(String s, int sizeSp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setPadding(0, dp(12), 0, dp(2));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
