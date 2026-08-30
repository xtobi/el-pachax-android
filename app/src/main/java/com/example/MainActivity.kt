package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.io.FileOutputStream

const val TARGET_URL = "https://xtobi.github.io/el-pachax-stable/"

/**
 * Removes the WebView-specific identifiers from the User-Agent string to match
 * standard Chrome on Android, ensuring Google OAuth and identity providers
 * process authentication requests without blocking with disallowed_useragent.
 */
fun cleanUserAgent(originalUa: String): String {
  return originalUa
    .replace("; wv", "")
    .replace("Version/4.0 ", "")
}

/**
 * Creates a secure temporary file URI for camera captures using FileProvider.
 */
fun createCameraImageUri(context: Context): Uri? {
  return try {
    val imageDir = File(context.cacheDir, "images").apply { mkdirs() }
    val imageFile = File.createTempFile("photo_${System.currentTimeMillis()}_", ".jpg", imageDir)
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
  } catch (_: Exception) {
    null
  }
}

/**
 * Saves a base64 encoded data payload (e.g. from local JSON backup export)
 * to the device's public Downloads directory.
 */
fun saveBase64ToDownloads(
  context: Context,
  base64Payload: String,
  suggestedFileName: String,
  mimeType: String
) {
  try {
    val rawBase64 = if (base64Payload.contains(",")) {
      base64Payload.substringAfter(",")
    } else {
      base64Payload
    }
    val bytes = Base64.decode(rawBase64, Base64.DEFAULT)
    val fileName = if (suggestedFileName.isBlank()) "el-pachax-backup.json" else suggestedFileName

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(
          MediaStore.MediaColumns.MIME_TYPE,
          if (mimeType.isNotBlank()) mimeType else "application/json"
        )
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
      }
      val resolver = context.contentResolver
      val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
      if (uri != null) {
        resolver.openOutputStream(uri)?.use { os ->
          os.write(bytes)
        }
        Handler(Looper.getMainLooper()).post {
          Toast.makeText(
            context,
            "Sauvegarde enregistrée dans Téléchargements",
            Toast.LENGTH_LONG
          ).show()
        }
      }
    } else {
      @Suppress("DEPRECATION")
      val downloadsDir =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
      if (!downloadsDir.exists()) {
        downloadsDir.mkdirs()
      }
      val file = File(downloadsDir, fileName)
      FileOutputStream(file).use { fos ->
        fos.write(bytes)
      }
      Handler(Looper.getMainLooper()).post {
        Toast.makeText(
          context,
          "Sauvegarde enregistrée dans Téléchargements",
          Toast.LENGTH_LONG
        ).show()
      }
    }
  } catch (e: Exception) {
    Handler(Looper.getMainLooper()).post {
      Toast.makeText(
        context,
        "Erreur lors de l'enregistrement: ${e.message}",
        Toast.LENGTH_SHORT
      ).show()
    }
  }
}

/**
 * JavaScript interface exposed to WebView to receive downloaded JSON backups or blobs.
 */
class AndroidDownloadBridge(private val context: Context) {
  @JavascriptInterface
  fun saveBase64File(base64Data: String, fileName: String, mimeType: String) {
    saveBase64ToDownloads(context, base64Data, fileName, mimeType)
  }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          ElPachaxWebViewScreen()
        }
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ElPachaxWebViewScreen(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var isLoading by remember { mutableStateOf(true) }
  var loadingProgress by remember { mutableFloatStateOf(0f) }
  var isOffline by remember { mutableStateOf(false) }
  var canGoBack by remember { mutableStateOf(false) }
  var hasLoadedOnce by remember { mutableStateOf(false) }

  // File chooser and camera capture state
  var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
  var currentCameraUri by remember { mutableStateOf<Uri?>(null) }
  var pendingCameraCapture by remember { mutableStateOf(false) }

  // Popup WebView state for Google OAuth / Firebase Authentication popup windows
  var popupWebView by remember { mutableStateOf<WebView?>(null) }
  var popupProgress by remember { mutableFloatStateOf(0f) }
  var isPopupLoading by remember { mutableStateOf(false) }

  val closePopup: () -> Unit = {
    popupWebView?.let { pView ->
      try {
        (pView.parent as? ViewGroup)?.removeView(pView)
        pView.stopLoading()
        pView.visibility = View.GONE
        pView.destroy()
      } catch (_: Exception) {}
    }
    popupWebView = null
    isPopupLoading = false
    popupProgress = 0f
  }

  // Camera Activity Result Launcher
  val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicture()
  ) { success ->
    val uri = currentCameraUri
    if (success && uri != null) {
      fileUploadCallback?.onReceiveValue(arrayOf(uri))
    } else {
      fileUploadCallback?.onReceiveValue(null)
    }
    currentCameraUri = null
    fileUploadCallback = null
  }

  // File Picker / Gallery Activity Result Launcher
  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
      val data = result.data
      val clipData = data?.clipData
      val singleUri = data?.data

      if (clipData != null && clipData.itemCount > 0) {
        val uris = Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
        fileUploadCallback?.onReceiveValue(uris)
      } else if (singleUri != null) {
        fileUploadCallback?.onReceiveValue(arrayOf(singleUri))
      } else {
        fileUploadCallback?.onReceiveValue(null)
      }
    } else {
      fileUploadCallback?.onReceiveValue(null)
    }
    fileUploadCallback = null
  }

  // Camera Runtime Permission Launcher
  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      if (pendingCameraCapture) {
        pendingCameraCapture = false
        val photoUri = createCameraImageUri(context)
        currentCameraUri = photoUri
        if (photoUri != null) {
          cameraLauncher.launch(photoUri)
        } else {
          fileUploadCallback?.onReceiveValue(null)
          fileUploadCallback = null
        }
      }
    } else {
      pendingCameraCapture = false
      Toast.makeText(context, "Permission de la caméra refusée", Toast.LENGTH_SHORT).show()
      fileUploadCallback?.onReceiveValue(null)
      fileUploadCallback = null
    }
  }

  // Check initial network connectivity
  LaunchedEffect(Unit) {
    if (!isNetworkConnected(context)) {
      isOffline = true
      isLoading = false
    }
  }

  // Android Back navigation handler:
  // 1. If a file chooser callback is pending, cancel it gracefully
  // 2. If OAuth popup is open, navigate back in popup history or close popup
  // 3. Otherwise navigate back in main WebView history
  // 4. Otherwise let system exit app
  BackHandler(enabled = fileUploadCallback != null || popupWebView != null || canGoBack) {
    if (fileUploadCallback != null) {
      fileUploadCallback?.onReceiveValue(null)
      fileUploadCallback = null
    } else if (popupWebView != null) {
      if (popupWebView?.canGoBack() == true) {
        popupWebView?.goBack()
      } else {
        closePopup()
      }
    } else {
      webViewRef?.let { webView ->
        if (webView.canGoBack()) {
          webView.goBack()
        }
      }
    }
  }

  // Handle lifecycle pause/resume/destroy
  DisposableEffect(lifecycleOwner, webViewRef) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_PAUSE -> {
          popupWebView?.onPause()
          popupWebView?.pauseTimers()
          webViewRef?.onPause()
          webViewRef?.pauseTimers()
        }
        Lifecycle.Event.ON_RESUME -> {
          popupWebView?.onResume()
          popupWebView?.resumeTimers()
          webViewRef?.onResume()
          webViewRef?.resumeTimers()
        }
        Lifecycle.Event.ON_DESTROY -> {
          fileUploadCallback?.onReceiveValue(null)
          fileUploadCallback = null
          closePopup()
          webViewRef?.destroy()
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      fileUploadCallback?.onReceiveValue(null)
      fileUploadCallback = null
      closePopup()
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .statusBarsPadding()
      .navigationBarsPadding()
  ) {
    // Native Android WebView (Main application window)
    AndroidView(
      modifier = Modifier
        .fillMaxSize()
        .testTag("webview"),
      factory = { ctx ->
        WebView(ctx).apply {
          layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )

          isVerticalScrollBarEnabled = true
          isHorizontalScrollBarEnabled = false
          overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

          // Configure WebSettings for full modern web app support
          settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowContentAccess = true
            allowFileAccess = false
            mediaPlaybackRequiresUserGesture = false

            // Multi-window and popup support for Google OAuth / Firebase signInWithPopup
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true

            // Clean user agent to eliminate disallowed_useragent blocks on Google OAuth
            userAgentString = cleanUserAgent(userAgentString)
          }

          // Configure first-party and third-party cookies (critical for cross-origin Firebase auth)
          val cookieManager = CookieManager.getInstance()
          cookieManager.setAcceptCookie(true)
          cookieManager.setAcceptThirdPartyCookies(this, true)

          // Configure WebViewClient
          webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
              view: WebView?,
              request: WebResourceRequest?
            ): Boolean {
              val url = request?.url ?: return false
              val scheme = url.scheme?.lowercase() ?: ""

              return if (scheme == "http" || scheme == "https") {
                // Allow normal web navigation inside WebView
                false
              } else {
                // Handle external apps/intents (tel:, mailto:, etc.)
                try {
                  val intent = Intent(Intent.ACTION_VIEW, url)
                  ctx.startActivity(intent)
                } catch (_: Exception) {
                  // Fallback if no intent handler available
                }
                true
              }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
              super.onPageStarted(view, url, favicon)
              isLoading = true
              canGoBack = view?.canGoBack() ?: false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
              super.onPageFinished(view, url)
              isLoading = false
              hasLoadedOnce = true
              canGoBack = view?.canGoBack() ?: false
              CookieManager.getInstance().flush()

              // Safety check: If the main window ever landed on the Firebase auth handler
              // (which has an empty body), redirect back to TARGET_URL to prevent blank screen.
              if (url != null && (url.contains("el-pacha.firebaseapp.com") || url.contains("/__/auth/handler"))) {
                view?.loadUrl(TARGET_URL)
              }
            }

            override fun onReceivedError(
              view: WebView?,
              request: WebResourceRequest?,
              error: WebResourceError?
            ) {
              super.onReceivedError(view, request, error)
              if (request?.isForMainFrame == true) {
                isOffline = true
                isLoading = false
              }
            }
          }

          // Configure WebChromeClient with Multi-Window and File Chooser support
          webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
              super.onProgressChanged(view, newProgress)
              loadingProgress = newProgress / 100f
              if (newProgress >= 100) {
                isLoading = false
              }
              canGoBack = view?.canGoBack() ?: false
            }

            override fun onShowFileChooser(
              webView: WebView?,
              filePathCallback: ValueCallback<Array<Uri>>?,
              fileChooserParams: FileChooserParams?
            ): Boolean {
              if (filePathCallback == null) return false

              // Cancel any previous pending callback to prevent WebView lockup
              fileUploadCallback?.onReceiveValue(null)
              fileUploadCallback = filePathCallback

              val isCapture = fileChooserParams?.isCaptureEnabled == true
              val acceptTypes = fileChooserParams?.acceptTypes ?: emptyArray()

              val hasImage = acceptTypes.any { it.contains("image", ignoreCase = true) } || acceptTypes.isEmpty()
              val hasJson = acceptTypes.any { it.contains("json", ignoreCase = true) }

              if (isCapture) {
                // Direct Camera photo capture requested
                val hasCameraPerm = ContextCompat.checkSelfPermission(
                  context,
                  Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

                if (hasCameraPerm) {
                  val photoUri = createCameraImageUri(context)
                  currentCameraUri = photoUri
                  if (photoUri != null) {
                    cameraLauncher.launch(photoUri)
                  } else {
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = null
                  }
                } else {
                  pendingCameraCapture = true
                  cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
                return true
              }

              // Normal file or gallery selection
              try {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                  addCategory(Intent.CATEGORY_OPENABLE)
                  if (hasJson) {
                    type = "application/json"
                    putExtra(
                      Intent.EXTRA_MIME_TYPES,
                      arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
                    )
                  } else if (hasImage) {
                    type = "image/*"
                    putExtra(
                      Intent.EXTRA_MIME_TYPES,
                      arrayOf("image/jpeg", "image/png", "image/webp", "image/jpg", "image/*")
                    )
                  } else {
                    type = "*/*"
                  }
                  if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                  }
                }

                val chooserTitle = if (hasImage) "Choisir une photo" else if (hasJson) "Choisir la sauvegarde" else "Choisir un fichier"
                filePickerLauncher.launch(Intent.createChooser(intent, chooserTitle))
                return true
              } catch (e: Exception) {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = null
                return false
              }
            }

            override fun onCreateWindow(
              view: WebView?,
              isDialog: Boolean,
              isUserGesture: Boolean,
              resultMsg: Message?
            ): Boolean {
              if (resultMsg == null || view == null) return false

              // Close any previously active popup view
              closePopup()

              // Create popup WebView dedicated to the OAuth flow
              val popup = WebView(view.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT
                )
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = true

                settings.apply {
                  javaScriptEnabled = true
                  domStorageEnabled = true
                  @Suppress("DEPRECATION")
                  databaseEnabled = true
                  setSupportMultipleWindows(true)
                  javaScriptCanOpenWindowsAutomatically = true
                  cacheMode = WebSettings.LOAD_DEFAULT
                  mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                  allowContentAccess = true
                  allowFileAccess = false
                  mediaPlaybackRequiresUserGesture = false
                  userAgentString = cleanUserAgent(userAgentString)
                }

                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                cm.setAcceptThirdPartyCookies(this, true)

                webChromeClient = object : WebChromeClient() {
                  override fun onCloseWindow(window: WebView?) {
                    super.onCloseWindow(window)
                    closePopup()
                  }

                  override fun onProgressChanged(v: WebView?, newProgress: Int) {
                    super.onProgressChanged(v, newProgress)
                    popupProgress = newProgress / 100f
                    isPopupLoading = newProgress < 100
                  }
                }

                webViewClient = object : WebViewClient() {
                  override fun shouldOverrideUrlLoading(
                    v: WebView?,
                    request: WebResourceRequest?
                  ): Boolean {
                    val url = request?.url ?: return false
                    val scheme = url.scheme?.lowercase() ?: ""

                    return if (scheme == "http" || scheme == "https") {
                      false
                    } else {
                      try {
                        val intent = Intent(Intent.ACTION_VIEW, url)
                        view.context.startActivity(intent)
                      } catch (_: Exception) {}
                      true
                    }
                  }

                  override fun onPageFinished(v: WebView?, url: String?) {
                    super.onPageFinished(v, url)
                    CookieManager.getInstance().flush()
                  }
                }
              }

              // Bind popup WebView to the transport message so window.opener is established
              val transport = resultMsg.obj as WebView.WebViewTransport
              transport.webView = popup
              resultMsg.sendToTarget()

              popupWebView = popup
              isPopupLoading = true
              return true
            }

            override fun onCloseWindow(window: WebView?) {
              super.onCloseWindow(window)
              closePopup()
            }
          }

          // Register JavaScript Interface and Download Listener for Local Backup export
          addJavascriptInterface(AndroidDownloadBridge(ctx), "AndroidDownloadBridge")

          setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            if (url.startsWith("blob:")) {
              val cleanFileName = "el-pachax-backup-${System.currentTimeMillis() / 1000}.json"
              val js = """
                (function() {
                  try {
                    fetch('$url')
                      .then(function(res) { return res.blob(); })
                      .then(function(blob) {
                        var reader = new FileReader();
                        reader.onloadend = function() {
                          if (window.AndroidDownloadBridge) {
                            window.AndroidDownloadBridge.saveBase64File(reader.result, '$cleanFileName', '$mimetype');
                          }
                        };
                        reader.readAsDataURL(blob);
                      })
                      .catch(function(err) {
                        console.error('Blob download failed:', err);
                      });
                  } catch(e) {
                    console.error('Download error:', e);
                  }
                })();
              """.trimIndent()
              evaluateJavascript(js, null)
            } else if (url.startsWith("data:")) {
              saveBase64ToDownloads(ctx, url, "el-pachax-backup.json", mimetype)
            } else {
              try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                  setMimeType(mimetype)
                  addRequestHeader("User-Agent", userAgent)
                  setDescription("Téléchargement du fichier")
                  setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
                  setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                  setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    URLUtil.guessFileName(url, contentDisposition, mimetype)
                  )
                }
                val downloadManager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                downloadManager?.enqueue(request)
                Toast.makeText(ctx, "Téléchargement en cours...", Toast.LENGTH_SHORT).show()
              } catch (_: Exception) {
                try {
                  val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                  ctx.startActivity(intent)
                } catch (_: Exception) {}
              }
            }
          }

          webViewRef = this
          loadUrl(TARGET_URL)
        }
      },
      update = {
        // Can be used if external props change
      }
    )

    // Top Linear Progress Indicator while loading
    AnimatedVisibility(
      visible = isLoading && !isOffline,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.TopCenter)
    ) {
      if (loadingProgress > 0f && loadingProgress < 1f) {
        LinearProgressIndicator(
          progress = { loadingProgress },
          modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .testTag("loading_indicator")
        )
      } else {
        LinearProgressIndicator(
          modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .testTag("loading_indicator")
        )
      }
    }

    // Initial Splash / Fullscreen Loading spinner on first load before content is ready
    if (isLoading && !hasLoadedOnce && !isOffline) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          CircularProgressIndicator(
            modifier = Modifier
              .size(48.dp)
              .testTag("loading_spinner"),
            color = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "Loading El Pachax...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
          )
        }
      }
    }

    // Google Sign-In / OAuth Popup Window overlay
    if (popupWebView != null) {
      popupWebView?.let { pWebView ->
        Surface(
          modifier = Modifier
            .fillMaxSize()
            .testTag("popup_overlay"),
          color = MaterialTheme.colorScheme.background
        ) {
          Column(modifier = Modifier.fillMaxSize()) {
            // Popup Top Bar with Title and Close ("✕") button
            Surface(
              modifier = Modifier.fillMaxWidth(),
              color = MaterialTheme.colorScheme.surfaceVariant,
              tonalElevation = 3.dp
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "تسجيل الدخول — El Pachax",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                  onClick = { closePopup() },
                  modifier = Modifier.testTag("close_popup_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Sign-In",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }

            if (isPopupLoading) {
              if (popupProgress > 0f && popupProgress < 1f) {
                LinearProgressIndicator(
                  progress = { popupProgress },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .testTag("popup_loading_indicator")
                )
              } else {
                LinearProgressIndicator(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .testTag("popup_loading_indicator")
                )
              }
            }

            AndroidView(
              modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .testTag("popup_webview"),
              factory = {
                (pWebView.parent as? ViewGroup)?.removeView(pWebView)
                pWebView
              }
            )
          }
        }
      }
    }

    // Offline / No Internet Connection View
    if (isOffline) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .testTag("offline_view"),
        contentAlignment = Alignment.Center
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "No internet connection",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "No internet connection",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Please check your network connection and try again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(24.dp))
          Button(
            onClick = {
              if (isNetworkConnected(context)) {
                isOffline = false
                isLoading = true
                val currentUrl = webViewRef?.url
                if (currentUrl != null && currentUrl.isNotEmpty() && currentUrl != "about:blank") {
                  webViewRef?.reload()
                } else {
                  webViewRef?.loadUrl(TARGET_URL)
                }
              } else {
                // Retry anyway in case network state is transitioning
                isOffline = false
                isLoading = true
                webViewRef?.loadUrl(TARGET_URL)
              }
            },
            modifier = Modifier.testTag("retry_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary
            )
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Retry",
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("Retry")
          }
        }
      }
    }
  }
}

/**
 * Checks if the device has an active network with internet capability.
 */
fun isNetworkConnected(context: Context): Boolean {
  val connectivityManager =
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
  val activeNetwork = connectivityManager.activeNetwork ?: return false
  val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
  return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}
