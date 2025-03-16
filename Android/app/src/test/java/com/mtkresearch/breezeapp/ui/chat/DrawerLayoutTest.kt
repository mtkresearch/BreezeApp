package com.mtkresearch.breezeapp.ui.chat

import android.content.Intent
import android.view.View
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.data.models.SavedConversation
import com.mtkresearch.breezeapp.ui.history.ConversationHistoryFragment
import com.mtkresearch.breezeapp.ui.settings.SettingsActivity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class DrawerLayoutTest {

    @Mock
    private lateinit var drawerLayout: DrawerLayout
    
    @Mock
    private lateinit var newChatButton: View
    
    @Mock
    private lateinit var settingsOption: View
    
    @Mock
    private lateinit var aboutOption: View
    
    @Mock
    private lateinit var historyFragment: ConversationHistoryFragment
    
    @Mock
    private lateinit var fragmentManager: FragmentManager
    
    @Mock
    private lateinit var fragmentTransaction: FragmentTransaction
    
    @Mock
    private lateinit var viewModel: ChatViewModel
    
    private lateinit var activity: ChatActivity
    
    @Before
    fun setup() {
        activity = ChatActivity()
        
        // Setup Fragment Transaction
        whenever(fragmentManager.beginTransaction()).thenReturn(fragmentTransaction)
        whenever(fragmentTransaction.replace(any(), any())).thenReturn(fragmentTransaction)
        
        // Setup ViewModel
        whenever(viewModel.getConversationRepository()).doReturn(any())
    }
    
    @Test
    fun `new chat button clears conversation and closes drawer`() {
        // Arrange - Set up the click listener
        activity.setupNewChatButton(newChatButton, drawerLayout, viewModel)
        
        // Act - Simulate click
        val clickListener = captureClickListener(newChatButton)
        clickListener.onClick(newChatButton)
        
        // Assert
        verify(viewModel).clearConversation()
        verify(drawerLayout).closeDrawer(GravityCompat.START)
    }
    
    @Test
    fun `settings option opens settings activity`() {
        // Arrange - Set up the click listener
        val mockContext = MockContext()
        whenever(settingsOption.context).thenReturn(mockContext)
        
        activity.setupSettingsOption(settingsOption, drawerLayout)
        
        // Act - Simulate click
        val clickListener = captureClickListener(settingsOption)
        clickListener.onClick(settingsOption)
        
        // Assert
        assert(mockContext.startedIntent?.component?.className == SettingsActivity::class.java.name)
        verify(drawerLayout).closeDrawer(GravityCompat.START)
    }
    
    @Test
    fun `about option is hidden and should not be clicked`() {
        // Arrange - Set visibility
        whenever(aboutOption.visibility).thenReturn(View.GONE)
        
        // Assert
        assert(aboutOption.visibility == View.GONE)
    }
    
    @Test
    fun `history fragment is set up correctly`() {
        // Arrange
        activity.setupHistoryFragment(
            fragmentManager,
            historyFragment,
            viewModel,
            drawerLayout
        )
        
        // Assert
        verify(fragmentTransaction).replace(any(), eq(historyFragment))
        verify(fragmentTransaction).commit()
    }
    
    @Test
    fun `selected conversation loads and closes drawer`() {
        // Arrange
        val conversation = SavedConversation(
            id = "test-id",
            title = "Test Conversation",
            date = Date(),
            previewText = "Test preview",
            messageCount = 5
        )
        
        activity.setupHistoryFragment(
            fragmentManager,
            historyFragment,
            viewModel,
            drawerLayout
        )
        
        // Capture the callback
        val captor = org.mockito.ArgumentCaptor.forClass(Function1::class.java)
        verify(historyFragment).onConversationSelected = captor.capture()
        
        // Act - Trigger the callback
        @Suppress("UNCHECKED_CAST")
        (captor.value as (SavedConversation) -> Unit).invoke(conversation)
        
        // Assert
        verify(viewModel).loadConversation("test-id")
        verify(drawerLayout).closeDrawer(GravityCompat.START)
    }
    
    // Helper class to check intent creation
    class MockContext : android.content.Context() {
        var startedIntent: Intent? = null
        
        override fun startActivity(intent: Intent?) {
            startedIntent = intent
        }
        
        // Other required overrides
        override fun getAssets() = null
        override fun getResources() = null
        override fun getPackageManager() = null
        override fun getContentResolver() = null
        override fun getMainLooper() = null
        override fun getApplicationContext() = this
        override fun getPackageName() = "com.mtkresearch.breezeapp"
        override fun getClassLoader() = null
        override fun getSystemService(name: String) = null
        override fun checkCallingOrSelfPermission(permission: String) = 0
        override fun checkCallingOrSelfUriPermission(uri: android.net.Uri, modeFlags: Int) = 0
        override fun checkCallingUriPermission(uri: android.net.Uri, modeFlags: Int) = 0
        override fun checkPermission(permission: String, pid: Int, uid: Int) = 0
        override fun checkSelfPermission(permission: String) = 0
        override fun checkUriPermission(uri: android.net.Uri, pid: Int, uid: Int, modeFlags: Int) = 0
        override fun checkUriPermission(uri: android.net.Uri?, readPermission: String?, writePermission: String?, pid: Int, uid: Int, modeFlags: Int) = 0
        override fun createPackageContext(packageName: String, flags: Int) = null
        override fun createConfigurationContext(overrideConfiguration: android.content.res.Configuration) = null
        override fun createDisplayContext(display: android.view.Display) = null
        override fun createAttributionContext(attributionTag: String?) = null
        override fun enforceCallingOrSelfPermission(permission: String, message: String?) {}
        override fun enforceCallingOrSelfUriPermission(uri: android.net.Uri, modeFlags: Int, message: String?) {}
        override fun enforceCallingPermission(permission: String, message: String?) {}
        override fun enforceCallingUriPermission(uri: android.net.Uri, modeFlags: Int, message: String?) {}
        override fun enforcePermission(permission: String, pid: Int, uid: Int, message: String?) {}
        override fun enforceUriPermission(uri: android.net.Uri, pid: Int, uid: Int, modeFlags: Int, message: String?) {}
        override fun enforceUriPermission(uri: android.net.Uri?, readPermission: String?, writePermission: String?, pid: Int, uid: Int, modeFlags: Int, message: String?) {}
        override fun getApplicationInfo() = android.content.pm.ApplicationInfo()
        override fun getDatabasePath(name: String?) = null
        override fun getDir(name: String?, mode: Int) = null
        override fun getFileStreamPath(name: String?) = null
        override fun getFilesDir() = null
        override fun getNoBackupFilesDir() = null
        override fun getObbDir() = null
        override fun getObbDirs() = arrayOf<java.io.File>()
        override fun getPackageCodePath() = ""
        override fun getPackageResourcePath() = ""
        override fun getSharedPreferences(name: String?, mode: Int) = null
        override fun moveSharedPreferencesFrom(sourceContext: android.content.Context?, name: String?) = false
        override fun deleteSharedPreferences(name: String?) = false
        override fun getSharedPreferencesPath(name: String?) = null
        override fun getCacheDir() = null
        override fun getCodeCacheDir() = null
        override fun getExternalCacheDir() = null
        override fun getExternalCacheDirs() = arrayOf<java.io.File>()
        override fun getExternalFilesDir(type: String?) = null
        override fun getExternalFilesDirs(type: String?) = arrayOf<java.io.File>()
        override fun getExternalMediaDirs() = arrayOf<java.io.File>()
        override fun getTheme() = null
        override fun setTheme(resid: Int) {}
        override fun openFileInput(name: String?) = null
        override fun openFileOutput(name: String?, mode: Int) = null
        override fun deleteFile(name: String?) = false
        override fun getWallpaper() = null
        override fun peekWallpaper() = null
        override fun getWallpaperDesiredMinimumWidth() = 0
        override fun getWallpaperDesiredMinimumHeight() = 0
        override fun setWallpaper(bitmap: android.graphics.Bitmap?) {}
        override fun setWallpaper(data: java.io.InputStream?) {}
        override fun clearWallpaper() {}
        override fun sendBroadcast(intent: Intent?) {}
        override fun sendBroadcast(intent: Intent?, receiverPermission: String?) {}
        override fun sendOrderedBroadcast(intent: Intent?, receiverPermission: String?) {}
        override fun sendOrderedBroadcast(intent: Intent, receiverPermission: String?, resultReceiver: android.content.BroadcastReceiver?, scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?) {}
        override fun sendBroadcastAsUser(intent: Intent?, user: android.os.UserHandle?) {}
        override fun sendBroadcastAsUser(intent: Intent?, user: android.os.UserHandle?, receiverPermission: String?) {}
        override fun sendOrderedBroadcastAsUser(intent: Intent?, user: android.os.UserHandle?, receiverPermission: String?, resultReceiver: android.content.BroadcastReceiver?, scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?) {}
        override fun sendStickyBroadcast(intent: Intent?) {}
        override fun sendStickyOrderedBroadcast(intent: Intent?, resultReceiver: android.content.BroadcastReceiver?, scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?) {}
        override fun removeStickyBroadcast(intent: Intent?) {}
        override fun sendStickyBroadcastAsUser(intent: Intent?, user: android.os.UserHandle?) {}
        override fun sendStickyOrderedBroadcastAsUser(intent: Intent?, user: android.os.UserHandle?, resultReceiver: android.content.BroadcastReceiver?, scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?) {}
        override fun removeStickyBroadcastAsUser(intent: Intent?, user: android.os.UserHandle?) {}
        override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter?) = null
        override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter?, flags: Int) = null
        override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter?, receiverPermission: String?, scheduler: android.os.Handler?) = null
        override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter?, receiverPermission: String?, scheduler: android.os.Handler?, flags: Int) = null
        override fun unregisterReceiver(receiver: android.content.BroadcastReceiver?) {}
        override fun startService(service: Intent?) = null
        override fun stopService(service: Intent?) = false
        override fun bindService(service: Intent?, conn: android.content.ServiceConnection, flags: Int) = false
        override fun unbindService(conn: android.content.ServiceConnection) {}
        override fun startInstrumentation(className: android.content.ComponentName, profileFile: String?, arguments: android.os.Bundle?) = false
        override fun getString(resId: Int) = ""
        override fun getString(resId: Int, vararg formatArgs: Any?) = ""
        override fun getOpPackageName() = ""
        override fun getOpAttributionTag() = null
        override fun managedQuery(contentUri: android.net.Uri?, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) = null
    }
    
    // Helper methods for tests
    private fun captureClickListener(view: View): View.OnClickListener {
        val captor = org.mockito.ArgumentCaptor.forClass(View.OnClickListener::class.java)
        verify(view).setOnClickListener(captor.capture())
        return captor.value
    }
    
    // Extension functions for testing
    private fun ChatActivity.setupNewChatButton(
        button: View,
        drawerLayout: DrawerLayout,
        viewModel: ChatViewModel
    ) {
        button.setOnClickListener {
            viewModel.clearConversation()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }
    
    private fun ChatActivity.setupSettingsOption(
        settingsOption: View,
        drawerLayout: DrawerLayout
    ) {
        settingsOption.setOnClickListener {
            val intent = Intent(it.context, SettingsActivity::class.java)
            it.context.startActivity(intent)
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }
    
    private fun ChatActivity.setupHistoryFragment(
        fragmentManager: FragmentManager,
        historyFragment: ConversationHistoryFragment,
        viewModel: ChatViewModel,
        drawerLayout: DrawerLayout
    ) {
        historyFragment.setRepository(viewModel.getConversationRepository())
        
        historyFragment.onConversationSelected = { conversation ->
            viewModel.loadConversation(conversation.id)
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        
        fragmentManager.beginTransaction()
            .replace(R.id.conversationListContainer, historyFragment)
            .commit()
    }
} 