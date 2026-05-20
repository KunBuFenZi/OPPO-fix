# OPPO-fix / ForceP2P5G

A small LSPosed module that forces the OPlus PantaConnect (`com.heytap.accessory`) Wi-Fi P2P Group Owner to use the 5 GHz band on the tb375fc ColorOS 16 port. Without this, the framework defaults to 2.4 GHz channel 11, causing screen-cast lag.

## What it does

Hooks `android.net.wifi.p2p.WifiP2pConfig$Builder` inside `com.heytap.accessory`:

- `setGroupOperatingFrequency(int)` is redirected to `setGroupOperatingBand(2)` (`GROUP_OWNER_BAND_5GHZ`)
- `setGroupOperatingBand(int)` arguments are rewritten to `2`

The hook only runs inside `com.heytap.accessory`. Other apps are unaffected.

## Build

GitHub Actions builds `app-debug.apk` on every push (see `.github/workflows/build.yml`). Download the artifact from the workflow run.

## Install

1. Install the APK on the pad (`adb install app-debug.apk`)
2. Open LSPosed Manager → enable **ForceP2P5G**, scope `com.heytap.accessory` (auto-added)
3. Force-stop `com.heytap.accessory`, reboot, then re-cast

## Verify

```
adb shell dumpsys wifip2p | grep -E 'frequency:|isGroupOwner|Client: Device'
```

Expected: `frequency:` shows a 5 GHz channel (e.g. 5180 / 5765 / 5825) instead of 2412 / 2462 / 2472.
