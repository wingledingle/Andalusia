package com.silpa.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Panorama
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.silpa.core.design.EmptyState
import com.silpa.core.design.Spacing

data class CanvasPreset(val name: String, val width: Int, val height: Int, val icon: ImageVector)

const val MAX_CANVAS_PX = 16384

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    brandMark: Int,
    onNewCanvas: (width: Int, height: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalContext.current.resources.displayMetrics
    val presets = remember(metrics) {
        listOf(
            CanvasPreset("Screen", minOf(metrics.widthPixels, metrics.heightPixels), maxOf(metrics.widthPixels, metrics.heightPixels), Icons.Outlined.Smartphone),
            CanvasPreset("Square", 3000, 3000, Icons.Outlined.CropSquare),
            CanvasPreset("A4 · 300 DPI", 2480, 3508, Icons.Outlined.Description),
            CanvasPreset("Comic page", 3307, 4677, Icons.Outlined.AutoStories),
            CanvasPreset("4K", 3840, 2160, Icons.Outlined.Panorama),
            CanvasPreset("8K", 7680, 4320, Icons.Outlined.Panorama),
        )
    }
    var showCustom by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(brandMark),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)),
                        )
                        Spacer(Modifier.width(Spacing.m))
                        Text("Silpa")
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("New canvas")
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.l),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item {
                    PresetCard(
                        title = "Custom",
                        subtitle = "Any size",
                        icon = Icons.Outlined.Tune,
                        aspect = 1f,
                        highlighted = true,
                        onClick = { showCustom = true },
                    )
                }
                items(presets, key = { it.name }) { p ->
                    PresetCard(
                        title = p.name,
                        subtitle = "${p.width} × ${p.height}",
                        icon = p.icon,
                        aspect = p.width.toFloat() / p.height,
                        onClick = { onNewCanvas(p.width, p.height) },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xl))
            SectionHeader("Recent")
            EmptyState(
                icon = Icons.Outlined.Image,
                title = "Your artworks live here",
                body = "Start a canvas above. Everything autosaves, so nothing you draw gets lost.",
            )
        }
    }

    if (showCustom) {
        CustomCanvasDialog(
            onDismiss = { showCustom = false },
            onCreate = { w, h -> showCustom = false; onNewCanvas(w, h) },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
    )
}

@Composable
private fun PresetCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    aspect: Float,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.width(132.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) colors.primaryContainer else colors.surfaceContainerHigh,
        ),
    ) {
        Box(
            Modifier.fillMaxWidth().height(96.dp).padding(Spacing.m),
            contentAlignment = Alignment.Center,
        ) {
            // Miniature of the canvas shape, so the size is recognizable at a glance.
            val a = aspect.coerceIn(0.4f, 2.5f)
            Box(
                Modifier
                    .then(if (a >= 1f) Modifier.width(72.dp) else Modifier.height(72.dp))
                    .aspectRatio(a)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.onSurface.copy(alpha = if (highlighted) 0f else 0.9f))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (highlighted) Icons.Outlined.Add else icon,
                    contentDescription = null,
                    tint = if (highlighted) colors.onPrimaryContainer else colors.surface,
                    modifier = Modifier.size(if (highlighted) 36.dp else 22.dp),
                )
            }
        }
        Column(Modifier.padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.m)) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlighted) colors.onPrimaryContainer else colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CustomCanvasDialog(onDismiss: () -> Unit, onCreate: (Int, Int) -> Unit) {
    var w by rememberSaveable { mutableStateOf("3000") }
    var h by rememberSaveable { mutableStateOf("3000") }
    val wi = w.toIntOrNull()
    val hi = h.toIntOrNull()
    fun valid(v: Int?) = v != null && v in 16..MAX_CANVAS_PX
    val ok = valid(wi) && valid(hi)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom canvas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                OutlinedTextField(
                    value = w, onValueChange = { w = it.filter(Char::isDigit).take(5) },
                    label = { Text("Width (px)") }, singleLine = true,
                    isError = !valid(wi),
                    supportingText = { if (!valid(wi)) Text("16 – $MAX_CANVAS_PX px") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = h, onValueChange = { h = it.filter(Char::isDigit).take(5) },
                    label = { Text("Height (px)") }, singleLine = true,
                    isError = !valid(hi),
                    supportingText = { if (!valid(hi)) Text("16 – $MAX_CANVAS_PX px") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(wi!!, hi!!) }, enabled = ok) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
