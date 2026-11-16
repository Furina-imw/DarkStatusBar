package com.furina.darkstatusbar;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.Set;

/**
 * 1) Ép icon thanh trạng thái luôn "dark" và đổi màu thành ICON_COLOR.
 * 2) Giữ màu pin (sạc/pin yếu/tiết kiệm pin/hiệu năng) nhưng hạ độ đục xuống BATTERY_ALPHA.
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

    // Màu icon (ARGB). 0x66808080 = xám, độ đục 40%.
    private static final int ICON_COLOR = 0x66808080;

    // Độ đục của màu pin khi sạc/pin yếu... (0x66 = 40%, 0x99 = 60%, 0x80 = 50%).
    private static final int BATTERY_ALPHA = 0x66;

    // true = làm mờ cả số trong pin (chỉ kiểu Hollow).
    private static final boolean DIM_NUMBER = true;
    private static final int NUMBER_ALPHA = 0x80;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) return;

        hookDispatcher(lpparam);

        hookBattery(lpparam, ICON_VIEW, "onDarkChangeInternal", new String[] {
                "mBatteryLowColor", "mBatteryChargingColor",
                "mBatteryPowerSaveColor", "mBatteryPerformanceModeColor"
        });

        String[] hollowFields = DIM_NUMBER
                ? new String[] {
                        "batteryLowColor", "batteryChargingColor",
                        "batteryPowerSaveColor", "batteryPerformanceModeColor",
                        "batteryLevelWhite"
                }
                : new String[] {
                        "batteryLowColor", "batteryChargingColor",
                        "batteryPowerSaveColor", "batteryPerformanceModeColor"
                };
        hookBattery(lpparam, HOLLOW_VIEW, "updateBatteryDrawable", hollowFields);
    }

    private void hookDispatcher(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = XposedHelpers.findClass(DISPATCHER, lpparam.classLoader);
            XposedBridge.hookAllMethods(clazz, "applyDarkIntensity", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length == 1 && param.args[0] instanceof Float) {
                        param.args[0] = 1.0f;
                        try {
                            XposedHelpers.setIntField(param.thisObject,
                                    "mDarkModeIconColorSingleTone", ICON_COLOR);
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + "set color failed: " + t);
                        }
                    }
                }
            });
            XposedBridge.log(TAG + "hooked " + DISPATCHER + ".applyDarkIntensity");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "dispatcher hook failed: " + t);
        }
    }

    private void hookBattery(XC_LoadPackage.LoadPackageParam lpparam, String className,
                             String method, final String[] fields) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(clazz, method,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            for (String f : fields) {
                                dim(param.thisObject, f, "batteryLevelWhite".equals(f) ? NUMBER_ALPHA : BATTERY_ALPHA);
                            }
                        }
                    });
            XposedBridge.log(TAG + (hooks.isEmpty() ? "NO METHOD " : "hooked ")
                    + className + "." + method);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "battery hook failed (" + className + "): " + t);
        }
    }

    private static void dim(Object obj, String field, int alpha) {
        try {
            int c = XposedHelpers.getIntField(obj, field);
            int d = (c & 0x00FFFFFF) | (alpha << 24);
            if (c != d) {
                XposedHelpers.setIntField(obj, field, d);
            }
        } catch (Throwable ignored) {
            // trường không tồn tại ở bản này: bỏ qua
        }
    }
}
