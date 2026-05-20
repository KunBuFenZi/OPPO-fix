package com.codex.forcep2p5g;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Force every WifiP2pConfig built inside com.heytap.accessory to use the 5 GHz
 * operating band, regardless of what the framework code requests. The OPlus
 * accessory framework calls {@code setGroupOperatingFrequency(0)} (auto) which
 * defaults to 2.4 GHz, causing single-radio chips on the ported ROM to share
 * the band with wlan0 and tank cast bandwidth.
 */
public class Hook implements IXposedHookLoadPackage {

    private static final String TAG = "[ForceP2P5G]";
    private static final int BAND_5GHZ = 2;
    private static final String BUILDER = "android.net.wifi.p2p.WifiP2pConfig$Builder";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (!"com.heytap.accessory".equals(lpparam.packageName)) {
            return;
        }
        XposedBridge.log(TAG + " loaded for " + lpparam.packageName);

        Class<?> builderCls;
        try {
            builderCls = XposedHelpers.findClass(BUILDER, lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " findClass " + BUILDER + " failed: " + t);
            return;
        }

        // 1) When accessory calls setGroupOperatingFrequency, skip the freq path
        //    and instead pin the band to 5 GHz on the same Builder instance.
        XposedBridge.hookAllMethods(builderCls, "setGroupOperatingFrequency",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object freq = param.args.length > 0 ? param.args[0] : null;
                    XposedBridge.log(TAG + " setGroupOperatingFrequency(" + freq
                        + ") -> redirect to setGroupOperatingBand(" + BAND_5GHZ + ")");
                    try {
                        XposedHelpers.callMethod(param.thisObject,
                            "setGroupOperatingBand", BAND_5GHZ);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG
                            + " callMethod setGroupOperatingBand failed: " + t);
                    }
                    // Return the Builder itself to keep the fluent chain intact.
                    param.setResult(param.thisObject);
                }
            });

        // 2) If anything else calls setGroupOperatingBand with a non-5G value,
        //    rewrite the argument before the original method runs.
        XposedBridge.hookAllMethods(builderCls, "setGroupOperatingBand",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length == 0 || !(param.args[0] instanceof Integer)) {
                        return;
                    }
                    int v = (Integer) param.args[0];
                    if (v != BAND_5GHZ) {
                        XposedBridge.log(TAG + " setGroupOperatingBand("
                            + v + ") -> forced " + BAND_5GHZ);
                        param.args[0] = BAND_5GHZ;
                    }
                }
            });

        XposedBridge.log(TAG + " hooks installed");
    }
}
