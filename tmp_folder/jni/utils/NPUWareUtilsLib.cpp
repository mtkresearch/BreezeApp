/*
 * Copyright (C) 2023 MediaTek Inc., this file is modified on 08/29/2024
 * by MediaTek Inc. based on MIT License .
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the ""Software""), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED ""AS IS"", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include "NPUWareUtilsLib.h"

#include "common/logging.h"

#include <android/log.h>
#include <dlfcn.h>

#include <cstdlib>
#include <memory>
#include <string>
#include <utility>

#ifdef USE_PERF_PARAM_LOCK
static constexpr bool kUsePerfParamLock = true;
#else
static constexpr bool kUsePerfParamLock = false;
#endif

#define NPUWARE_LOG_D(format, ...) \
    __android_log_print(ANDROID_LOG_DEBUG, "NPUWARELIB", format "\n", ##__VA_ARGS__);

#define NPUWARE_LOG_E(format, ...) \
    __android_log_print(ANDROID_LOG_ERROR, "NPUWARELIB", format "\n", ##__VA_ARGS__);

static void* voidFunction() {
    return nullptr;
}

const NpuWareUtilsLib& NpuWareUtilsLib::get() {
    static NpuWareUtilsLib npuWareUtilsLib;
    npuWareUtilsLib.load();
    return npuWareUtilsLib;
}

NpuWareUtilsLib::NpuWareUtilsLib()
    : fnAcquirePerformanceLock(reinterpret_cast<FnAcquirePerformanceLock>(voidFunction)),
      fnAcquirePerfParamsLock(reinterpret_cast<FnAcquirePerfParamsLock>(voidFunction)),
      fnReleasePerformanceLock(reinterpret_cast<FnReleasePerformanceLock>(voidFunction)) {}

// Open a given library and load symbols
bool NpuWareUtilsLib::load() {
    if (mEnable) {
        // Library already loaded
        return true;
    }

    void* handle = nullptr;
    const std::string libraries[] = {"libapuwareutils_v2.mtk.so", "libapuwareutils.mtk.so"};
    for (const auto& lib : libraries) {
        handle = dlopen(lib.c_str(), RTLD_LAZY | RTLD_LOCAL);
        if (handle) {
            NPUWARE_LOG_D("dlopen %s", lib.c_str());
            fnAcquirePerformanceLock = reinterpret_cast<FnAcquirePerformanceLock>(
                dlsym(handle, "acquirePerformanceLockInternal"));
            fnAcquirePerfParamsLock = reinterpret_cast<FnAcquirePerfParamsLock>(
                dlsym(handle, "acquirePerfParamsLockInternal"));
            fnReleasePerformanceLock = reinterpret_cast<FnReleasePerformanceLock>(
                dlsym(handle, "releasePerformanceLockInternal"));
            mEnable =
                fnAcquirePerformanceLock && fnReleasePerformanceLock && fnAcquirePerfParamsLock;
            return mEnable;
        } else {
            NPUWARE_LOG_E("unable to open library %s", lib.c_str());
        }
    }
    return false;
}

// ScopePowerHal
ScopePerformancer::ScopePerformancer(uint32_t ms) : ScopePerformancer(NpuWareUtilsLib::get(), ms) {}

ScopePerformancer::ScopePerformancer(const NpuWareUtilsLib& lib, uint32_t ms) : mLib(lib) {
    mLock = mLib.isEnable();
    if (mLock) {
        LOG(INFO) << "PowerHAL Enable";
        if constexpr (!kUsePerfParamLock) {
            mHalHandle = mLib.fnAcquirePerformanceLock(mHalHandle, FAST_SINGLE_ANSWER_MODE, ms);
        } else {
            const auto numParams =
                sizeof(kFastSingleAnswerParams) / sizeof(*kFastSingleAnswerParams);
            mHalHandle = mLib.fnAcquirePerfParamsLock(
                mHalHandle, ms, (int32_t*)kFastSingleAnswerParams, numParams);
        }
    }
};

ScopePerformancer::~ScopePerformancer() {
    if (mHalHandle != 0 && mLock) {
        LOG(INFO) << "PowerHAL Free";
        mLib.fnReleasePerformanceLock(mHalHandle);
        mHalHandle = 0;
    }
}