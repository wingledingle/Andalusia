package com.silpa.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.silpa.core.design.SilpaTheme
import com.silpa.monetization.Ads
import com.silpa.monetization.ProManager

class MainActivity : ComponentActivity() {
    /** Images shared into Silpa, waiting to be filed into the Image Bank. */
    private val incoming = mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ProManager.get(this)
        Ads.bannerUnitId = BuildConfig.ADMOB_BANNER_ID
        Ads.init(this)
        handle(intent)
        setContent { SilpaTheme { SilpaApp(incoming.value) { incoming.value = emptyList() } } }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    private fun handle(i: Intent?) {
        i ?: return
        val uris = when (i.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) incoming.value = uris
    }
}
