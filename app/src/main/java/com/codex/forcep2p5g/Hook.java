package com.codex.forcep2p5g;

import java.util.concurrent.atomic.AtomicBoolean;

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
    private static final String C1_NAME = "com.oplus.virtualcomm2.c1";
    private static final String D3_NAME = "com.oplus.virtualcomm2.d3";
    private static final String Y0_NAME = "com.oplus.virtualcomm2.y0";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if ("com.heytap.accessory".equals(lpparam.packageName)) {
            hookP2PBand(lpparam);
        } else if ("com.oplus.subsys".equals(lpparam.packageName)
                || "com.oplus.virtualcomm2".equals(lpparam.packageName)) {
            hookVcForbidFlag(lpparam);
        }
    }

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
                    if (param.args.length == 0 || !(param.args[0] instanceof Integer)) return;
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
     * c1 lives in VirtualCommService.apk, loaded via a plugin DexClassLoader
     * into com.oplus.subsys. lpparam.classLoader is the host app's loader and
     * doesn't see c1. We hook BaseDexClassLoader.findClass and wait until the
     * loader actually loads c1 — then we own the right ClassLoader instance
     * and can install the b0() hook on it.
     */
    private void hookVcForbidFlag(final LoadPackageParam lpparam) {
        XposedBridge.log(TAG_VC + " loaded for " + lpparam.packageName);

        // Fast path: maybe c1 IS in the host loader.
        if (tryHookC1(lpparam.classLoader)) {
            return;
        }
        XposedBridge.log(TAG_VC + " c1 not in host loader, waiting for plugin loader");

        final AtomicBoolean installed = new AtomicBoolean(false);

        // BaseDexClassLoader.findClass watcher — original strategy. Works only
        // when c1's classloader actually delegates to BaseDexClassLoader's
        // findClass. OPlus' plugin loader appears to bypass this on tb375fc,
        // so we keep this but add fallbacks below.
        try {
            Class<?> baseCls = Class.forName("dalvik.system.BaseDexClassLoader");
            XposedBridge.hookAllMethods(baseCls, "findClass", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (installed.get()) return;
                    if (param.args.length == 0 || !(param.args[0] instanceof String)) return;
                    String name = (String) param.args[0];
                    if (!C1_NAME.equals(name)) return;
                    if (param.getResult() == null) return;

                    ClassLoader pluginLoader = (ClassLoader) param.thisObject;
                    XposedBridge.log(TAG_VC + " caught BaseDexClassLoader.findClass for c1: " + pluginLoader);
                    if (tryHookC1(pluginLoader)) {
                        installed.set(true);
                    }
                }
            });
            XposedBridge.log(TAG_VC + " BaseDexClassLoader.findClass watcher installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG_VC + " BaseDexClassLoader watcher failed: " + t);
        }

        // Fallback A: hook ClassLoader.loadClass on EVERY classloader. This
        // catches even fully custom loaders, as long as they extend ClassLoader
        // and don't override loadClass to bypass super entirely.
        try {
            XposedBridge.hookAllMethods(ClassLoader.class, "loadClass", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (installed.get()) return;
                    if (param.args.length == 0 || !(param.args[0] instanceof String)) return;
                    String name = (String) param.args[0];
                    if (!C1_NAME.equals(name)) return;
                    if (param.getResult() == null) return;

                    ClassLoader cl = (ClassLoader) param.thisObject;
                    XposedBridge.log(TAG_VC + " caught ClassLoader.loadClass for c1: " + cl);
                    if (tryHookC1(cl)) {
                        installed.set(true);
                    }
                }
            });
            XposedBridge.log(TAG_VC + " ClassLoader.loadClass watcher installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG_VC + " ClassLoader.loadClass watcher failed: " + t);
        }

        // Fallback B: hook android.util.Log.d. DistributedCommDev (= c1) logs
        // its own state via Log.d with tag/msg containing "DistributedCommDev".
        // When we see one of those calls, c1 is definitely loaded; grab the
        // calling thread's context classloader and walk it to find c1.
        try {
            Class<?> logCls = Class.forName("android.util.Log");
            XposedBridge.hookAllMethods(logCls, "d", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (installed.get()) return;
                    if (param.args.length < 2) return;
                    String tag = String.valueOf(param.args[0]);
                    String msg = String.valueOf(param.args[1]);
                    if (!tag.contains("DistributedCommDev")
                            && !msg.contains("DistributedCommDev")) return;

                    // Try every classloader we can reach.
                    ClassLoader[] candidates = new ClassLoader[]{
                        Thread.currentThread().getContextClassLoader(),
                        lpparam.classLoader,
                        ClassLoader.getSystemClassLoader(),
                    };
                    for (ClassLoader probe : candidates) {
                        for (ClassLoader cl = probe; cl != null; cl = cl.getParent()) {
                            try {
                                cl.loadClass(C1_NAME);
                                XposedBridge.log(TAG_VC + " Log.d trace -> c1 in loader " + cl);
                                if (tryHookC1(cl)) {
                                    installed.set(true);
                                    return;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            });
            XposedBridge.log(TAG_VC + " Log.d trace fallback installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG_VC + " Log.d trace fallback failed: " + t);
        }
    }

    private boolean tryHookC1(ClassLoader loader) {
        try {
            Class<?> c1Cls = XposedHelpers.findClass(C1_NAME, loader);
            XposedBridge.hookAllMethods(c1Cls, "b0", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG_VC + " c1.b0() -> 0 (was suppressed)");
                    return 0;
                }
            });
            XposedBridge.log(TAG_VC + " hook on c1.b0() installed via loader " + loader);
        } catch (Throwable t) {
            return false;
        }

        try {
            Class<?> d3Cls = XposedHelpers.findClass(D3_NAME, loader);
            XposedBridge.hookAllMethods(d3Cls, "h", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length == 1 && param.args[0] instanceof Integer) {
                        int v = (Integer) param.args[0];
                        if (v != 0) {
                            XposedBridge.log(TAG_VC + " d3.h(" + v + ") -> forced 0");
                            param.args[0] = 0;
                        }
                    }
                }
            });
            XposedBridge.log(TAG_VC + " hook on d3.h installed via loader " + loader);
        } catch (Throwable t) {
            XposedBridge.log(TAG_VC + " d3.h hook failed (non-fatal): " + t);
        }

        // y0.x0() == isAllSimCardRemoved(). On tb375fc, modem RIL never reports
        // SIM_STATE_ABSENT (mSimState=UNKNOWN), so this returns false even with
        // no card inserted. That makes c1.b0() add the forbid bit and pad's own
        // capability frame goes out as forbidFlag=258, telling phone "I'm not
        // ready" so phone refuses to share its modem TO us. Forcing this to true
        // matches the "tablet has no SIM" intent.
        try {
            Class<?> y0Cls = XposedHelpers.findClass(Y0_NAME, loader);
            XposedBridge.hookAllMethods(y0Cls, "x0", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG_VC + " y0.x0() -> true (forced all-SIM-removed)");
                    return Boolean.TRUE;
                }
            });
            XposedBridge.log(TAG_VC + " hook on y0.x0() installed via loader " + loader);
        } catch (Throwable t) {
            XposedBridge.log(TAG_VC + " y0.x0() hook failed (non-fatal): " + t);
        }
        return true;
    }
}
