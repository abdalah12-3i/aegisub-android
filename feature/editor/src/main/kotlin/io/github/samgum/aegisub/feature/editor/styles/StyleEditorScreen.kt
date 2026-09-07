package io.github.samgum.aegisub.feature.editor.styles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samgum.aegisub.data.font.FontManager
import io.github.samgum.aegisub.domain.model.AssColor
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.feature.editor.components.EditorActions

@Composable
private fun tr(en: String, ar: String, tr: String = en): String {
    val lang = LocalConfiguration.current.locales[0]?.language ?: "en"
    return when {
        lang.startsWith("ar") -> ar
        lang.startsWith("tr") -> tr
        else -> en
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleEditorScreen(
    onBack: () -> Unit,
    viewModel: StyleEditorViewModel = hiltViewModel(),
) {
    val styles by viewModel.styles.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Styles (${styles.size})", "الأنماط (${styles.size})", "Stiller (${styles.size})")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "رجوع", "Geri"))
                    }
                },
                actions = {
                    EditorActions(
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                    )
                    IconButton(onClick = viewModel::addStyle) {
                        Icon(Icons.Filled.Add, contentDescription = tr("Add Style", "إضافة نمط", "Stil Ekle"))
                    }
                },
            )
        },
    ) { padding ->
        if (styles.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(tr("No styles yet, tap + in top bar to add", "لا توجد أنماط بعد، اضغط + بالأعلى للإضافة", "Henüz stil yok"), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(styles, key = { i, s -> s.name + "#" + i }) { index, style ->
                    StyleCard(
                        style = style,
                        onUpdate = { transform -> viewModel.updateStyle(index, transform) },
                        onDelete = { viewModel.deleteStyle(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleCard(
    style: AssStyle,
    onUpdate: (transform: (AssStyle) -> AssStyle) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(style.primary.toComposeColor())
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
                Text(
                    style.name.ifEmpty { tr("(Unnamed)", "(بدون اسم)", "(İsimsiz)") },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = "Expand")
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete Style")
                }
            }
            AnimatedVisibility(visible = expanded) {
                StyleFields(style, onUpdate)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Delete Style", "حذف النمط", "Stili Sil")) },
            text = { Text(tr("Delete style \"${style.name.ifEmpty { "Unnamed" }}\"? Events referencing it will use fallback style. Undo is available.", "هل تريد حذف النمط \"${style.name.ifEmpty { "بدون اسم" }}\"؟ الأسطر المرتبطة به ستستخدم النمط البديل. يمكنك التراجع.", "Stil silinsin mi?")) },
            confirmButton = { TextButton(onClick = { onDelete(); confirmDelete = false }) { Text(tr("Delete", "حذف", "Sil")) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancel", "إلغاء", "İptal")) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun StyleFields(style: AssStyle, onUpdate: (transform: (AssStyle) -> AssStyle) -> Unit) {
    val context = LocalContext.current
    val fontOptions = remember { FontManager.getAvailableFontNames(context) }
    var fontExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(tr("Name & Font", "الاسم والخط", "İsim ve Yazı Tipi"))
        OutlinedTextField(
            value = style.name,
            onValueChange = { v -> onUpdate { it.copy(name = v) } },
            label = { Text(tr("Style Name", "اسم النمط", "Stil Adı")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ExposedDropdownMenuBox(
                expanded = fontExpanded,
                onExpandedChange = { fontExpanded = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = style.font,
                    onValueChange = { v -> onUpdate { it.copy(font = v) } },
                    label = { Text(tr("Font", "الخط", "Yazı Tipi")) },
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                if (fontOptions.isNotEmpty()) {
                    DropdownMenu(
                        expanded = fontExpanded,
                        onDismissRequest = { fontExpanded = false },
                    ) {
                        fontOptions.forEach { fName ->
                            DropdownMenuItem(
                                text = { Text(fName) },
                                onClick = {
                                    onUpdate { it.copy(font = fName) }
                                    fontExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            NumberField(tr("Size", "الحجم", "Boyut"), style.fontSize) { v -> onUpdate { it.copy(fontSize = v) } }
        }

        SectionLabel(tr("Colors (Tap to expand RGBA)", "الألوان (اضغط لضبط RGBA)", "Renkler"))
        AssColorField(tr("Primary Color", "اللون الأساسي", "Birincil Renk"), style.primary) { c -> onUpdate { it.copy(primary = c) } }
        AssColorField(tr("Secondary Color", "اللون الثانوي", "İkincil Renk"), style.secondary) { c -> onUpdate { it.copy(secondary = c) } }
        AssColorField(tr("Outline Color", "لون الحدود (Outline)", "Kenarlık Rengi"), style.outline) { c -> onUpdate { it.copy(outline = c) } }
        AssColorField(tr("Shadow Color", "لون الظل (Shadow)", "Gölge Rengi"), style.shadow) { c -> onUpdate { it.copy(shadow = c) } }

        SectionLabel(tr("Font Attributes", "سمات الخط", "Yazı Tipi Nitelikleri"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleChip(tr("Bold", "عريض", "Kalın"), style.bold) { v -> onUpdate { it.copy(bold = v) } }
            ToggleChip(tr("Italic", "مائل", "İtalik"), style.italic) { v -> onUpdate { it.copy(italic = v) } }
            ToggleChip(tr("Underline", "تسطير", "Altı Çizili"), style.underline) { v -> onUpdate { it.copy(underline = v) } }
            ToggleChip(tr("Strikeout", "يتوسطه خط", "Üstü Çizili"), style.strikeout) { v -> onUpdate { it.copy(strikeout = v) } }
        }

        SectionLabel(tr("Alignment (\\an 1-9)", "المحاذاة (\\an 1-9)", "Hizalama"))
        AlignmentGrid(style.alignment) { a -> onUpdate { it.copy(alignment = a) } }

        SectionLabel(tr("Margins (L / R / V)", "الهوامش (يسار / يمين / عمودي)", "Kenar Boşlukları"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(tr("Left", "يسار", "Sol"), style.margins.left.toDouble()) { v -> onUpdate { it.copy(margins = it.margins.copy(left = v.toInt())) } }
            NumberField(tr("Right", "يمين", "Sağ"), style.margins.right.toDouble()) { v -> onUpdate { it.copy(margins = it.margins.copy(right = v.toInt())) } }
            NumberField(tr("Vertical", "عمودي", "Dikey"), style.margins.vertical.toDouble()) { v -> onUpdate { it.copy(margins = it.margins.copy(vertical = v.toInt())) } }
        }

        SectionLabel(tr("Outline & Shadow", "الحدود والظل", "Kenarlık ve Gölge"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(tr("Outline Width", "سمك الحد", "Kenarlık Kalınlığı"), style.outlineWidth) { v -> onUpdate { it.copy(outlineWidth = v) } }
            NumberField(tr("Shadow Depth", "عمق الظل", "Gölge Derinliği"), style.shadowWidth) { v -> onUpdate { it.copy(shadowWidth = v) } }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = style.borderStyle == 1,
                onClick = { onUpdate { it.copy(borderStyle = 1) } },
                label = { Text(tr("Outline + Shadow", "حد + ظل", "Kenarlık + Gölge")) },
            )
            FilterChip(
                selected = style.borderStyle == 3,
                onClick = { onUpdate { it.copy(borderStyle = 3) } },
                label = { Text(tr("Opaque Box", "خلفية معتمة", "Opak Kutu")) },
            )
        }

        SectionLabel(tr("Transform (Scale / Spacing / Rotation)", "التحويل (التحجيم / التباعد / التدوير)", "Dönüştürme"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(tr("ScaleX %", "تحجيم أفقي X %", "ÖlçekX %"), style.scaleX) { v -> onUpdate { it.copy(scaleX = v) } }
            NumberField(tr("ScaleY %", "تحجيم رأسي Y %", "ÖlçekY %"), style.scaleY) { v -> onUpdate { it.copy(scaleY = v) } }
            NumberField(tr("Spacing", "تباعد الأحرف", "Karakter Boşluğu"), style.spacing) { v -> onUpdate { it.copy(spacing = v) } }
            NumberField(tr("Angle °", "زاوية التدوير °", "Açı °"), style.angle) { v -> onUpdate { it.copy(angle = v) } }
        }

        SectionLabel(tr("Encoding", "الترميز", "Kodlama"))
        NumberField(tr("Encoding", "الترميز", "Kodlama"), style.encoding.toDouble()) { v -> onUpdate { it.copy(encoding = v.toInt()) } }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun NumberField(label: String, value: Double, onParsed: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v
            v.toDoubleOrNull()?.let(onParsed)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.width(96.dp),
    )
}

@Composable
private fun ToggleChip(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(selected = checked, onClick = { onChange(!checked) }, label = { Text(label) })
}

@Composable
private fun AssColorField(label: String, color: AssColor, onChange: (AssColor) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        ) {
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(color.toComposeColor())
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Text(label, modifier = Modifier.padding(start = 8.dp).weight(1f))
            Text(color.toAssString(), style = MaterialTheme.typography.bodySmall)
            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 4.dp)) {
                ColorChannelSlider("R", color.r) { v -> onChange(AssColor(v, color.g, color.b, color.a)) }
                ColorChannelSlider("G", color.g) { v -> onChange(AssColor(color.r, v, color.b, color.a)) }
                ColorChannelSlider("B", color.b) { v -> onChange(AssColor(color.r, color.g, v, color.a)) }
                ColorChannelSlider("A", color.a) { v -> onChange(AssColor(color.r, color.g, color.b, v)) }
            }
        }
    }
}

@Composable
private fun ColorChannelSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(20.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
        Text(value.toString(), modifier = Modifier.width(32.dp))
    }
}

@Composable
private fun AlignmentGrid(selected: Int, onSelect: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in listOf(listOf(7, 8, 9), listOf(4, 5, 6), listOf(1, 2, 3))) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in row) {
                    AlignCell(a, selected == a) { onSelect(a) }
                }
            }
        }
    }
}

@Composable
private fun AlignCell(value: Int, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(value.toString(), color = fg)
    }
}

private fun AssColor.toComposeColor(): Color = Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = a / 255f)
