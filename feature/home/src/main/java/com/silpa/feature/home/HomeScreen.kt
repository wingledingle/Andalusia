package com.silpa.feature.home

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.silpa.core.design.HeroArt
import com.silpa.core.design.SilpaSkeuo
import com.silpa.core.design.SkeuoPill
import com.silpa.core.design.Spacing
import com.silpa.core.design.StarterArtwork
import com.silpa.engine.Compositor
import com.silpa.engine.Document
import com.silpa.engine.Ops
import com.silpa.engine.ProjectInfo
import com.silpa.engine.ProjectStore
import com.silpa.engine.Psd
import com.silpa.engine.Selection
import com.silpa.monetization.BannerAd
import com.silpa.monetization.ProManager
import compose.icons.TablerIcons
import compose.icons.tablericons.Crown
import compose.icons.tablericons.FileImport
import compose.icons.tablericons.Photo
import compose.icons.tablericons.Plus
import io.github.skeuocompose.SkeuoBevelKind
import io.github.skeuocompose.SkeuoButton
import io.github.skeuocompose.SkeuoSurface
import io.github.skeuocompose.SkeuoSurfaceStyle
import io.github.skeuocompose.SkeuoTextureKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

data class CanvasPreset(val name: String, val width: Int, val height: Int)

const val MAX_CANVAS_PX = 16384

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(brandMark: Int, onOpenProject: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val store = remember { ProjectStore(File(ctx.filesDir, "projects")) }
    var refresh by remember { mutableIntStateOf(0) }
    val projects by produceState(emptyList<ProjectInfo>(), refresh) { value = withContext(Dispatchers.IO) { store.list() } }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var showCustom by rememberSaveable { mutableStateOf(false) }
    var showPro by rememberSaveable { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<ProjectInfo?>(null) }
    var renameFor by remember { mutableStateOf<ProjectInfo?>(null) }
    var deleteFor by remember { mutableStateOf<ProjectInfo?>(null) }
    var working by remember { mutableStateOf<String?>(null) }
    val pro by remember { ProManager.get(ctx).isPro }.collectAsState()
    val metrics = ctx.resources.displayMetrics

    fun create(w: Int, h: Int, name: String) = scope.launch {
        val id = withContext(Dispatchers.IO) { store.create(name, w, h) }
        onOpenProject(id)
    }

    fun saveDoc(doc: Document) = scope.launch {
        val thumb = Compositor(doc, Selection(doc.width, doc.height)).renderPreview(512)
        val id = withContext(Dispatchers.IO) { store.saveNew(doc, thumb) }
        working = null; onOpenProject(id)
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        working = "Importing…"
        scope.launch {
            val name = uri.lastPathSegment.orEmpty().lowercase()
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)!!.use { input ->
                        val head = input.readNBytes(4)
                        ctx.contentResolver.openInputStream(uri)!!.use { full ->
                            if (String(head) == "8BPS") Psd.read(full).also { it.name = "Imported PSD" }
                            else if (head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) { store.importPackage(full); null }
                            else throw IllegalArgumentException("Choose a .psd or .silpa file")
                        }
                    }
                }
            }
            r.onSuccess { d -> if (d != null) saveDoc(d) else { working = null; refresh++; snack.showSnackbar("Project imported") } }
            r.onFailure { working = null; snack.showSnackbar(it.message ?: "Import failed") }
            if (name.isEmpty()) Unit
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        working = "Preparing canvas…"
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
                var s = 1; while (maxOf(o.outWidth, o.outHeight) / s > 4096) s *= 2
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
            }
            if (bmp == null) { working = null; snack.showSnackbar("Couldn't read that photo"); return@launch }
            val doc = Document(bmp.width, bmp.height).apply { name = "Photo canvas" }
            val l = doc.newLayer("Photo"); Ops.placeBitmap(l, bmp, doc, android.graphics.RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()))
            doc.layers += l; doc.layers += doc.newLayer("Layer 2"); doc.activeIndex = 1
            saveDoc(doc)
        }
    }

    val presets = remember(metrics) {
        listOf(
            CanvasPreset("Screen", minOf(metrics.widthPixels, metrics.heightPixels), maxOf(metrics.widthPixels, metrics.heightPixels)),
            CanvasPreset("Square", 3000, 3000),
            CanvasPreset("A4 · 300 DPI", 2480, 3508),
            CanvasPreset("Comic page", 3307, 4677),
            CanvasPreset("Webtoon", 800, 12800),
            CanvasPreset("4K", 3840, 2160),
            CanvasPreset("Icon", 1024, 1024),
            CanvasPreset("8K", 7680, 4320),
        )
    }

    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF15182A), Color(0xFF121316), Color(0xFF121316))))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (wide) 200.dp else 150.dp),
                contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.statusBarsPadding().padding(top = Spacing.m, bottom = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(brandMark), null, Modifier.size(44.dp).shadow(6.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(Spacing.m))
                        Text("Silpa", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (!pro) SkeuoPill(Modifier.combinedClickable(onClick = { showPro = true }), palette = io.github.skeuocompose.SkeuoPalettes.Gold) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Icon(TablerIcons.Crown, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Go Pro", fontWeight = FontWeight.SemiBold) }
                        } else SkeuoPill(palette = SilpaSkeuo.Cobalt) { Text("Pro", fontWeight = FontWeight.SemiBold) }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Hero(wide) { create(3000, 3000, "Untitled") } }
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("New canvas") }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.m), contentPadding = PaddingValues(vertical = 8.dp)) {
                        item { PaperSheet("Custom", "Any size", 1f, highlighted = true) { showCustom = true } }
                        items(presets, key = { it.name }) { p -> PaperSheet(p.name, "${p.width} × ${p.height}", p.width.toFloat() / p.height) { create(p.width, p.height, p.name) } }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(vertical = 4.dp)) {
                        SkeuoPill(Modifier.combinedClickable(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Icon(TablerIcons.Photo, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("From photo") }
                        }
                        SkeuoPill(Modifier.combinedClickable(onClick = { openFile.launch(arrayOf("*/*")) })) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Icon(TablerIcons.FileImport, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Import PSD / .silpa") }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(if (projects.isEmpty()) "Your gallery" else "Your gallery · ${projects.size}") }
                if (projects.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyGallery() }
                items(projects, key = { it.id }) { p ->
                    Box {
                        ProjectCard(p, onClick = { onOpenProject(p.id) }, onLongClick = { menuFor = p })
                        DropdownMenu(menuFor?.id == p.id, { menuFor = null }) {
                            DropdownMenuItem({ Text("Rename") }, { renameFor = p; menuFor = null })
                            DropdownMenuItem({ Text("Duplicate") }, { menuFor = null; scope.launch { withContext(Dispatchers.IO) { store.duplicate(p.id) }; refresh++ } })
                            DropdownMenuItem({ Text("Share .silpa") }, {
                                menuFor = null
                                scope.launch {
                                    val f = File(File(ctx.cacheDir, "share").apply { mkdirs() }, p.name.replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "Silpa" } + ".silpa")
                                    withContext(Dispatchers.IO) { f.outputStream().use { store.exportPackage(p.id, it) } }
                                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share"))
                                }
                            })
                            DropdownMenuItem({ Text("Delete", color = MaterialTheme.colorScheme.error) }, { deleteFor = p; menuFor = null })
                        }
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            SnackbarHost(snack)
            BannerAd()
        }
        working?.let {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) { Text(it, style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (showCustom) CustomCanvasDialog({ showCustom = false }) { w, h -> showCustom = false; create(w, h, "Untitled") }
    if (showPro) ProDialog { showPro = false }
    renameFor?.let { p ->
        var name by remember { mutableStateOf(p.name) }
        AlertDialog({ renameFor = null }, title = { Text("Rename") }, text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton({ scope.launch { withContext(Dispatchers.IO) { store.rename(p.id, name.ifBlank { "Untitled" }) }; refresh++ }; renameFor = null }) { Text("Rename") } },
            dismissButton = { TextButton({ renameFor = null }) { Text("Cancel") } })
    }
    deleteFor?.let { p ->
        AlertDialog({ deleteFor = null }, title = { Text("Delete “${p.name}”?") }, text = { Text("This can't be undone. Export a .silpa copy first if you might want it back.") },
            confirmButton = { TextButton({ scope.launch { withContext(Dispatchers.IO) { store.delete(p.id) }; refresh++ }; deleteFor = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ deleteFor = null }) { Text("Cancel") } })
    }
}

@Composable
private fun SectionTitle(t: String) = Text(t, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Spacing.l, bottom = 2.dp))

@Composable
private fun Hero(wide: Boolean, onStart: () -> Unit) {
    val pager = rememberPagerState { HeroArt.size }
    LaunchedEffect(pager) { while (true) { delay(5000); pager.animateScrollToPage((pager.currentPage + 1) % HeroArt.size) } }
    val lines = listOf("Draw your world", "Characters come alive", "Ink like a pro", "Paint anywhere", "Sketch every day", "Your story, your style", "Classic meets new")
    val tints = listOf(Color(0xFF1F3FD1), Color(0xFF7A2E8E), Color(0xFF0E6E8C), Color(0xFF8C3A2B), Color(0xFF2E6B3F), Color(0xFF6B4E1F), Color(0xFF3A2E6B))
    Column {
        HorizontalPager(pager, pageSpacing = 12.dp) { page ->
            val art = HeroArt[page]
            Box(Modifier.fillMaxWidth().height(if (wide) 280.dp else 210.dp).shadow(12.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(tints[page % tints.size], Color(0xFF121316))))) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(start = 22.dp, end = 8.dp)) {
                        Text(lines[page % lines.size], style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(6.dp))
                        Text("Pro brushes, layers, comics & animation.", style = MaterialTheme.typography.bodyMedium, color = Color(0xCCFFFFFF))
                        Spacer(Modifier.height(14.dp))
                        SkeuoButton(text = "Start drawing", onClick = onStart, palette = SilpaSkeuo.Cobalt)
                    }
                    // Art sits on a white "print" card so every illustration reads cleanly on the gradient.
                    Box(Modifier.fillMaxHeight().aspectRatio(0.85f).padding(14.dp).shadow(10.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White)) {
                        Image(painterResource(art.res), null, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            repeat(HeroArt.size) { i ->
                Box(Modifier.padding(3.dp).size(if (i == pager.currentPage) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(if (i == pager.currentPage) Color(0xFF4CB4E6) else Color(0x55FFFFFF)))
            }
        }
    }
}

/** Skeuomorphic sheet of paper showing the canvas proportions. */
@Composable
private fun PaperSheet(title: String, subtitle: String, aspect: Float, highlighted: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.width(112.dp).clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(112.dp, 120.dp), contentAlignment = Alignment.Center) {
            val a = aspect.coerceIn(0.35f, 2.2f)
            SkeuoSurface(
                modifier = Modifier.then(if (a >= 1f) Modifier.width(96.dp) else Modifier.height(104.dp)).aspectRatio(a),
                palette = if (highlighted) SilpaSkeuo.Cobalt else SilpaSkeuo.Paper,
                style = SkeuoSurfaceStyle(shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(0.dp), raisedElevation = 10.dp, textureKind = SkeuoTextureKind.Grainy, bevelKind = SkeuoBevelKind.Subtle),
            ) { if (highlighted) Icon(TablerIcons.Plus, null, Modifier.size(32.dp)) }
        }
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(p: ProjectInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    val img by produceState<ImageBitmap?>(null, p.id, p.modified) { value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(p.thumb.path)?.asImageBitmap() } }
    Column(Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(10.dp).shadow(6.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            img?.let { Image(it, p.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            Text(p.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${p.width}×${p.height} · ${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(p.modified))}${if (p.animation) " · anim" else ""}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyGallery() {
    val art = StarterArtwork.first { it.key == "scarf_girl" }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(art.res), null, Modifier.size(110.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Your artworks live here", style = MaterialTheme.typography.titleMedium)
            Text("Pick a canvas above. Everything autosaves after every stroke, and long-press any artwork for more options.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProDialog(dismiss: () -> Unit) {
    val ctx = LocalContext.current
    val pm = remember { ProManager.get(ctx) }
    val price by pm.price.collectAsState()
    var note by remember { mutableStateOf<String?>(null) }
    AlertDialog(dismiss, title = { Text("Silpa Pro") }, text = {
        Column {
            Text("A one-time purchase, yours forever:", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            listOf("No ads, anywhere", "Supports an independent drawing app", "Every future Pro feature included").forEach { Text("•  $it") }
            note?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = {
        SkeuoButton(text = price?.let { "Buy · $it" } ?: "Buy", onClick = {
            val a = ctx as? Activity
            if (a == null || !pm.buy(a)) note = "The Play Store isn't available right now. Check your connection and try again."
        }, palette = SilpaSkeuo.Cobalt)
    }, dismissButton = { TextButton(dismiss) { Text("Not now") } })
}

@Composable
private fun CustomCanvasDialog(onDismiss: () -> Unit, onCreate: (Int, Int) -> Unit) {
    var w by rememberSaveable { mutableStateOf("3000") }
    var h by rememberSaveable { mutableStateOf("3000") }
    fun valid(v: Int?) = v != null && v in 16..MAX_CANVAS_PX
    val wi = w.toIntOrNull(); val hi = h.toIntOrNull()
    val mp = if (valid(wi) && valid(hi)) wi!!.toLong() * hi!! / 1_000_000.0 else 0.0
    AlertDialog(onDismiss, title = { Text("Custom canvas") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            OutlinedTextField(w, { w = it.filter(Char::isDigit).take(5) }, label = { Text("Width (px)") }, singleLine = true, isError = !valid(wi),
                supportingText = { if (!valid(wi)) Text("16 – $MAX_CANVAS_PX px") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(h, { h = it.filter(Char::isDigit).take(5) }, label = { Text("Height (px)") }, singleLine = true, isError = !valid(hi),
                supportingText = { if (!valid(hi)) Text("16 – $MAX_CANVAS_PX px") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (mp > 0) Text("%.1f megapixels. Empty areas use no memory; layers are only limited by your device.".format(mp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton({ onCreate(wi!!, hi!!) }, enabled = valid(wi) && valid(hi)) { Text("Create") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
