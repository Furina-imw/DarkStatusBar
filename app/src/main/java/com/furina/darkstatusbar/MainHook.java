package com.furina.darkstatusbar;

import android.content.Context;
import android.database.ContentObserver;
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
        return c;
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
                    if (!c.enabled || incoming != 1.0f) {
                        lastRealIntensity = incoming;
                    }
                    try {
                        int orig = original(param.thisObject, ICON_COLOR_FIELD);
                        if (c.enabled) {
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
                            for (String f : fields) {
                                try {
                                    int orig = original(param.thisObject, f);
                                    int v = orig;
                                    if (c.enabled) {
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
}
