package com.mtkresearch.breezeapp.presentation.common.base

import android.view.View
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * 基礎ViewHolder
 */
abstract class BaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
    /**
     * 綁定數據到ViewHolder
     */
    abstract fun bind(item: T, position: Int)
    
    /**
     * 可選的綁定方法，包含payload參數用於部分更新
     */
    open fun bind(item: T, position: Int, payloads: List<Any>) {
        bind(item, position)
    }
    
    /**
     * ViewHolder被回收時的清理
     */
    open fun onViewRecycled() {
        // 子類別可以覆寫進行清理
    }
}

/**
 * 點擊監聽器介面
 */
interface OnItemClickListener<T> {
    fun onItemClick(item: T, position: Int, view: View)
    fun onItemLongClick(item: T, position: Int, view: View): Boolean = false
}

/**
 * BaseAdapter - 所有RecyclerView Adapter的基礎類別
 * 
 * 提供統一的功能：
 * - DiffUtil自動計算差異
 * - 統一的點擊處理
 * - ViewHolder生命週期管理
 * - 數據更新動畫
 */
abstract class BaseAdapter<T, VH : BaseViewHolder<T>>(
    diffCallback: DiffUtil.ItemCallback<T>
) : ListAdapter<T, VH>(diffCallback) {

    // 點擊監聽器
    private var itemClickListener: OnItemClickListener<T>? = null
    
    // 是否啟用點擊動畫
    var isClickAnimationEnabled = true
    
    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.bind(item, position)
        setupClickListeners(holder, item, position)
    }
    
    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val item = getItem(position)
            holder.bind(item, position, payloads)
        }
    }
    
    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.onViewRecycled()
    }
    
    /**
     * 設置點擊監聽器
     */
    fun setOnItemClickListener(listener: OnItemClickListener<T>?) {
        this.itemClickListener = listener
    }
    
    /**
     * 設置點擊監聽器（簡化版本）
     */
    fun setOnItemClickListener(onClick: (item: T, position: Int, view: View) -> Unit) {
        this.itemClickListener = object : OnItemClickListener<T> {
            override fun onItemClick(item: T, position: Int, view: View) {
                onClick(item, position, view)
            }
        }
    }
    
    /**
     * 設置點擊和長按監聽器
     */
    fun setOnItemClickListener(
        onClick: (item: T, position: Int, view: View) -> Unit,
        onLongClick: ((item: T, position: Int, view: View) -> Boolean)? = null
    ) {
        this.itemClickListener = object : OnItemClickListener<T> {
            override fun onItemClick(item: T, position: Int, view: View) {
                onClick(item, position, view)
            }
            
            override fun onItemLongClick(item: T, position: Int, view: View): Boolean {
                return onLongClick?.invoke(item, position, view) ?: false
            }
        }
    }
    
    /**
     * 設置點擊監聽器到ViewHolder
     */
    private fun setupClickListeners(holder: VH, item: T, position: Int) {
        itemClickListener?.let { listener ->
            holder.itemView.setOnClickListener { view ->
                if (isClickAnimationEnabled) {
                    animateClick(view)
                }
                listener.onItemClick(item, position, view)
            }
            
            holder.itemView.setOnLongClickListener { view ->
                listener.onItemLongClick(item, position, view)
            }
        }
    }
    
    /**
     * 點擊動畫
     */
    private fun animateClick(view: View) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }
    
    /**
     * 獲取指定位置的項目
     */
    fun getItemAt(position: Int): T? {
        return if (position in 0 until itemCount) {
            getItem(position)
        } else {
            null
        }
    }
    
    /**
     * 檢查是否為空
     */
    fun isEmpty(): Boolean = itemCount == 0
    
    /**
     * 檢查是否不為空
     */
    fun isNotEmpty(): Boolean = itemCount > 0
    
    /**
     * 獲取第一個項目
     */
    fun getFirstItem(): T? = if (isNotEmpty()) getItem(0) else null
    
    /**
     * 獲取最後一個項目
     */
    fun getLastItem(): T? = if (isNotEmpty()) getItem(itemCount - 1) else null
    
    /**
     * 查找項目位置
     */
    fun findPosition(predicate: (T) -> Boolean): Int {
        for (i in 0 until itemCount) {
            if (predicate(getItem(i))) {
                return i
            }
        }
        return -1
    }
    
    /**
     * 查找項目
     */
    fun findItem(predicate: (T) -> Boolean): T? {
        val position = findPosition(predicate)
        return if (position != -1) getItem(position) else null
    }
    
    /**
     * 更新單一項目
     * 如果項目不存在，則添加到列表末尾
     */
    fun updateItem(newItem: T, predicate: (T) -> Boolean) {
        val currentList = currentList.toMutableList()
        val position = currentList.indexOfFirst(predicate)
        
        if (position != -1) {
            currentList[position] = newItem
        } else {
            currentList.add(newItem)
        }
        
        submitList(currentList)
    }
    
    /**
     * 移除項目
     */
    fun removeItem(predicate: (T) -> Boolean) {
        val currentList = currentList.toMutableList()
        val removed = currentList.removeAll(predicate)
        if (removed) {
            submitList(currentList)
        }
    }
    
    /**
     * 添加項目到指定位置
     */
    fun addItem(item: T, position: Int = itemCount) {
        val currentList = currentList.toMutableList()
        val safePosition = position.coerceIn(0, currentList.size)
        currentList.add(safePosition, item)
        submitList(currentList)
    }
    
    /**
     * 添加多個項目
     */
    fun addItems(items: List<T>, position: Int = itemCount) {
        val currentList = currentList.toMutableList()
        val safePosition = position.coerceIn(0, currentList.size)
        currentList.addAll(safePosition, items)
        submitList(currentList)
    }
    
    /**
     * 清空列表
     */
    fun clear() {
        submitList(emptyList())
    }
    
    /**
     * 刷新數據（重新提交當前列表）
     */
    fun refresh() {
        submitList(currentList.toList())
    }
}

/**
 * 簡化的DiffUtil.ItemCallback
 */
abstract class SimpleDiffCallback<T> : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(oldItem: T & Any, newItem: T & Any): Boolean {
        return getItemId(oldItem) == getItemId(newItem)
    }
    
    override fun areContentsTheSame(oldItem: T & Any, newItem: T & Any): Boolean {
        return oldItem == newItem
    }
    
    /**
     * 獲取項目唯一ID
     */
    abstract fun getItemId(item: T): Any
    
    /**
     * 可選：返回變更payload
     */
    override fun getChangePayload(oldItem: T & Any, newItem: T & Any): Any? {
        return null
    }
} 