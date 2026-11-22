package com.furina.darkstatusbar;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * Cầu nối cấu hình: app lưu giá trị vào SharedPreferences, còn hook trong System UI
 * đọc lại qua ContentResolver.call(URI, "get"). Mỗi lần thay đổi, app gọi notifyChange(URI)
 * để hook nạp lại và áp dụng ngay.
 */
public class ConfigProvider extends ContentProvider {

    public static final String AUTHORITY = "com.furina.darkstatusbar.cfg";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    public static final String PREFS = "cfg";

    // Khóa và giá trị mặc định (hằng số biên dịch, dùng chung cho app và hook).
    public static final String K_ENABLED = "enabled";
    public static final boolean D_ENABLED = true;

    public static final String K_ICON_ALPHA = "icon_alpha";   // 0..100 (%)
    public static final int D_ICON_ALPHA = 40;
    public static final String K_ICON_GRAY = "icon_gray";     // 0..255
    public static final int D_ICON_GRAY = 128;

    public static final String K_FILL_ALPHA = "fill_alpha";   // 0..100 (%)
    public static final int D_FILL_ALPHA = 30;
    public static final String K_FILL_BRIGHT = "fill_bright"; // 0..100 (%)
    public static final int D_FILL_BRIGHT = 70;

    public static final String K_NUM_DIM = "num_dim";
    public static final boolean D_NUM_DIM = true;
    public static final String K_NUM_ALPHA = "num_alpha";     // 0..100 (%)
    public static final int D_NUM_ALPHA = 33;
    public static final String K_NUM_BRIGHT = "num_bright";   // 0..100 (%)
    public static final int D_NUM_BRIGHT = 60;

    public static final String K_APP_ON = "app_on";
    public static final boolean D_APP_ON = true;
    public static final String K_APP_ALPHA = "app_alpha";     // 0..100 (%)
    public static final int D_APP_ALPHA = 55;
    public static final String K_APP_BRIGHT = "app_bright";   // 0..100 (%)
    public static final int D_APP_BRIGHT = 60;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!"get".equals(method) || getContext() == null) return null;
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Bundle b = new Bundle();
        b.putBoolean(K_ENABLED, sp.getBoolean(K_ENABLED, D_ENABLED));
        b.putInt(K_ICON_ALPHA, sp.getInt(K_ICON_ALPHA, D_ICON_ALPHA));
        b.putInt(K_ICON_GRAY, sp.getInt(K_ICON_GRAY, D_ICON_GRAY));
        b.putInt(K_FILL_ALPHA, sp.getInt(K_FILL_ALPHA, D_FILL_ALPHA));
        b.putInt(K_FILL_BRIGHT, sp.getInt(K_FILL_BRIGHT, D_FILL_BRIGHT));
        b.putBoolean(K_NUM_DIM, sp.getBoolean(K_NUM_DIM, D_NUM_DIM));
        b.putInt(K_NUM_ALPHA, sp.getInt(K_NUM_ALPHA, D_NUM_ALPHA));
        b.putInt(K_NUM_BRIGHT, sp.getInt(K_NUM_BRIGHT, D_NUM_BRIGHT));
        b.putBoolean(K_APP_ON, sp.getBoolean(K_APP_ON, D_APP_ON));
        b.putInt(K_APP_ALPHA, sp.getInt(K_APP_ALPHA, D_APP_ALPHA));
        b.putInt(K_APP_BRIGHT, sp.getInt(K_APP_BRIGHT, D_APP_BRIGHT));
        return b;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
