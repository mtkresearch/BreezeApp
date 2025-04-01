#ifndef BREEZE_APP_CRASHLYTICS_INIT_H
#define BREEZE_APP_CRASHLYTICS_INIT_H

#include <jni.h>
#include "crashlytics/crashlytics.h"

#ifdef __cplusplus
extern "C" {
#endif

// Initialize Crashlytics in native code
void initCrashlytics() {
    firebase::crashlytics::Initialize();
}

// Log a message to Crashlytics from native code
void logToCrashlytics(const char* message) {
    firebase::crashlytics::Log(message);
}

// Set a custom key in Crashlytics
void setCrashlyticsKey(const char* key, const char* value) {
    firebase::crashlytics::SetCustomKey(key, value);
}

#ifdef __cplusplus
}
#endif

#endif // BREEZE_APP_CRASHLYTICS_INIT_H 