package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("El Pachax", appName)
  }

  @Test
  fun `target URL is configured correctly`() {
    assertEquals("https://xtobi.github.io/el-pachax-stable/", TARGET_URL)
  }

  @Test
  fun `cleanUserAgent removes WebView identifiers for Google OAuth compatibility`() {
    val defaultWebViewUa = "Mozilla/5.0 (Linux; Android 14; Pixel 7; Build/UP1A.231005.007; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.0.0 Mobile Safari/537.36"
    val cleanedUa = cleanUserAgent(defaultWebViewUa)

    assertFalse("Cleaned User-Agent must not contain '; wv'", cleanedUa.contains("; wv"))
    assertFalse("Cleaned User-Agent must not contain 'Version/4.0 '", cleanedUa.contains("Version/4.0 "))
    assertTrue("Cleaned User-Agent must retain Chrome identity", cleanedUa.contains("Chrome/130.0.0.0"))
    assertTrue("Cleaned User-Agent must retain Mobile Safari", cleanedUa.contains("Mobile Safari/537.36"))
    assertEquals(
      "Mozilla/5.0 (Linux; Android 14; Pixel 7; Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
      cleanedUa
    )
  }

  @Test
  fun `createCameraImageUri generates valid FileProvider Uri`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val uri = createCameraImageUri(context)
    org.junit.Assert.assertNotNull("Camera Image Uri should not be null", uri)
    assertTrue("Uri must use content scheme", uri.toString().startsWith("content://"))
    assertTrue("Uri must contain fileprovider authority", uri.toString().contains("fileprovider"))
  }
}

