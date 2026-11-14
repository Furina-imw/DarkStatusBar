package com.furina.darkstatusbar;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Ép icon thanh trạng thái (pin, sóng, WiFi, tốc độ mạng...) luôn ở trạng thái "dark",
 * bất kể nền app bên dưới sáng hay tối.
 *
 * Mọi receiver trên thanh trạng thái nhận màu từ DarkIconDispatcherImpl.applyDarkIntensity(float),
 * nên chỉ cần ép tham số này = 1f (1f = icon tối hoàn toàn, 0f = icon sáng).
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "[DarkStatusBar] ";
    private static final String TARGET_PKG = "com.android.systemui";
    private static final String DISPATCHER =
            "com.android.systemui.statusbar.phone.DarkIconDispatcherImpl";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) return;

        try {
            Class<?> clazz = XposedHelpers.findClass(DISPATCHER, lpparam.classLoader);
            XposedBridge.hookAllMethods(clazz, "applyDarkIntensity", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length == 1 && param.args[0] instanceof Float) {
                        param.args[0] = 1.0f;
                    }
                }
            });
            XposedBridge.log(TAG + "hooked " + DISPATCHER + ".applyDarkIntensity");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook failed: " + t);
        }
    }
}
