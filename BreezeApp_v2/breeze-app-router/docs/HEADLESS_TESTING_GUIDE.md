# Headless AI Router Testing Guide

This guide provides simple, clear steps to test and verify the `breeze-app-router` service directly from the command line using `adb`, without needing a separate client UI application.

**Note:** The `debug` build of this application has an application ID suffix of `.debug`. All commands below will use `com.mtkresearch.breezeapp.router.debug` as the package name. If you are testing a `release` build, you will need to remove the `.debug` suffix from the commands.

## Prerequisites

1.  You have built the `breeze-app-router` APK (e.g., `breeze-app-router-mock-debug.apk`).
2.  Your Android device is connected to your computer with debugging enabled.
3.  `adb` is installed and accessible from your terminal.

---

### Step 1: Install the Router APK

First, install the router application onto your connected device.

```sh
# Replace with the actual path to your APK file
adb install -r -d /path/to/your/breeze-app-router-mock-debug.apk
```
*   The `-r` flag allows re-installation of an existing app.
*   The `-d` flag allows version code downgrade (useful during development).

---

### Step 2: Verify Installation

Check if the router package is successfully installed on the device.

```sh
adb shell pm list packages | grep com.mtkresearch.breezeapp.router.debug
```

**Expected Output:**
You should see the package name printed back to you, like this:
```
package:com.mtkresearch.breezeapp.router.debug
```
If you see this, the application is installed.

---

### Step 3: Check Service Status (Initial State)

You can check the status of a service using the `dumpsys` command. The component name for our service is `com.mtkresearch.breezeapp.router.debug/.AIRouterService`.

```sh
adb shell dumpsys activity services com.mtkresearch.breezeapp.router.debug/.AIRouterService
```

**Expected Output (Before First Run):**
Initially, because no client has connected, the service is not running. The output will be empty or show "No services match". This is normal.

---

### Step 4: Method 1 - Direct Service Testing (Recommended)

**The most reliable method for ADB testing is to use the Host App client:**

```sh
# Install and run the Host App
adb install -r -d /path/to/host-app-debug.apk
adb shell am start -n com.mtkresearch.hostapp/.MainActivity
```

Then use the Host App UI to connect to the service. This method:
- ✅ Works reliably with Android background execution limits
- ✅ Tests the full AIDL communication stack
- ✅ Provides visual feedback of service operations
- ✅ Matches real-world usage patterns

---

### Step 5: Method 2 - Direct ADB Testing (Advanced)

**Warning:** This method has limitations due to Android 8.0+ background execution restrictions.

#### Step 5a: Bring App to Foreground

Due to Android background execution limits (API 26+), services cannot be started when the app is in background. Bring the app to foreground first:

```sh
adb shell am start -n com.mtkresearch.breezeapp.router.debug/com.mtkresearch.breezeapp.router.ui.DummyLauncherActivity
```

#### Step 5b: Start Service (Within 10 seconds)

Within 10 seconds of bringing the app to foreground, start the service:

```sh
adb shell am start-service -a "com.mtkresearch.breezeapp.router.AIRouterService" -p com.mtkresearch.breezeapp.router.debug
```

**Expected Output (Success):**
```
Starting service: Intent { act=com.mtkresearch.breezeapp.router.AIRouterService pkg=com.mtkresearch.breezeapp.router.debug }
```

**If you get "Background start not allowed" error:**
- This is expected Android behavior for API 26+
- Use Method 1 (Host App) instead
- Or disable battery optimization for this app (not recommended for testing)

---

### Step 6: Verify the Service is Running

Check if the service is running:

```sh
adb shell dumpsys activity services com.mtkresearch.breezeapp.router.debug/.AIRouterService
```

**Expected Output (When Running):**
```
SERVICE com.mtkresearch.breezeapp.router.debug/.AIRouterService ...
  * Intent: Intent { cmp=com.mtkresearch.breezeapp.router.debug/.AIRouterService }
  * app: ProcessRecord{...}
  * createTime: ...
  * lastActivity: ...
  * conns: 0
```

---

### Step 7: Check Service Logs

To see what the service is doing, check the logs:

```sh
adb logcat | grep AIRouterService
```

You should see log messages like:
```
I/AIRouterService: AIRouterService created
I/AIRouterService: Service started via test intent
```

---

### Step 8: Stop the Service

To stop the service and its process for a clean state:

```sh
adb shell am force-stop com.mtkresearch.breezeapp.router.debug
```

---

## Alternative: One-Line Testing Script

For Method 2 (if it works on your device), you can combine the steps:

```sh
adb shell am start -n com.mtkresearch.breezeapp.router.debug/com.mtkresearch.breezeapp.router.ui.DummyLauncherActivity && sleep 2 && adb shell am start-service -a "com.mtkresearch.breezeapp.router.AIRouterService" -p com.mtkresearch.breezeapp.router.debug
```

---

## Troubleshooting

### "Error: Not found; no service started"
- ✅ Check if the package is correctly installed
- ✅ Verify the service name and action in the manifest
- ✅ Make sure you're using the correct package name (debug vs release)
- ✅ Check if AIDL files are properly compiled

### "Background start not allowed" Error
- ⚠️  **This is normal Android behavior (API 26+)**
- ✅ **Solution: Use the Host App client (Method 1)**
- ❌ Avoid disabling battery optimization (affects production behavior)
- ❌ Don't use `am start-foreground-service` (requires different permissions)

### Service starts but crashes immediately
- ✅ Check logs with `adb logcat | grep AIRouterService`
- ✅ Look for initialization errors or missing dependencies
- ✅ Verify AIDL files are properly generated
- ✅ Check if MockEngine dependencies are available

### Permission Denied
- ✅ For release builds, signature permission will block external access
- ✅ Use debug builds for ADB testing (permission is removed)
- ✅ For production testing, use the Host App client instead

### Host App Cannot Connect
- ✅ Check both apps are installed with correct signatures
- ✅ Verify the `<queries>` section in Host App manifest
- ✅ Check package names match (debug vs release)
- ✅ Look for binding errors in logcat

---

## Best Practices for Service Testing

1. **Use the Host App** for comprehensive testing
2. **Check logs** for all operations
3. **Test both debug and release** builds separately
4. **Verify AIDL compatibility** between client and service
5. **Test on different Android versions** (especially API 26+ vs older) 