package com.mtkresearch.breezeapp_kotlin

import com.mtkresearch.breezeapp_kotlin.presentation.chat.adapter.MessageAdapterTest
import com.mtkresearch.breezeapp_kotlin.presentation.chat.model.ChatMessageTest
import com.mtkresearch.breezeapp_kotlin.presentation.chat.viewmodel.ChatViewModelTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * BreezeApp 測試套件
 * 包含所有重要的單元測試
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    ChatViewModelTest::class,
    MessageAdapterTest::class,
    ChatMessageTest::class
)
class BreezeAppTestSuite 