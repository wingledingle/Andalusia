package com.silpa.app

import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.silpa.core.design.SilpaTheme
import com.silpa.feature.studio.Panel
import com.silpa.feature.studio.StudioScreen
import com.silpa.feature.studio.StudioViewModel
import org.junit.Test
import org.robolectric.annotation.Config

/** Play Store listing screenshots: phones 1080×1920, tablets 1920×1200 (within Play's 2:1 limit). */
private const val PHONE = "w360dp-h640dp-xxhdpi"
private const val TABLET = "w1280dp-h800dp-hdpi"
private const val DIR = "../fastlane/metadata/android/en-US/images"

abstract class StoreShot : ScreenshotTest() {
    fun studioShot(path: String, panel: Panel) {
        val id = sampleProject()
        lateinit var vm: StudioViewModel
        compose.setContent { SilpaTheme { vm = androidx.lifecycle.viewmodel.compose.viewModel(); StudioScreen(id, onBack = {}, vm = vm) } }
        compose.waitUntil(20_000) { vm.doc != null }
        compose.runOnIdle { vm.panel = panel }
        compose.waitForIdle(); Thread.sleep(1500); compose.waitForIdle()
        compose.onRoot().captureRoboImage(path)
    }
    fun homeShot(path: String) {
        sampleProject()
        compose.setContent { SilpaTheme { SilpaApp(emptyList()) {} } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(path)
    }
}

@Config(qualifiers = PHONE) class Store1Home : StoreShot() { @Test fun shot() = homeShot("$DIR/phoneScreenshots/1_home.png") }
@Config(qualifiers = PHONE) class Store2Studio : StoreShot() { @Test fun shot() = studioShot("$DIR/phoneScreenshots/2_studio.png", Panel.None) }
@Config(qualifiers = PHONE) class Store3Brushes : StoreShot() { @Test fun shot() = studioShot("$DIR/phoneScreenshots/3_brushes.png", Panel.Brushes) }
@Config(qualifiers = PHONE) class Store4Layers : StoreShot() { @Test fun shot() = studioShot("$DIR/phoneScreenshots/4_layers.png", Panel.Layers) }
@Config(qualifiers = PHONE) class Store5Color : StoreShot() { @Test fun shot() = studioShot("$DIR/phoneScreenshots/5_color.png", Panel.Color) }
@Config(qualifiers = PHONE) class Store6Actions : StoreShot() { @Test fun shot() = studioShot("$DIR/phoneScreenshots/6_actions.png", Panel.Actions) }
@Config(qualifiers = TABLET) class Store7Tablet : StoreShot() { @Test fun shot() = studioShot("$DIR/tenInchScreenshots/1_studio.png", Panel.Brushes) }
@Config(qualifiers = TABLET) class Store8TabletHome : StoreShot() { @Test fun shot() = homeShot("$DIR/tenInchScreenshots/2_home.png") }
@Config(qualifiers = TABLET) class Store9TabletLayers : StoreShot() { @Test fun shot() = studioShot("$DIR/tenInchScreenshots/3_layers.png", Panel.Layers) }
