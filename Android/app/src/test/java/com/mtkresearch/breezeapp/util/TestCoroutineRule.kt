package com.mtkresearch.breezeapp.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Test rule for managing coroutines in unit tests.
 * This rule swaps the main dispatcher with a test dispatcher during tests,
 * which allows for deterministic testing of coroutines.
 */
@ExperimentalCoroutinesApi
class TestCoroutineRule(private val testDispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    
    override fun starting(description: Description) {
        super.starting(description)
        Dispatchers.setMain(testDispatcher)
    }
    
    override fun finished(description: Description) {
        super.finished(description)
        Dispatchers.resetMain()
    }
} 