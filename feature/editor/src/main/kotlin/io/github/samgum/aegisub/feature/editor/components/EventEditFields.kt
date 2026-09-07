package io.github.samgum.aegisub.feature.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import io.github.samgum.aegisub.data.spell.SpellCheckManager
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.time.SubTime
import io.github.samgum.aegisub.feature.editor.R

@Composable
private fun tr(en: String, ar: String, tr: String = en): String {
    val lang = LocalConfiguration.current.locales[0]?.language ?: "en"
    return when {
        lang.startsWith("ar") -> ar
        lang.startsWith("tr") -> tr
        else -> en
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventEditFields(
    event: AssEvent,
    styles: ImmutableList<String>,
    onTextChanged: (String) -> Unit,
    onTimesChanged: (start: SubTime, end: SubTime) -> Unit,
    onStyleChanged: (String) -> Unit,
    onLayerChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var misspelledWords by remember(event.text) {
        mutableStateOf(SpellCheckManager.checkMisspelledWords(context, event.text))
    }

    LaunchedEffect(Unit) {
        SpellCheckManager.loadDictionaries(context)
        misspelledWords = SpellCheckManager.checkMisspelledWords(context, event.text)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val errorColor = MaterialTheme.colorScheme.error
        val spellVisualTransformation = remember(misspelledWords, errorColor) {
            VisualTransformation { annotatedString ->
                val fullText = annotatedString.text
                val builder = buildAnnotatedString {
                    append(fullText)
                    misspelledWords.forEach { errorWord ->
                        var startIndex = fullText.indexOf(errorWord, ignoreCase = true)
                        while (startIndex >= 0) {
                            val endIndex = startIndex + errorWord.length
                            addStyle(
                                style = SpanStyle(
                                    color = errorColor,
                                    textDecoration = TextDecoration.Underline,
                                ),
                                start = startIndex,
                                end = endIndex,
                            )
                            startIndex = fullText.indexOf(errorWord, endIndex, ignoreCase = true)
                        }
                    }
                }
                TransformedText(builder, OffsetMapping.Identity)
            }
        }

        // خانة نص الترجمة مع التدقيق الإملائي
        OutlinedTextField(
            value = event.text,
            onValueChange = onTextChanged,
            label = { Text(stringResource(R.string.edit_text)) },
            visualTransformation = spellVisualTransformation,
            modifier = Modifier.fillMaxWidth(),
        )

        // اقتراحات التصحيح السريعة
        if (misspelledWords.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    text = tr("Spelling Suggestions (Tap to fix):", "اقتراحات التدقيق الإملائي (اضغط للتصحيح):", "Yazım Önerileri (Düzeltmek için dokunun):"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    misspelledWords.forEach { badWord ->
                        var showSuggestions by remember { mutableStateOf(false) }
                        val suggestions = remember(badWord) { SpellCheckManager.getSuggestions(badWord) }

                        Box {
                            AssistChip(
                                onClick = { showSuggestions = true },
                                label = { Text("⚠ $badWord") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    labelColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            )
                            if (showSuggestions) {
                                DropdownMenu(
                                    expanded = showSuggestions,
                                    onDismissRequest = { showSuggestions = false },
                                ) {
                                    if (suggestions.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text(tr("No suggestions found", "لا توجد اقتراحات مطابقة", "Öneri bulunamadı")) },
                                            onClick = { showSuggestions = false },
                                        )
                                    } else {
                                        suggestions.forEach { goodWord ->
                                            DropdownMenuItem(
                                                text = { Text("✔ $goodWord") },
                                                onClick = {
                                                    val fixedText = event.text.replaceFirst(badWord, goodWord, ignoreCase = true)
                                                    onTextChanged(fixedText)
                                                    showSuggestions = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // خانات التوقيت (مضبوطة لتعرض وتحدث الوقت دائماً وباتجاه صحيح)
        var startText by remember(event.start) { mutableStateOf(event.start.toAssString(false)) }
        var endText by remember(event.end) { mutableStateOf(event.end.toAssString(false)) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = {
                        startText = it
                        runCatching { SubTime.parseAss(it) }
                            .onSuccess { s -> onTimesChanged(s, event.end) }
                    },
                    label = { Text(stringResource(R.string.edit_start)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = {
                        endText = it
                        runCatching { SubTime.parseAss(it) }
                            .onSuccess { e -> onTimesChanged(event.start, e) }
                    },
                    label = { Text(stringResource(R.string.edit_end)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // قائمة الأنماط
        var styleExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = styleExpanded,
            onExpandedChange = { styleExpanded = it },
        ) {
            OutlinedTextField(
                value = event.style,
                onValueChange = onStyleChanged,
                label = { Text(stringResource(R.string.edit_style)) },
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = styleExpanded)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            DropdownMenu(
                expanded = styleExpanded,
                onDismissRequest = { styleExpanded = false },
            ) {
                styles.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onStyleChanged(name)
                            styleExpanded = false
                        },
                    )
                }
            }
        }

        // الطبقة
        OutlinedTextField(
            value = event.layer.toString(),
            onValueChange = { raw -> raw.toIntOrNull()?.let(onLayerChanged) },
            label = { Text(stringResource(R.string.edit_layer)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
