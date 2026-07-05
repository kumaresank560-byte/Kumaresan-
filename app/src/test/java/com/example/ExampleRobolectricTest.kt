package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.util.PdfGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Tn Doc scanner", appName)
  }

  @Test
  fun `generate pdf from images`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    
    // Create temp image files
    val file1 = File(context.cacheDir, "test1.jpg")
    val file2 = File(context.cacheDir, "test2.jpg")
    
    val bitmap1 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    val bitmap2 = Bitmap.createBitmap(100, 150, Bitmap.Config.ARGB_8888)
    
    FileOutputStream(file1).use { out ->
      bitmap1.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
    FileOutputStream(file2).use { out ->
      bitmap2.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
    
    val paths = listOf(file1.absolutePath, file2.absolutePath)
    val pdfPath = PdfGenerator.generatePdf(context, paths, "TestDocument")
    
    val pdfFile = File(pdfPath)
    assertTrue(pdfFile.exists())
    assertTrue(pdfFile.length() > 0)
    assertTrue(pdfPath.endsWith(".pdf"))
    
    // Clean up
    file1.delete()
    file2.delete()
    pdfFile.delete()
  }
}

