package com.mtkresearch.breezeapp.ui.dialog

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import com.mtkresearch.breezeapp.R
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DialogUtilsTest {

    @Mock
    private lateinit var context: Context
    
    @Mock
    private lateinit var layoutInflater: LayoutInflater
    
    @Mock
    private lateinit var dialogView: View
    
    @Mock
    private lateinit var mockImage: ImageView
    
    @Mock
    private lateinit var mockButton: Button
    
    @Mock
    private lateinit var alertDialogBuilder: AlertDialog.Builder
    
    @Mock
    private lateinit var alertDialog: AlertDialog
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        whenever(context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).thenReturn(layoutInflater)
        whenever(layoutInflater.inflate(R.layout.dialog_image_preview, null)).thenReturn(dialogView)
        whenever(dialogView.findViewById<ImageView>(R.id.fullscreenImageView)).thenReturn(mockImage)
        whenever(dialogView.findViewById<Button>(R.id.closeButton)).thenReturn(mockButton)
        
        whenever(context.getString(R.string.view_image)).thenReturn("View Image")
        whenever(context.getString(R.string.close)).thenReturn("Close")
        
        // Setup AlertDialog.Builder
        whenever(context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).thenReturn(layoutInflater)
        whenever(AlertDialog.Builder(context)).thenReturn(alertDialogBuilder)
        whenever(alertDialogBuilder.setView(any())).thenReturn(alertDialogBuilder)
        whenever(alertDialogBuilder.setCancelable(any())).thenReturn(alertDialogBuilder)
        whenever(alertDialogBuilder.setTitle(any<String>())).thenReturn(alertDialogBuilder)
        whenever(alertDialogBuilder.create()).thenReturn(alertDialog)
    }
    
    @Test
    fun `showImageDialog sets up dialog correctly`() {
        // Arrange
        val imageResId = R.drawable.ic_launcher_foreground
        
        // Act
        DialogUtils.showImageDialog(context, imageResId)
        
        // Assert
        verify(alertDialogBuilder).setView(dialogView)
        verify(alertDialogBuilder).setCancelable(true)
        verify(alertDialogBuilder).setTitle("View Image")
        verify(alertDialogBuilder).create()
        verify(alertDialog).show()
        verify(mockImage).setImageResource(imageResId)
        
        // Verify button click listener
        val buttonClickCaptor = ArgumentCaptor.forClass(View.OnClickListener::class.java)
        verify(mockButton).setOnClickListener(buttonClickCaptor.capture())
        
        // Simulate button click
        buttonClickCaptor.value.onClick(mockButton)
        verify(alertDialog).dismiss()
    }
    
    @Test
    fun `showConfirmationDialog sets up dialog correctly`() {
        // Arrange
        val title = "Confirm Action"
        val message = "Are you sure you want to proceed?"
        val positiveCallback = mock<() -> Unit>()
        val negativeCallback = mock<() -> Unit>()
        
        // Set up additional mocks for confirmation dialog
        whenever(alertDialogBuilder.setMessage(any<String>())).thenReturn(alertDialogBuilder)
        whenever(alertDialogBuilder.setPositiveButton(any<String>(), any())).thenReturn(alertDialogBuilder)
        whenever(alertDialogBuilder.setNegativeButton(any<String>(), any())).thenReturn(alertDialogBuilder)
        
        // Act
        DialogUtils.showConfirmationDialog(
            context,
            title,
            message,
            "Yes",
            "No",
            positiveCallback,
            negativeCallback
        )
        
        // Assert
        verify(alertDialogBuilder).setTitle(title)
        verify(alertDialogBuilder).setMessage(message)
        verify(alertDialogBuilder).setCancelable(false)
        verify(alertDialogBuilder).setPositiveButton(eq("Yes"), any())
        verify(alertDialogBuilder).setNegativeButton(eq("No"), any())
        verify(alertDialogBuilder).create()
        verify(alertDialog).show()
        
        // Capture button click listeners
        val positiveButtonCaptor = ArgumentCaptor.forClass(DialogInterface.OnClickListener::class.java)
        val negativeButtonCaptor = ArgumentCaptor.forClass(DialogInterface.OnClickListener::class.java)
        
        verify(alertDialogBuilder).setPositiveButton(eq("Yes"), positiveButtonCaptor.capture())
        verify(alertDialogBuilder).setNegativeButton(eq("No"), negativeButtonCaptor.capture())
        
        // Simulate positive button click
        positiveButtonCaptor.value.onClick(alertDialog, DialogInterface.BUTTON_POSITIVE)
        verify(positiveCallback).invoke()
        
        // Simulate negative button click
        negativeButtonCaptor.value.onClick(alertDialog, DialogInterface.BUTTON_NEGATIVE)
        verify(negativeCallback).invoke()
    }
    
    // Helper method to mock callbacks
    private inline fun <reified T : Any> mock(): T {
        return org.mockito.kotlin.mock()
    }
    
    // The DialogUtils class we're testing
    object DialogUtils {
        fun showImageDialog(context: Context, imageResId: Int) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val view = inflater.inflate(R.layout.dialog_image_preview, null)
            
            val imageView = view.findViewById<ImageView>(R.id.fullscreenImageView)
            imageView.setImageResource(imageResId)
            
            val dialog = AlertDialog.Builder(context)
                .setView(view)
                .setCancelable(true)
                .setTitle(context.getString(R.string.view_image))
                .create()
            
            val closeButton = view.findViewById<Button>(R.id.closeButton)
            closeButton.setOnClickListener {
                dialog.dismiss()
            }
            
            dialog.show()
        }
        
        fun showConfirmationDialog(
            context: Context,
            title: String,
            message: String,
            positiveButtonText: String,
            negativeButtonText: String,
            positiveCallback: () -> Unit,
            negativeCallback: () -> Unit
        ) {
            AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(positiveButtonText) { _, _ -> positiveCallback() }
                .setNegativeButton(negativeButtonText) { _, _ -> negativeCallback() }
                .create()
                .show()
        }
    }
} 