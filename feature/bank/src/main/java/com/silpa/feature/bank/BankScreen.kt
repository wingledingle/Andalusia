package com.silpa.feature.bank

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.silpa.core.design.SilpaSkeuo
import com.silpa.core.design.SkeuoPill
import com.silpa.core.design.Spacing
import com.silpa.core.design.StarterArtwork
import com.silpa.ml.Bank
import com.silpa.ml.BankCategories
import com.silpa.ml.BankItem
import com.silpa.ml.ImportResult
import com.silpa.ml.SmartFolder
import com.silpa.monetization.BannerAd
import compose.icons.TablerIcons
import compose.icons.tablericons.Photo
import compose.icons.tablericons.Search
import compose.icons.tablericons.Star
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val colorDots = listOf("red" to 0xFFE53935, "orange" to 0xFFFB8C00, "yellow" to 0xFFFDD835, "green" to 0xFF43A047, "cyan" to 0xFF00ACC1, "blue" to 0xFF1E88E5,
    "purple" to 0xFF8E24AA, "pink" to 0xFFEC407A, "brown" to 0xFF795548, "black" to 0xFF111111, "gray" to 0xFF9E9E9E, "white" to 0xFFFFFFFF)

/** Decode-on-demand thumbnails so big banks stay smooth. */
fun loadThumb(path: String, max: Int): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(path, o)
    var s = 1; while (maxOf(o.outWidth, o.outHeight) / s > max * 2) s *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BankScreen(modifier: Modifier = Modifier, incoming: List<Uri> = emptyList(), onIncomingHandled: () -> Unit = {}) {
    val ctx = LocalContext.current
    val bank = remember { Bank.get(ctx) }
    val items by bank.items.collectAsState()
    val folders by bank.folders.collectAsState()
    val busy by bank.busy.collectAsState()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var color by rememberSaveable { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<BankItem?>(null) }
    var similarTo by remember { mutableStateOf<BankItem?>(null) }
    var saveFolder by remember { mutableStateOf(false) }
    var seeding by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        bank.load()
        if (items.isEmpty()) { seeding = true; bank.ensureStarter(StarterArtwork.map { Triple(it.key, it.res, it.category) }); seeding = false }
    }

    fun importUris(uris: List<Uri>) = scope.launch {
        var added = 0; var parts = 0; var dupes = 0
        for (u in uris) {
            val bmp = withContext(Dispatchers.IO) {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
                var s = 1; while (maxOf(o.outWidth, o.outHeight) / s > 2048) s *= 2
                ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
            } ?: continue
            when (val r = bank.import(bmp, "Imported")) {
                is ImportResult.Added -> { added++; parts += r.items.size - 1 }
                is ImportResult.Duplicate -> dupes++
                is ImportResult.Failed -> snack.showSnackbar(r.reason)
            }
        }
        val msg = buildString {
            append("Added $added image${if (added == 1) "" else "s"}")
            if (parts > 0) append(", plus $parts face part${if (parts == 1) "" else "s"} cut out automatically")
            if (dupes > 0) append(" · skipped $dupes duplicate${if (dupes == 1) "" else "s"}")
        }
        snack.showSnackbar(msg)
    }

    LaunchedEffect(incoming) { if (incoming.isNotEmpty()) { importUris(incoming); onIncomingHandled() } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { if (it.isNotEmpty()) importUris(it) }

    val shown = remember(items, query, category, color, similarTo) {
        similarTo?.let { bank.similar(it) } ?: bank.search(items, query, category, color)
    }
    val counts = remember(items) { items.groupingBy { it.category }.eachCount() }

    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF15182A), Color(0xFF121316))))) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalItemSpacing = Spacing.s,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Column(Modifier.statusBarsPadding().padding(top = Spacing.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Image Bank", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("${items.size} references · sorted automatically on your device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        com.silpa.core.design.SilpaButton(text = "Add images", onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, palette = SilpaSkeuo.Cobalt)
                    }
                    if (busy > 0 || seeding) {
                        Spacer(Modifier.height(8.dp))
                        Text(if (seeding) "Organizing the starter pack…" else "Sorting $busy image${if (busy == 1) "" else "s"}…", style = MaterialTheme.typography.labelMedium)
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(Spacing.m))
                    OutlinedTextField(query, { query = it; similarTo = null }, Modifier.fillMaxWidth(), placeholder = { Text("Search eyes, houses, blue, line art…") },
                        leadingIcon = { Icon(TablerIcons.Search, null) }, singleLine = true, shape = RoundedCornerShape(28.dp))
                    LazyRow(contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(colorDots) { (name, c) ->
                            val sel = color == name
                            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(c)).border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color(0x55FFFFFF), CircleShape)
                                .clickable { color = if (sel) null else name; similarTo = null })
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { FilterChip(category == null, { category = null; similarTo = null }, { Text("All ${items.size}") }) }
                        items(BankCategories) { c -> FilterChip(category == c, { category = if (category == c) null else c; similarTo = null }, { Text("$c ${counts[c] ?: 0}") }) }
                    }
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(folders, key = { it.id }) { f ->
                                SkeuoPill(Modifier.clickable { query = f.query; category = f.category; color = f.color; similarTo = null }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(TablerIcons.Star, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(f.name) }
                                }
                            }
                        }
                        if (query.isNotBlank() || category != null || color != null) TextButton({ saveFolder = true }) { Text("Save as smart folder") }
                    }
                    similarTo?.let { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Similar to a ${s.category.lowercase().trimEnd('s')}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton({ similarTo = null }) { Text("Clear") }
                        }
                    }
                }
            }
            if (shown.isEmpty() && !seeding) item(span = StaggeredGridItemSpan.FullLine) { EmptyBank(items.isEmpty()) }
            items(shown, key = { it.id }) { item -> BankTile(item) { detail = item } }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) { SnackbarHost(snack); BannerAd() }
    }

    detail?.let { item ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            ItemDetail(item, onMove = { c -> scope.launch { bank.move(item.id, c); snack.showSnackbar("Moved to $c — Silpa will sort similar images there") }; detail = null },
                onSimilar = { similarTo = item; detail = null },
                onDelete = { scope.launch { bank.delete(setOf(item.id)) }; detail = null })
        }
    }
    if (saveFolder) {
        var name by remember { mutableStateOf(listOfNotNull(category, color, query.ifBlank { null }).joinToString(" · ")) }
        AlertDialog({ saveFolder = false }, title = { Text("Smart folder") }, text = {
            Column { Text("Updates itself as new images match.", style = MaterialTheme.typography.bodySmall); OutlinedTextField(name, { name = it }, singleLine = true) }
        }, confirmButton = { TextButton({ scope.launch { bank.addFolder(SmartFolder(UUID.randomUUID().toString(), name.ifBlank { "Folder" }, category, query, color)) }; saveFolder = false }) { Text("Save") } },
            dismissButton = { TextButton({ saveFolder = false }) { Text("Cancel") } })
    }
}

@Composable
private fun BankTile(item: BankItem, onClick: () -> Unit) {
    val img by produceState<ImageBitmap?>(null, item.id) { value = withContext(Dispatchers.IO) { loadThumb(item.file.path, 360)?.asImageBitmap() } }
    val ratio = if (item.height > 0) (item.width.toFloat() / item.height).coerceIn(0.5f, 2f) else 1f
    Box(Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable(onClick = onClick)) {
        img?.let { Image(it, "${item.category}: ${item.tags.joinToString()}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Text(item.category, style = MaterialTheme.typography.labelSmall, color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xAA121316)).padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemDetail(item: BankItem, onMove: (String) -> Unit, onSimilar: () -> Unit, onDelete: () -> Unit) {
    val img by produceState<ImageBitmap?>(null, item.id) { value = withContext(Dispatchers.IO) { loadThumb(item.file.path, 900)?.asImageBitmap() } }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Box(Modifier.fillMaxWidth().heightIn(max = 360.dp).aspectRatio((item.width.toFloat() / item.height.coerceAtLeast(1)).coerceIn(0.6f, 2f)).clip(RoundedCornerShape(16.dp)).background(Color.White)) {
            img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        Spacer(Modifier.height(12.dp))
        Text(item.category, style = MaterialTheme.typography.titleLarge)
        Text("${item.style} · ${item.width}×${item.height}${if (item.corrected) " · sorted by you" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { item.palette.forEach { c -> Box(Modifier.size(28.dp).clip(CircleShape).background(Color(c))) } }
        Spacer(Modifier.height(8.dp))
        Text("Tags: " + item.tags.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.silpa.core.design.SilpaButton(text = "Find similar", onClick = onSimilar, palette = SilpaSkeuo.Cobalt)
            TextButton(onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.height(12.dp))
        Text("Wrong category? Move it — Silpa learns from this.", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { BankCategories.forEach { c -> FilterChip(item.category == c, { onMove(c) }, { Text(c) }) } }
    }
}

@Composable
private fun EmptyBank(totallyEmpty: Boolean) {
    val art = StarterArtwork.first { it.key == "anime_eyes" }
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(art.res), null, Modifier.size(180.dp, 120.dp), contentScale = ContentScale.Fit)
        Text(if (totallyEmpty) "Your Image Bank is empty" else "Nothing matches", style = MaterialTheme.typography.titleMedium)
        Text("Share images to Silpa from any app, or tap Add images. Faces are split into eyes, noses and mouths automatically.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 32.dp))
    }
}
