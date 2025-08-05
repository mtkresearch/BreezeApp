package com.mtkresearch.breezeapp.presentation.common.base

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * BaseFragment - 所有Fragment的基礎類別
 *
 * 提供統一的功能：
 * - 生命週期管理
 * - 權限處理
 * - 錯誤處理
 * - Loading狀態管理
 * - UI狀態觀察
 */
abstract class BaseFragment : Fragment() {

    // 權限請求處理器
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        onPermissionsResult(permissions)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeUIState()
    }

    /**
     * 設置UI組件
     * 子類別需要實作此方法來初始化UI
     */
    protected abstract fun setupUI()

    /**
     * 觀察UI狀態變化
     * 子類別可以覆寫此方法來自訂狀態觀察邏輯
     */
    protected open fun observeUIState() {
        // 預設實作：可由子類別覆寫
    }

    /**
     * 安全地收集Flow數據
     * 確保在Fragment生命週期內安全收集
     */
    protected fun <T> Flow<T>.collectSafely(
        state: Lifecycle.State = Lifecycle.State.STARTED,
        action: (T) -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(state) {
                collect(action)
            }
        }
    }

    /**
     * 顯示Loading狀態
     */
    protected open fun showLoading() {
        // 預設實作：可由子類別覆寫
        // 例如顯示ProgressBar或Loading Dialog
    }

    /**
     * 隱藏Loading狀態
     */
    protected open fun hideLoading() {
        // 預設實作：可由子類別覆寫
    }

    /**
     * 顯示錯誤訊息
     */
    protected open fun showError(message: String, action: (() -> Unit)? = null) {
        view?.let { view ->
            val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
            action?.let { actionCallback ->
                snackbar.setAction("重試") { actionCallback() }
            }
            snackbar.show()
        }
    }

    /**
     * 顯示成功訊息
     */
    protected open fun showSuccess(message: String) {
        view?.let { view ->
            Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    /**
     * 檢查權限
     */
    protected fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 檢查多個權限
     */
    protected fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all { hasPermission(it) }
    }

    /**
     * 請求單一權限
     */
    protected fun requestPermission(permission: String) {
        requestPermissions(arrayOf(permission))
    }

    /**
     * 請求多個權限
     */
    protected fun requestPermissions(permissions: Array<String>) {
        permissionLauncher.launch(permissions)
    }

    /**
     * 權限請求結果處理
     * 子類別可以覆寫此方法來處理權限結果
     */
    protected open fun onPermissionsResult(permissions: Map<String, Boolean>) {
        val deniedPermissions = permissions.filterValues { !it }.keys
        if (deniedPermissions.isNotEmpty()) {
            onPermissionsDenied(deniedPermissions.toList())
        } else {
            onPermissionsGranted(permissions.keys.toList())
        }
    }

    /**
     * 權限被拒絕時的處理
     */
    protected open fun onPermissionsDenied(permissions: List<String>) {
        showError("需要權限才能正常使用此功能")
    }

    /**
     * 權限被授予時的處理
     */
    protected open fun onPermissionsGranted(permissions: List<String>) {
        // 預設空實作，子類別可覆寫
    }

    /**
     * 常用權限常數
     */
    companion object {
        const val CAMERA_PERMISSION = Manifest.permission.CAMERA
        const val RECORD_AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
        const val READ_EXTERNAL_STORAGE_PERMISSION = Manifest.permission.READ_EXTERNAL_STORAGE
        const val WRITE_EXTERNAL_STORAGE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE

        // 常用權限組合
        val MEDIA_PERMISSIONS = arrayOf(
            READ_EXTERNAL_STORAGE_PERMISSION,
            CAMERA_PERMISSION
        )

        val AUDIO_PERMISSIONS = arrayOf(
            RECORD_AUDIO_PERMISSION
        )
    }

    /**
     * Fragment銷毀時的清理
     */
    override fun onDestroyView() {
        super.onDestroyView()
        // 子類別可以在此進行額外清理
        onCleanup()
    }

    /**
     * 清理資源
     * 子類別可以覆寫此方法進行自訂清理
     */
    protected open fun onCleanup() {
        // 預設空實作
    }
} 