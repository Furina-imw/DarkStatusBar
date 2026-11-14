package com.furina.darkstatusbar;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Ép icon thanh trạng thái luôn ở trạng thái "dark" và đổi màu "dark" thành ICON_COLOR,
 * bất kể nền app bên dưới sáng hay tối.
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "[DarkStatusBar] ";
    private static final String TARGET_PKG = "com.android.systemui";
    private static final String DISPATCHER =
            "com.android.systemui.statusbar.phone.DarkIconDispatcherImpl";

    // Màu icon (ARGB). 0x66808080 = xám, độ đục 40%.
    private static final int ICON_COLOR = 0x66808080;

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
            XposedBridge.log(TAG + "hook failed: " + t);
        }
    }
}
