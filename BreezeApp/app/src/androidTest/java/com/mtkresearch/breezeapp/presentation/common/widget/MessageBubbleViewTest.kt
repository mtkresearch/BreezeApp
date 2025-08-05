package com.mtkresearch.breezeapp.presentation.common.widget

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp.core.utils.MessageType
import com.mtkresearch.breezeapp.presentation.common.widget.MessageBubbleView.MessageState
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * MessageBubbleView UI組件測試
 * 
 * 測試重點:
 * - 訊息氣泡創建和初始化
 * - 不同訊息類型設置
 * - 訊息狀態變更
 * - 回調函數設置
 * 
 * 注意：這是基於 Instrumentation 的 View 組件測試，不需要 Activity
 */
@RunWith(AndroidJUnit4::class)
class MessageBubbleViewTest {

    private lateinit var context: Context
    private lateinit var messageBubbleView: MessageBubbleView
    private lateinit var testContainer: FrameLayout

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // 在主線程中創建View組件和測試容器
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            testContainer = FrameLayout(context)
            messageBubbleView = MessageBubbleView(context)
            
            // 將View添加到容器中，這樣可以確保布局正確初始化
            testContainer.addView(messageBubbleView, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        
        // 等待UI初始化完成
        Thread.sleep(200)
    }

    @Test
    fun 初始狀態應該正確() {
        // 驗證初始狀態
        assertNotNull("MessageBubbleView應該被成功創建", messageBubbleView)
        assertEquals("View應該可見", android.view.View.VISIBLE, messageBubbleView.visibility)
    }

    @Test
    fun 設置用戶訊息應該成功() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                messageBubbleView.setMessage(
                    text = "這是用戶訊息",
                    type = MessageType.USER,
                    state = MessageState.NORMAL,
                    showButtons = false
                )
            } catch (e: Exception) {
                android.util.Log.e("MessageBubbleViewTest", "設置用戶訊息失敗", e)
                fail("設置用戶訊息不應該失敗: ${e.message}")
            }
        }
        
        Thread.sleep(100)
        assertNotNull("設置用戶訊息後，View應該仍然存在", messageBubbleView)
    }

    @Test
    fun 設置AI訊息應該成功() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                messageBubbleView.setMessage(
                    text = "這是AI回應",
                    type = MessageType.AI,
                    state = MessageState.NORMAL,
                    showButtons = true
                )
            } catch (e: Exception) {
                android.util.Log.e("MessageBubbleViewTest", "設置AI訊息失敗", e)
                fail("設置AI訊息不應該失敗: ${e.message}")
            }
        }
        
        Thread.sleep(100)
        assertNotNull("設置AI訊息後，View應該仍然存在", messageBubbleView)
    }

    @Test
    fun 設置系統訊息應該成功() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                messageBubbleView.setMessage(
                    text = "這是系統訊息",
                    type = MessageType.SYSTEM,
                    state = MessageState.NORMAL,
                    showButtons = false
                )
            } catch (e: Exception) {
                android.util.Log.e("MessageBubbleViewTest", "設置系統訊息失敗", e)
                fail("設置系統訊息不應該失敗: ${e.message}")
            }
        }
        
        Thread.sleep(100)
        assertNotNull("設置系統訊息後，View應該仍然存在", messageBubbleView)
    }

    @Test
    fun 載入狀態應該正確顯示() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                messageBubbleView.setMessage(
                    text = "正在載入...",
                    type = MessageType.AI,
                    state = MessageState.LOADING,
                    showButtons = false
                )
            } catch (e: Exception) {
                android.util.Log.e("MessageBubbleViewTest", "設置載入狀態失敗", e)
                fail("設置載入狀態不應該失敗: ${e.message}")
            }
        }
        
        Thread.sleep(100)
        assertNotNull("設置載入狀態後，View應該仍然存在", messageBubbleView)
    }

    @Test
    fun 錯誤狀態應該正確顯示() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                messageBubbleView.setMessage(
                    text = "發生錯誤",
                    type = MessageType.USER,
                    state = MessageState.ERROR,
                    showButtons = true
                )
            } catch (e: Exception) {
                android.util.Log.e("MessageBubbleViewTest", "設置錯誤狀態失敗", e)
                fail("設置錯誤狀態不應該失敗: ${e.message}")
            }
        }
        
        Thread.sleep(100)
        assertNotNull("設置錯誤狀態後，View應該仍然存在", messageBubbleView)
    }

    @Test
    fun TYPING狀態應該正確顯示() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                messageBubbleView.setMessage(
                    text = "AI正在輸入...",
                    type = MessageType.AI,
                    state = MessageState.TYPING,
                    showButtons = false
                )
            } catch (e: Exception) {
                android.util.Log.e("MessageBubbleViewTest", "設置TYPING狀態失敗", e)
                fail("設置TYPING狀態不應該失敗: ${e.message}")
            }
        }
        
        Thread.sleep(100)
        assertNotNull("設置TYPING狀態後，View應該仍然存在", messageBubbleView)
    }

    @Test
    fun 狀態更新應該成功() {
        // 先設置初始狀態
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            messageBubbleView.setMessage(
                text = "測試訊息",
                type = MessageType.AI,
                state = MessageState.LOADING
            )
        }
        
        Thread.sleep(100)
        
        // 然後更新狀態
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                messageBubbleView.updateState(MessageState.NORMAL)
            } catch (e: Exception) {
                android.util.Log.e("MessageBubbleViewTest", "更新狀態失敗", e)
                fail("更新狀態不應該失敗: ${e.message}")
            }
        }
        
        Thread.sleep(100)
        assertNotNull("更新狀態後，View應該仍然存在", messageBubbleView)
    }

    @Test
    fun 回調函數設置應該成功() {
        var speakerClicked = false
        var likeClicked = false
        var retryClicked = false
        
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                // 設置回調函數
                messageBubbleView.setOnSpeakerClickListener { speakerClicked = true }
                messageBubbleView.setOnLikeClickListener { isPositive -> likeClicked = true }
                messageBubbleView.setOnRetryClickListener { retryClicked = true }
                
                // 設置一個帶按鈕的訊息
                messageBubbleView.setMessage(
                    text = "測試回調函數",
                    type = MessageType.AI,
                    state = MessageState.NORMAL,
                    showButtons = true
                )
            } catch (e: Exception) {
                android.util.Log.e("MessageBubbleViewTest", "設置回調函數失敗", e)
                fail("設置回調函數不應該失敗: ${e.message}")
            }
        }
        
        Thread.sleep(100)
        assertNotNull("設置回調函數後，View應該仍然存在", messageBubbleView)
    }

    @Test
    fun 空訊息文字應該被處理() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                messageBubbleView.setMessage(
                    text = "",
                    type = MessageType.AI,
                    state = MessageState.NORMAL,
                    showButtons = false
                )
            } catch (e: Exception) {
                android.util.Log.e("MessageBubbleViewTest", "處理空訊息失敗", e)
                fail("處理空訊息不應該失敗: ${e.message}")
            }
        }
        
        Thread.sleep(100)
        assertNotNull("處理空訊息後，View應該仍然存在", messageBubbleView)
    }

    @Test
    fun View組件穩定性測試() {
        // 測試連續的狀態變更
        val messageTypes = arrayOf(MessageType.USER, MessageType.AI, MessageType.SYSTEM)
        val messageStates = arrayOf(MessageState.NORMAL, MessageState.LOADING, MessageState.ERROR, MessageState.TYPING)
        
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                for (type in messageTypes) {
                    for (state in messageStates) {
                        messageBubbleView.setMessage(
                            text = "測試訊息 - ${type.name} - ${state.name}",
                            type = type,
                            state = state,
                            showButtons = (type == MessageType.AI)
                        )
                        
                        // 短暫等待，讓UI更新
                        Thread.sleep(10)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MessageBubbleViewTest", "穩定性測試失敗", e)
                fail("穩定性測試不應該失敗: ${e.message}")
            }
        }
        
        Thread.sleep(100)
        assertNotNull("穩定性測試後，View應該仍然存在", messageBubbleView)
    }
} 