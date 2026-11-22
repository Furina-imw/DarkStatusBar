package com.furina.darkstatusbar;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook System UI:
 * 1) Ép icon thanh trạng thái luôn "dark" và đổi màu theo cấu hình.
 * 2) Làm mờ màu pin (sạc/pin yếu/...) và số phần trăm theo cấu hình.
 * Cấu hình đọc từ app qua ConfigProvider, thay đổi áp dụng ngay.
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "[DarkStatusBar] ";
    private static final String TARGET_PKG = "com.android.systemui";
    private static final String DISPATCHER =
            "com.android.systemui.statusbar.phone.DarkIconDispatcherImpl";
    private static final String ICON_VIEW =
            "com.android.systemui.statusbar.views.MiuiBatteryMeterIconView";
    private static final String HOLLOW_VIEW =
            "com.android.systemui.statusbar.views.MiuiHollowBatteryMeterIconView";

    private static final String ICON_COLOR_FIELD = "mDarkModeIconColorSingleTone";
    private static final String NUMBER_FIELD = "batteryLevelWhite";

    private static final Uri CFG_URI = Uri.parse("content://com.furina.darkstatusbar.cfg");

    // ---- Cấu hình ----

    private static final class Config {
        boolean enabled = ConfigProvider.D_ENABLED;
        int iconColor = (pct(ConfigProvider.D_ICON_ALPHA) << 24)
                | (ConfigProvider.D_ICON_GRAY << 16)
                | (ConfigProvider.D_ICON_GRAY << 8)
                | ConfigProvider.D_ICON_GRAY;
        int fillAlpha = pct(ConfigProvider.D_FILL_ALPHA);
        float fillBright = ConfigProvider.D_FILL_BRIGHT / 100f;
        boolean numDim = ConfigProvider.D_NUM_DIM;
        int numAlpha = pct(ConfigProvider.D_NUM_ALPHA);
        float numBright = ConfigProvider.D_NUM_BRIGHT / 100f;
        boolean skipDark = ConfigProvider.D_SKIP_DARK;
        boolean skipLand = ConfigProvider.D_SKIP_LAND;
        boolean appOn = ConfigProvider.D_APP_ON;
        float appAlpha = ConfigProvider.D_APP_ALPHA / 100f;
        float appBright = ConfigProvider.D_APP_BRIGHT / 100f;
    }

    private static volatile Config cfg = new Config();

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int pct(int p) {
        return Math.round(clamp(p, 0, 100) * 255f / 100f);
    }

    private static Config fromBundle(Bundle b) {
        Config c = new Config();
        c.enabled = b.getBoolean(ConfigProvider.K_ENABLED, ConfigProvider.D_ENABLED);
        int a = pct(b.getInt(ConfigProvider.K_ICON_ALPHA, ConfigProvider.D_ICON_ALPHA));
        int g = clamp(b.getInt(ConfigProvider.K_ICON_GRAY, ConfigProvider.D_ICON_GRAY), 0, 255);
        c.iconColor = (a << 24) | (g << 16) | (g << 8) | g;
        c.fillAlpha = pct(b.getInt(ConfigProvider.K_FILL_ALPHA, ConfigProvider.D_FILL_ALPHA));
        c.fillBright = clamp(b.getInt(ConfigProvider.K_FILL_BRIGHT,
                ConfigProvider.D_FILL_BRIGHT), 0, 100) / 100f;
        c.numDim = b.getBoolean(ConfigProvider.K_NUM_DIM, ConfigProvider.D_NUM_DIM);
        c.numAlpha = pct(b.getInt(ConfigProvider.K_NUM_ALPHA, ConfigProvider.D_NUM_ALPHA));
        c.numBright = clamp(b.getInt(ConfigProvider.K_NUM_BRIGHT,
                ConfigProvider.D_NUM_BRIGHT), 0, 100) / 100f;
        c.skipDark = b.getBoolean(ConfigProvider.K_SKIP_DARK, ConfigProvider.D_SKIP_DARK);
        c.skipLand = b.getBoolean(ConfigProvider.K_SKIP_LAND, ConfigProvider.D_SKIP_LAND);
        c.appOn = b.getBoolean(ConfigProvider.K_APP_ON, ConfigProvider.D_APP_ON);
        c.appAlpha = clamp(b.getInt(ConfigProvider.K_APP_ALPHA,
                ConfigProvider.D_APP_ALPHA), 0, 100) / 100f;
        c.appBright = clamp(b.getInt(ConfigProvider.K_APP_BRIGHT,
                ConfigProvider.D_APP_BRIGHT), 0, 100) / 100f;
        return c;
    }

    // ---- Khi nào áp dụng hiệu ứng ----

    private static volatile boolean landscape = false;

    private static void updateOrientation() {
        try {
            if (appContext != null) {
                landscape = appContext.getResources().getConfiguration().orientation
                        == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            }
        } catch (Throwable ignored) {
            // giữ giá trị cũ
        }
    }

    /** Hiệu ứng đang thực sự áp dụng: bật, không phải xoay ngang, không phải nền tối. */
    private static boolean active(Config c) {
        if (!c.enabled) return false;
        if (c.skipLand && landscape) return false;
        // cường độ thật < 0.5: hệ thống đang dùng icon trắng trên nền tối
        if (c.skipDark && lastRealIntensity < 0.5f) return false;
        return true;
    }

    // ---- Khởi tạo, nạp cấu hình, làm mới ----

    private static boolean initDone = false;
    private static Context appContext;
    private static Handler bgHandler;

    private static synchronized void ensureInit() {
        if (initDone) return;
        try {
            Object app = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentApplication");
            if (!(app instanceof Context)) return;
            appContext = (Context) app;
        } catch (Throwable t) {
            return;
        }
        initDone = true;
        updateOrientation();
        try {
            appContext.registerComponentCallbacks(new android.content.ComponentCallbacks() {
                @Override
                public void onConfigurationChanged(android.content.res.Configuration newConfig) {
                    boolean l = newConfig.orientation
                            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
                    if (l != landscape) {
                        landscape = l;
                        refreshAll();
                    }
                }

                @Override
                public void onLowMemory() {
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "orientation callback failed: " + t);
        }
        HandlerThread ht = new HandlerThread("dsb-config");
        ht.start();
        bgHandler = new Handler(ht.getLooper());
        try {
            appContext.getContentResolver().registerContentObserver(CFG_URI, false,
                    new ContentObserver(bgHandler) {
                        @Override
                        public void onChange(boolean selfChange) {
                            loadConfig(0);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "observer failed: " + t);
        }
        bgHandler.post(new Runnable() {
            @Override
            public void run() {
                loadConfig(0);
            }
        });
    }

    private static void loadConfig(final int attempt) {
        try {
            Bundle b = appContext.getContentResolver().call(CFG_URI, "get", null, null);
            if (b != null) {
                cfg = fromBundle(b);
                refreshAll();
                return;
            }
        } catch (Throwable t) {
            // app cấu hình chưa sẵn sàng, thử lại bên dưới
        }
        if (attempt < 30 && bgHandler != null) {
            bgHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    loadConfig(attempt + 1);
                }
            }, 5000);
        }
    }

    private static WeakReference<Object> dispatcherRef;
    private static volatile float lastRealIntensity = 0f;
    private static volatile boolean lastActive = true;
    private static final ThreadLocal<Boolean> IN_ECHO = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };
    private static final WeakHashMap<Object, String> VIEWS = new WeakHashMap<>();

    private static void refreshAll() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Object d = dispatcherRef == null ? null : dispatcherRef.get();
                    if (d != null) {
                        XposedHelpers.callMethod(d, "applyDarkIntensity", lastRealIntensity);
                    }
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "refresh dispatcher failed: " + t);
                }
                ArrayList<Object> views = new ArrayList<>();
                ArrayList<String> methods = new ArrayList<>();
                synchronized (VIEWS) {
                    for (Map.Entry<Object, String> e : VIEWS.entrySet()) {
                        Object k = e.getKey();
                        if (k != null) {
                            views.add(k);
                            methods.add(e.getValue());
                        }
                    }
                }
                refreshNotifIcons();
                for (int i = 0; i < views.size(); i++) {
                    try {
                        XposedHelpers.callMethod(views.get(i), methods.get(i));
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "refresh view failed: " + t);
                    }
                }
            }
        });
    }

    // ---- Theo dõi giá trị gốc của từng trường màu ----

    // obj -> (tên trường -> {giá trị gốc, giá trị module đã ghi})
    private static final WeakHashMap<Object, HashMap<String, int[]>> SLOTS = new WeakHashMap<>();

    private static int original(Object obj, String field) {
        int c = XposedHelpers.getIntField(obj, field);
        synchronized (SLOTS) {
            HashMap<String, int[]> m = SLOTS.get(obj);
            if (m == null) {
                m = new HashMap<>();
                SLOTS.put(obj, m);
            }
            int[] s = m.get(field);
            if (s == null) {
                s = new int[] {c, c};
                m.put(field, s);
            } else if (c != s[1]) {
                // hệ thống vừa nạp lại màu gốc từ resource
                s[0] = c;
            }
            return s[0];
        }
    }

    private static void write(Object obj, String field, int value) {
        XposedHelpers.setIntField(obj, field, value);
        synchronized (SLOTS) {
            HashMap<String, int[]> m = SLOTS.get(obj);
            if (m != null) {
                int[] s = m.get(field);
                if (s != null) s[1] = value;
            }
        }
    }

    private static int transform(int color, int alpha, float brightness) {
        int r = Math.round(((color >> 16) & 0xFF) * brightness);
        int g = Math.round(((color >> 8) & 0xFF) * brightness);
        int b = Math.round((color & 0xFF) * brightness);
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    // ---- Hook ----

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) return;

        hookDispatcher(lpparam);
        hookAppIcons(lpparam);

        hookBattery(lpparam, ICON_VIEW, "onDarkChangeInternal", new String[] {
                "mBatteryLowColor", "mBatteryChargingColor",
                "mBatteryPowerSaveColor", "mBatteryPerformanceModeColor"
        });
        hookBattery(lpparam, HOLLOW_VIEW, "updateBatteryDrawable", new String[] {
                "batteryLowColor", "batteryChargingColor",
                "batteryPowerSaveColor", "batteryPerformanceModeColor",
                NUMBER_FIELD
        });
    }

    private void hookDispatcher(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = XposedHelpers.findClass(DISPATCHER, lpparam.classLoader);
            XposedBridge.hookAllMethods(clazz, "applyDarkIntensity", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    ensureInit();
                    if (param.args.length != 1 || !(param.args[0] instanceof Float)) return;
                    Config c = cfg;
                    float incoming = (Float) param.args[0];
                    dispatcherRef = new WeakReference<Object>(param.thisObject);
                    // updateResource() gọi lại với giá trị đã bị ép: không coi là giá trị thật
                    if (!IN_ECHO.get()) {
                        lastRealIntensity = incoming;
                    }
                    boolean act = active(c);
                    if (act != lastActive) {
                        lastActive = act;
                        refreshNotifColors();
                    }
                    try {
                        int orig = original(param.thisObject, ICON_COLOR_FIELD);
                        if (act) {
                            param.args[0] = 1.0f;
                            write(param.thisObject, ICON_COLOR_FIELD, c.iconColor);
                        } else {
                            write(param.thisObject, ICON_COLOR_FIELD, orig);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "dispatcher failed: " + t);
                    }
                }
            });
            XposedBridge.hookAllMethods(clazz, "updateResource", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    IN_ECHO.set(Boolean.TRUE);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    IN_ECHO.set(Boolean.FALSE);
                }
            });
            XposedBridge.log(TAG + "hooked " + DISPATCHER + ".applyDarkIntensity");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "dispatcher hook failed: " + t);
        }
    }

    private void hookBattery(XC_LoadPackage.LoadPackageParam lpparam, String className,
                             final String method, final String[] fields) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(clazz, method,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            ensureInit();
                            synchronized (VIEWS) {
                                VIEWS.put(param.thisObject, method);
                            }
                            Config c = cfg;
                            boolean act = active(c);
                            for (String f : fields) {
                                try {
                                    int orig = original(param.thisObject, f);
                                    int v = orig;
                                    if (act) {
                                        if (NUMBER_FIELD.equals(f)) {
                                            if (c.numDim) {
                                                v = transform(orig, c.numAlpha, c.numBright);
                                            }
                                        } else {
                                            v = transform(orig, c.fillAlpha, c.fillBright);
                                        }
                                    }
                                    write(param.thisObject, f, v);
                                } catch (Throwable ignored) {
                                    // trường không tồn tại ở bản này: bỏ qua
                                }
                            }
                        }
                    });
            XposedBridge.log(TAG + (hooks.isEmpty() ? "NO METHOD " : "hooked ")
                    + className + "." + method);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "battery hook failed (" + className + "): " + t);
        }
    }

    // ---- Icon ứng dụng màu cho thông báo trên thanh trạng thái ----

    private static final String ICON_VIEW_CLASS =
            "com.android.systemui.statusbar.StatusBarIconView";
    private static final String DEPENDENCY = "com.miui.systemui.MiuiDependency";
    private static final String APP_ICONS = "com.miui.systemui.graphics.AppIconsManager";
    private static final int MAX_ICON_PX = 96;

    // mọi StatusBarIconView của thông báo đã đi qua getIcon -> đang thay icon ứng dụng hay không
    private static final WeakHashMap<Object, Boolean> NOTIF_VIEWS = new WeakHashMap<>();
    private static ClassLoader sysLoader;

    private static void refreshNotifIcons() {
        ArrayList<Object> views = new ArrayList<>();
        synchronized (NOTIF_VIEWS) {
            for (Object k : NOTIF_VIEWS.keySet()) {
                if (k != null) views.add(k);
            }
        }
        for (Object v : views) {
            try {
                XposedHelpers.callMethod(v, "updateDrawable", true);
                XposedHelpers.callMethod(v, "updateIconColor");
            } catch (Throwable t) {
                XposedBridge.log(TAG + "refresh notif icon failed: " + t);
            }
        }
    }

    private static void refreshNotifColors() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                ArrayList<Object> views = new ArrayList<>();
                synchronized (NOTIF_VIEWS) {
                    for (Object k : NOTIF_VIEWS.keySet()) {
                        if (k != null) views.add(k);
                    }
                }
                for (Object v : views) {
                    try {
                        XposedHelpers.callMethod(v, "updateIconColor");
                    } catch (Throwable ignored) {
                        // bỏ qua view đã bị hủy
                    }
                }
            }
        });
    }

    private static Drawable loadAppIcon(Object view, Object sbn) {
        Drawable result = null;
        try {
            String pkg = (String) XposedHelpers.callMethod(sbn, "getPackageName");
            Object user = XposedHelpers.callMethod(sbn, "getUser");
            int uid = (Integer) XposedHelpers.callMethod(user, "getIdentifier");
            if (uid < 0) uid = 0;
            Class<?> dep = XposedHelpers.findClass(DEPENDENCY, sysLoader);
            Class<?> mgrCls = XposedHelpers.findClass(APP_ICONS, sysLoader);
            Object mgr = XposedHelpers.callStaticMethod(dep, "get", mgrCls);
            if (mgr != null) {
                Object bmp = XposedHelpers.callMethod(mgr, "getAppIconBitmap", uid, pkg);
                if (bmp instanceof Bitmap && !((Bitmap) bmp).isRecycled()) {
                    Bitmap b = (Bitmap) bmp;
                    if (b.getWidth() > MAX_ICON_PX) {
                        b = Bitmap.createScaledBitmap(b, MAX_ICON_PX, MAX_ICON_PX, true);
                    }
                    Context ctx = ((android.view.View) view).getContext();
                    result = new BitmapDrawable(ctx.getResources(), b);
                }
            }
        } catch (Throwable ignored) {
            // thử đường dự phòng bên dưới
        }
        if (result == null) {
            try {
                Object d = XposedHelpers.getObjectField(sbn, "mAppIcon");
                if (d instanceof Drawable) {
                    Drawable.ConstantState cs = ((Drawable) d).getConstantState();
                    result = cs != null ? cs.newDrawable() : (Drawable) d;
                }
            } catch (Throwable ignored) {
                // không có icon ứng dụng: giữ icon gốc
            }
        }
        return result;
    }

    private void hookAppIcons(XC_LoadPackage.LoadPackageParam lpparam) {
        sysLoader = lpparam.classLoader;
        try {
            Class<?> clazz = XposedHelpers.findClass(ICON_VIEW_CLASS, lpparam.classLoader);

            XposedBridge.hookAllMethods(clazz, "getIcon", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length != 1) return;
                    try {
                        Object view = param.thisObject;
                        if (!XposedHelpers.getBooleanField(view, "mExpandNotification")) return;
                        ensureInit();
                        synchronized (NOTIF_VIEWS) {
                            NOTIF_VIEWS.put(view, Boolean.FALSE);
                        }
                        Config c = cfg;
                        if (!c.appOn) return;
                        if (XposedHelpers.getBooleanField(view, "mShowsConversation")) return;
                        Object sbn = XposedHelpers.getObjectField(view, "mNotification");
                        if (sbn == null) return;
                        if ("android".equals(XposedHelpers.callMethod(sbn, "getOpPkg"))) return;
                        Drawable d = loadAppIcon(view, sbn);
                        if (d == null) return;
                        param.setResult(d);
                        synchronized (NOTIF_VIEWS) {
                            NOTIF_VIEWS.put(view, Boolean.TRUE);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "getIcon hook failed: " + t);
                    }
                }
            });

            XposedBridge.hookAllMethods(clazz, "updateIconColor", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Boolean on;
                        synchronized (NOTIF_VIEWS) {
                            on = NOTIF_VIEWS.get(param.thisObject);
                        }
                        Config c = cfg;
                        if (on == null || !on || !c.appOn) return;
                        if (!active(c)) {
                            // nền tối / xoay ngang: giữ nguyên màu icon ứng dụng, không phủ màu
                            XposedHelpers.callMethod(param.thisObject, "setColorFilter",
                                    new Class<?>[] {android.graphics.ColorFilter.class},
                                    new Object[] {null});
                            return;
                        }
                        float b = c.appBright;
                        float a = c.appAlpha;
                        float[] m = {
                                b, 0, 0, 0, 0,
                                0, b, 0, 0, 0,
                                0, 0, b, 0, 0,
                                0, 0, 0, a, 0};
                        XposedHelpers.callMethod(param.thisObject, "setColorFilter",
                                new ColorMatrixColorFilter(m));
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "updateIconColor hook failed: " + t);
                    }
                }
            });
            XposedBridge.log(TAG + "hooked app icons");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "app icon hook failed: " + t);
        }
    }
}
