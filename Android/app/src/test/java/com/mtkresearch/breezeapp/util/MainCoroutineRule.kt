package com.mtkresearch.breezeapp.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Main coroutine rule for unit tests.
 * 
 * This rule allows you to easily manage coroutines in unit tests by:
 * 1. Setting the main dispatcher to a test dispatcher
 * 2. Providing a TestCoroutineScope that can be used to launch coroutines
 * 3. Providing a runBlockingTest extension for running coroutine test code
 */
@ExperimentalCoroutinesApi
class MainCoroutineRule(
    private val dispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()
) : TestRule, TestCoroutineScope by TestCoroutineScope(dispatcher) {
    
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                Dispatchers.setMain(dispatcher)
                
                try {
                    base.evaluate()
                } finally {
                    cleanupTestCoroutines()
                    Dispatchers.resetMain()
                }
            }
        }
    }
    
    fun runBlockingTest(block: suspend TestCoroutineScope.() -> Unit) = 
        kotlinx.coroutines.test.runBlockingTest(dispatcher, block)
} 