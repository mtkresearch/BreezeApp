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

#pragma once

#include <cstdlib>
#include <type_traits>

typedef enum {
    LOW_POWER_MODE = 0,      // For model execution preference
    FAST_SINGLE_ANSWER_MODE, // For model execution preference
    SUSTAINED_SPEED_MODE,    // For model execution preference
    FAST_COMPILE_MODE,       // For model compile preference
    PERFORMANCE_MODE_MAX,
} PERFORMANCE_MODE_E;

static const int32_t kFastSingleAnswerParams[] = {
    0x00410000, // PERF_RES_CPUFREQ_CCI_FREQ
    1,
    0x00414000, // PERF_RES_CPUFREQ_PERF_MODE
    1,
    0x0143c000, // PERF_RES_SCHED_ISOLATION_CPU
    128,
    0x01000000, // PERF_RES_DRAM_OPP_MIN
    0,
    0x01408300, // PERF_RES_SCHED_UCLAMP_MIN_TA
    100,
    0x0201c000, // PERF_RES_FPS_FSTB_SOFT_FPS_LOWER
    60,
    0x0201c100, // PERF_RES_FPS_FSTB_SOFT_FPS_UPPER
    60,
    0x02020000, // PERF_RES_FPS_FBT_BHR_OPP
    31,
    0x01438400, // PERF_RES_SCHED_UTIL_UP_RATE_LIMIT_US_CLUSTER_1
    0,
    0x01438500, // PERF_RES_SCHED_UTIL_UP_RATE_LIMIT_US_CLUSTER_2
    0,
    0x01438700, // PERF_RES_SCHED_UTIL_DOWN_RATE_LIMIT_US_CLUSTER_1
    40000,
    0x01438800, // PERF_RES_SCHED_UTIL_DOWN_RATE_LIMIT_US_CLUSTER_2
    40000,
    0x01c3c100, // PERF_RES_PM_QOS_CPUIDLE_MCDI_ENABLE
    0,
};

// NpuWareUtils library construct
class NpuWareUtilsLib {
private:
    using FnAcquirePerformanceLock =
        std::add_pointer_t<int32_t(int32_t, PERFORMANCE_MODE_E, uint32_t)>;

    using FnAcquirePerfParamsLock =
        std::add_pointer_t<int32_t(int32_t, uint32_t, int32_t[], uint32_t)>;

    using FnReleasePerformanceLock = std::add_pointer_t<bool(int32_t)>;

public:
    static const NpuWareUtilsLib& get();

    bool isEnable() const { return mEnable; }

    FnAcquirePerformanceLock fnAcquirePerformanceLock = nullptr;
    FnAcquirePerfParamsLock fnAcquirePerfParamsLock = nullptr;
    FnReleasePerformanceLock fnReleasePerformanceLock = nullptr;

private:
    NpuWareUtilsLib();

    // Open a given library and load symbols
    bool load();

    bool mEnable = false;
};

// ScopePowerHal
class ScopePerformancer {
public:
    ScopePerformancer(uint32_t ms = 30000 /*Default to 30 sec*/);

    ScopePerformancer(const NpuWareUtilsLib& lib, uint32_t ms = 30000 /*Default to 30 sec*/);

    ~ScopePerformancer();

private:
    const NpuWareUtilsLib& mLib;

    bool mLock = 0;

    int mHalHandle = 0;
};