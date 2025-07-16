package com.mtkresearch.breezeapp.router.performance

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

/**
 * Performance profiler for analyzing AI Router performance characteristics.
 * 
 * This utility helps identify bottlenecks and memory usage patterns during
 * AI processing operations.
 */
class PerformanceProfiler {
    
    companion object {
        private const val TAG = "PerformanceProfiler"
    }
    
    private val operationTimes = ConcurrentHashMap<String, MutableList<Long>>()
    private val memorySnapshots = mutableListOf<MemorySnapshot>()
    private val activeOperations = AtomicLong(0)
    
    data class MemorySnapshot(
        val timestamp: Long,
        val usedMemoryMB: Long,
        val maxMemoryMB: Long,
        val freeMemoryMB: Long,
        val operationContext: String
    )
    
    data class PerformanceReport(
        val operationStats: Map<String, OperationStats>,
        val memoryProfile: MemoryProfile,
        val concurrencyStats: ConcurrencyStats
    )
    
    data class OperationStats(
        val operationName: String,
        val totalExecutions: Int,
        val averageTimeMs: Double,
        val minTimeMs: Long,
        val maxTimeMs: Long,
        val p95TimeMs: Long,
        val p99TimeMs: Long
    )
    
    data class MemoryProfile(
        val peakMemoryUsageMB: Long,
        val averageMemoryUsageMB: Double,
        val memoryGrowthMB: Long,
        val gcEvents: Int
    )
    
    data class ConcurrencyStats(
        val maxConcurrentOperations: Long,
        val averageConcurrentOperations: Double,
        val concurrencyBottlenecks: List<String>
    )
    
    /**
     * Measures the execution time of an operation and records memory usage.
     */
    suspend fun <T> measureOperation(
        operationName: String,
        operation: suspend () -> T
    ): T {
        val startMemory = captureMemorySnapshot(operationName)
        activeOperations.incrementAndGet()
        
        val result: T
        val executionTime = measureTimeMillis {
            result = operation()
        }
        
        activeOperations.decrementAndGet()
        val endMemory = captureMemorySnapshot("$operationName-end")
        
        // Record timing
        operationTimes.computeIfAbsent(operationName) { mutableListOf() }.add(executionTime)
        
        Log.d(TAG, "Operation '$operationName' completed in ${executionTime}ms")
        Log.d(TAG, "Memory usage: ${startMemory.usedMemoryMB}MB -> ${endMemory.usedMemoryMB}MB")
        
        return result
    }
    
    /**
     * Captures current memory state.
     */
    private fun captureMemorySnapshot(context: String): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / 1024 / 1024
        val totalMemory = runtime.totalMemory() / 1024 / 1024
        val freeMemory = runtime.freeMemory() / 1024 / 1024
        val usedMemory = totalMemory - freeMemory
        
        val snapshot = MemorySnapshot(
            timestamp = System.currentTimeMillis(),
            usedMemoryMB = usedMemory,
            maxMemoryMB = maxMemory,
            freeMemoryMB = freeMemory,
            operationContext = context
        )
        
        memorySnapshots.add(snapshot)
        return snapshot
    }
    
    /**
     * Simulates memory pressure to test service behavior.
     */
    fun simulateMemoryPressure(): Boolean {
        return try {
            Log.d(TAG, "Simulating memory pressure...")
            
            // Force garbage collection
            System.gc()
            Thread.sleep(100)
            System.gc()
            
            val beforeGC = captureMemorySnapshot("before-gc")
            
            // Allocate some memory to simulate pressure
            val memoryHog = mutableListOf<ByteArray>()
            repeat(10) {
                memoryHog.add(ByteArray(1024 * 1024)) // 1MB chunks
            }
            
            val afterAllocation = captureMemorySnapshot("after-allocation")
            
            // Clear and force GC again
            memoryHog.clear()
            System.gc()
            Thread.sleep(100)
            
            val afterGC = captureMemorySnapshot("after-gc")
            
            Log.d(TAG, "Memory pressure test: ${beforeGC.usedMemoryMB}MB -> ${afterAllocation.usedMemoryMB}MB -> ${afterGC.usedMemoryMB}MB")
            
            true
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError during memory pressure test", e)
            false
        }
    }
    
    /**
     * Tests concurrent operation performance.
     */
    suspend fun testConcurrentPerformance(
        operationName: String,
        concurrencyLevel: Int,
        operation: suspend (Int) -> Unit
    ): ConcurrencyStats {
        Log.d(TAG, "Testing concurrent performance: $operationName with $concurrencyLevel threads")
        
        val startTime = System.currentTimeMillis()
        val jobs = mutableListOf<Job>()
        val concurrencyLevels = mutableListOf<Long>()
        
        // Monitor concurrency levels
        val monitoringJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                concurrencyLevels.add(activeOperations.get())
                delay(10) // Sample every 10ms
            }
        }
        
        // Launch concurrent operations
        repeat(concurrencyLevel) { index ->
            val job = CoroutineScope(Dispatchers.Default).launch {
                measureOperation("$operationName-concurrent-$index") {
                    operation(index)
                }
            }
            jobs.add(job)
        }
        
        // Wait for completion
        jobs.forEach { it.join() }
        monitoringJob.cancel()
        
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        
        val maxConcurrency = concurrencyLevels.maxOrNull() ?: 0
        val avgConcurrency = concurrencyLevels.average()
        
        Log.d(TAG, "Concurrent test completed in ${totalTime}ms")
        Log.d(TAG, "Max concurrency: $maxConcurrency, Average: $avgConcurrency")
        
        return ConcurrencyStats(
            maxConcurrentOperations = maxConcurrency,
            averageConcurrentOperations = avgConcurrency,
            concurrencyBottlenecks = if (maxConcurrency < concurrencyLevel * 0.8) {
                listOf("Concurrency bottleneck detected: achieved $maxConcurrency/$concurrencyLevel")
            } else emptyList()
        )
    }
    
    /**
     * Generates a comprehensive performance report.
     */
    fun generateReport(): PerformanceReport {
        val operationStats = operationTimes.mapValues { (operationName, times) ->
            val sortedTimes = times.sorted()
            val total = times.size
            
            OperationStats(
                operationName = operationName,
                totalExecutions = total,
                averageTimeMs = times.average(),
                minTimeMs = sortedTimes.firstOrNull() ?: 0,
                maxTimeMs = sortedTimes.lastOrNull() ?: 0,
                p95TimeMs = if (total > 0) sortedTimes[(total * 0.95).toInt().coerceAtMost(total - 1)] else 0,
                p99TimeMs = if (total > 0) sortedTimes[(total * 0.99).toInt().coerceAtMost(total - 1)] else 0
            )
        }
        
        val memoryProfile = if (memorySnapshots.isNotEmpty()) {
            val peakMemory = memorySnapshots.maxByOrNull { it.usedMemoryMB }?.usedMemoryMB ?: 0
            val avgMemory = memorySnapshots.map { it.usedMemoryMB }.average()
            val firstMemory = memorySnapshots.firstOrNull()?.usedMemoryMB ?: 0
            val lastMemory = memorySnapshots.lastOrNull()?.usedMemoryMB ?: 0
            
            MemoryProfile(
                peakMemoryUsageMB = peakMemory,
                averageMemoryUsageMB = avgMemory,
                memoryGrowthMB = lastMemory - firstMemory,
                gcEvents = 0 // Would need more sophisticated tracking
            )
        } else {
            MemoryProfile(0, 0.0, 0, 0)
        }
        
        return PerformanceReport(
            operationStats = operationStats,
            memoryProfile = memoryProfile,
            concurrencyStats = ConcurrencyStats(0, 0.0, emptyList()) // Would be filled by concurrent tests
        )
    }
    
    /**
     * Clears all collected performance data.
     */
    fun reset() {
        operationTimes.clear()
        memorySnapshots.clear()
        activeOperations.set(0)
        Log.d(TAG, "Performance profiler reset")
    }
    
    /**
     * Logs a detailed performance report.
     */
    fun logReport() {
        val report = generateReport()
        
        Log.i(TAG, "=== PERFORMANCE REPORT ===")
        
        // Operation statistics
        Log.i(TAG, "Operation Statistics:")
        report.operationStats.forEach { (name, stats) ->
            Log.i(TAG, "  $name:")
            Log.i(TAG, "    Executions: ${stats.totalExecutions}")
            Log.i(TAG, "    Average: ${String.format("%.2f", stats.averageTimeMs)}ms")
            Log.i(TAG, "    Min/Max: ${stats.minTimeMs}ms / ${stats.maxTimeMs}ms")
            Log.i(TAG, "    P95/P99: ${stats.p95TimeMs}ms / ${stats.p99TimeMs}ms")
        }
        
        // Memory profile
        Log.i(TAG, "Memory Profile:")
        Log.i(TAG, "  Peak Usage: ${report.memoryProfile.peakMemoryUsageMB}MB")
        Log.i(TAG, "  Average Usage: ${String.format("%.2f", report.memoryProfile.averageMemoryUsageMB)}MB")
        Log.i(TAG, "  Memory Growth: ${report.memoryProfile.memoryGrowthMB}MB")
        
        Log.i(TAG, "=== END REPORT ===")
    }
}