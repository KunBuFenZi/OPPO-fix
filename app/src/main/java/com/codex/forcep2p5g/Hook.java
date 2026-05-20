package com.codex.forcep2p5g;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Hook implements IXposedHookLoadPackage {

    private static final String TAG_P2P = "[ForceP2P5G]";
    private static final String TAG_VC = "[ForceVcShare]";
    private static final int BAND_5GHZ = 2;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if ("com.heytap.accessory".equals(lpparam.packageName)) {
            hookP2PBand(lpparam);
        } else if ("com.oplus.subsys".equals(lpparam.packageName)
                || "com.oplus.virtualcomm2".equals(lpparam.packageName)) {
            // c1 lives in com.oplus.virtualcomm2 (VirtualCommService.apk);
            // also try in com.oplus.subsys in case the build inlines it there.
            hookVcForbidFlag(lpparam);
        }
    }

    /**
     * Force WifiP2pConfig.Builder to always pin the operating band to 5 GHz.
     * Fixes screen-cast lag caused by OPlus PantaConnect defaulting to 2.4 GHz.
     */
    private void hookP2PBand(LoadPackageParam lpparam) {
        XposedBridge.log(TAG_P2P + " loaded for " + lpparam.packageName);

        Class<?> builderCls;
        try {
            builderCls = XposedHelpers.findClass(
                "android.net.wifi.p2p.WifiP2pConfig$Builder", lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG_P2P + " findClass Builder failed: " + t);
            return;
        }

        XposedBridge.hookAllMethods(builderCls, "setGroupOperatingFrequency",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object freq = param.args.length > 0 ? param.args[0] : null;
                    XposedBridge.log(TAG_P2P + " setGroupOperatingFrequency(" + freq
                        + ") -> redirect to setGroupOperatingBand(" + BAND_5GHZ + ")");
                    try {
                        XposedHelpers.callMethod(param.thisObject,
                            "setGroupOperatingBand", BAND_5GHZ);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG_P2P
                            + " callMethod setGroupOperatingBand failed: " + t);
                    }
                    param.setResult(param.thisObject);
                }
            });

        XposedBridge.hookAllMethods(builderCls, "setGroupOperatingBand",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length == 0 || !(param.args[0] instanceof Integer)) {
                        return;
                    }
                    int v = (Integer) param.args[0];
                    if (v != BAND_5GHZ) {
                        XposedBridge.log(TAG_P2P + " setGroupOperatingBand("
                            + v + ") -> forced " + BAND_5GHZ);
                        param.args[0] = BAND_5GHZ;
                    }
                }
            });

        XposedBridge.log(TAG_P2P + " hooks installed");
    }

    /**
     * Force OPlus VirtualCommService (com.oplus.subsys) to always report
     * forbidFlag = 0 in VcCapabilityMsg, so the paired phone believes the pad
     * is ready to receive cellular share / call / SMS.
     *
     * Target: com.oplus.virtualcomm2.c1#b0() returns the computed forbidFlag.
     * The ported ROM's pad has no working "virtual modem" so c1.b0() ORs in
     * bits 0x100 + 0x80 = 384 = "forbid". We override to 0 so the phone-side
     * accepts the share request.
     */
    private void hookVcForbidFlag(LoadPackageParam lpparam) {
        XposedBridge.log(TAG_VC + " loaded for " + lpparam.packageName);

        Class<?> c1Cls;
        try {
            c1Cls = XposedHelpers.findClass(
                "com.oplus.virtualcomm2.c1", lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG_VC + " findClass c1 failed: " + t);
            return;
        }

        try {
            XposedBridge.hookAllMethods(c1Cls, "b0", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG_VC + " c1.b0() -> 0 (was suppressed)");
                    return 0;
                }
            });
            XposedBridge.log(TAG_VC + " hook on c1.b0() installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG_VC + " hook c1.b0 failed: " + t);
        }

        // Defense in depth: also force the VcCapabilityMsg setter to 0 in case
        // some code path sets the field directly.
        try {
            Class<?> msgCls = XposedHelpers.findClass(
                "com.oplus.virtualcomm2.d3", lpparam.classLoader);
            XposedBridge.hookAllMethods(msgCls, "h", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length == 1 && param.args[0] instanceof Integer) {
                        int v = (Integer) param.args[0];
                        if (v != 0) {
                            XposedBridge.log(TAG_VC
                                + " d3.h(" + v + ") -> forced 0");
                            param.args[0] = 0;
                        }
                    }
                }
            });
            XposedBridge.log(TAG_VC + " hook on d3.h installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG_VC + " hook d3.h failed: " + t);
        }
    }
}
