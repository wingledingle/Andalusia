package com.silpa.monetization

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unobtrusive ads, by design:
 *  - one small adaptive banner, only on Home and the Image Bank screens;
 *  - never in the studio, never full-screen, never video, never pop-ups;
 *  - nothing at all for Pro users;
 *  - requested only after the user's privacy choice (Google UMP consent).
 *
 * Replace the test IDs below with your own AdMob IDs before release (and in AndroidManifest.xml).
 */
object Ads {
    const val BANNER_UNIT_ID = "ca-app-pub-3940256099942544/9214589741" // Google's test adaptive banner
    private val started = AtomicBoolean(false)
    private val consentChecked = AtomicBoolean(false)

    /** Call from the activity on launch: asks for consent where required, then starts the SDK. */
    fun init(activity: Activity) {
        if (ProManager.get(activity).isPro.value || !consentChecked.compareAndSet(false, true)) return
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { if (info.canRequestAds()) start(activity) }
        }, { if (info.canRequestAds()) start(activity) })
        if (info.canRequestAds()) start(activity)
    }

    private fun start(activity: Activity) {
        if (started.compareAndSet(false, true)) Thread { MobileAds.initialize(activity) }.start()
    }

    fun canShow(activity: Activity) = started.get() && UserMessagingPlatform.getConsentInformation(activity).canRequestAds()
}

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val pro by remember { ProManager.get(ctx).isPro }.collectAsState()
    val activity = ctx as? Activity ?: return
    if (pro || !Ads.canShow(activity)) return
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val w = maxWidth.value.toInt()
        AndroidView(factory = { c ->
            AdView(c).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(c, w))
                adUnitId = Ads.BANNER_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        }, onRelease = { it.destroy() })
    }
}
