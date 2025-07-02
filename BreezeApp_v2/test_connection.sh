#!/bin/bash

echo "🔧 BreezeApp Router Client Connection Diagnostic Tool"
echo "==================================================="
echo "This script helps diagnose connection issues between the client and service."
echo ""

# 1. Check installed packages
echo "📋 Checking installed packages..."
echo "--------------------------------"
adb shell pm list packages | grep -E "breezeapp|router" | sort
echo ""

# 2. Check service permissions
echo "🔒 Checking service permissions..."
echo "---------------------------------"
echo "Router service declared permissions:"
adb shell dumpsys package com.mtkresearch.breezeapp.router.debug | grep -A 10 "declared permissions"
echo ""
echo "Client requested permissions:"
adb shell dumpsys package com.mtkresearch.breezeapp.router.client | grep -A 10 "requested permissions"
echo ""

# 3. Verify signing certificates match
echo "🔑 Checking signing certificates..."
echo "---------------------------------"
ROUTER_CERT=$(adb shell dumpsys package com.mtkresearch.breezeapp.router.debug | grep -A 5 "signatures" | grep "1:" | head -1)
CLIENT_CERT=$(adb shell dumpsys package com.mtkresearch.breezeapp.router.client | grep -A 5 "signatures" | grep "1:" | head -1)

echo "Router: $ROUTER_CERT"
echo "Client: $CLIENT_CERT"

if [ "$ROUTER_CERT" == "$CLIENT_CERT" ]; then
    echo "✅ Certificates match! Signature-level permissions should work."
else
    echo "❌ Certificates do not match! Signature-level permissions will fail."
fi
echo ""

# 4. Test service availability
echo "🚀 Testing service availability..."
echo "--------------------------------"
echo "Starting router service..."
adb shell am start-service -n com.mtkresearch.breezeapp.router.debug/com.mtkresearch.breezeapp.router.AIRouterService

echo "Checking if service is running..."
adb shell dumpsys activity services | grep -A 10 "com.mtkresearch.breezeapp.router.AIRouterService"
echo ""

# 5. Clear logs and monitor connection
echo "📝 Monitoring connection logs..."
echo "------------------------------"
echo "Clearing logs..."
adb logcat -c

echo "Starting client app..."
adb shell am start -n com.mtkresearch.breezeapp.router.client/.MainActivity

echo "Monitoring logs for 10 seconds (press Ctrl+C to stop early)..."
timeout 10 adb logcat | grep -E "(AIRouter|ServiceConnection|binder)"

echo ""
echo "✅ Diagnostic complete!"
echo "If you're still having issues, check the detailed logs with:"
echo "adb logcat | grep -E '(AIRouter|ServiceConnection|binder)'" 