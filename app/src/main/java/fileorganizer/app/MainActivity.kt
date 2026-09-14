package fileorganizer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import fileorganizer.app.theme.FileKitTheme

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import fileorganizer.app.ui.navigation.AppNavigation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

class MainActivity : ComponentActivity() {

  private val _sharedPdfUris = MutableStateFlow<List<Uri>>(emptyList())

  override fun attachBaseContext(newBase: android.content.Context) {
    val prefs = newBase.getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE)
    val lang = prefs.getString("language", "ar") ?: "ar"
    val locale = java.util.Locale(lang)
    java.util.Locale.setDefault(locale)
    val config = android.content.res.Configuration(newBase.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    val context = newBase.createConfigurationContext(config)
    super.attachBaseContext(context)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    try {
      com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
    } catch (e: Exception) {
      e.printStackTrace()
    }
    try {
      com.google.android.gms.ads.MobileAds.initialize(this) {}
      fileorganizer.app.utils.AdHelper.loadInterstitialAd(this)
    } catch (e: Exception) {
      e.printStackTrace()
    }
    handleIntent(intent)

    val prefs = getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE)
    val lang = prefs.getString("language", "ar") ?: "ar"
    val locale = java.util.Locale(lang)
    java.util.Locale.setDefault(locale)
    val config = android.content.res.Configuration(resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    val isDark = prefs.getBoolean("isDark", true)
    
    @Suppress("DEPRECATION")
    resources.updateConfiguration(config, resources.displayMetrics)
    
    val layoutDirection = if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

    val statusBarStyle = if (isDark) {
        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    } else {
        androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
    }
    
    val navBarStyle = if (isDark) {
        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    } else {
        androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
    }

    enableEdgeToEdge(
        statusBarStyle = statusBarStyle,
        navigationBarStyle = navBarStyle
    )
    
    setContent {
      FileKitTheme(darkTheme = isDark) { 
        CompositionLocalProvider(
            LocalLayoutDirection provides layoutDirection,
            androidx.compose.ui.platform.LocalConfiguration provides config
        ) {
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { 
            val sharedUris by _sharedPdfUris.collectAsState()
            AppNavigation(sharedPdfUris = sharedUris) 
          } 
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    if (intent == null) return
    
    val uris = mutableListOf<Uri>()
    if (intent.action == Intent.ACTION_SEND) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let { uris.add(it) }
      } else {
          @Suppress("DEPRECATION")
          (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let { uris.add(it) }
      }
    } else if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let { uris.addAll(it) }
      } else {
          @Suppress("DEPRECATION")
          intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
      }
    }
    
    _sharedPdfUris.value = uris
  }
}

