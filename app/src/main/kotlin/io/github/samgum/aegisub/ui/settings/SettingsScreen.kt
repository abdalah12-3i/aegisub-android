package io.github.samgum.aegisub.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samgum.aegisub.R
import io.github.samgum.aegisub.data.hotkeys.KeyCombo
import io.github.samgum.aegisub.data.hotkeys.rememberHotkeyEditor
import io.github.samgum.aegisub.data.settings.LayoutMode
import io.github.samgum.aegisub.data.settings.ThemeMode
import io.github.samgum.aegisub.domain.edit.HotkeyAction
import io.github.samgum.aegisub.domain.format.TimePrecision

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val exportPrecision by viewModel.exportPrecision.collectAsStateWithLifecycle()
    val layoutMode by viewModel.layoutMode.collectAsStateWithLifecycle()
    val langCode by viewModel.langCode.collectAsStateWithLifecycle()
    val hotkeyVM = rememberHotkeyEditor()
    val hotkeys by hotkeyVM.hotkeys.collectAsStateWithLifecycle()
    var capturingAction by remember { mutableStateOf<HotkeyAction?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle(stringResource(R.string.settings_section_theme))
            OptionRow(stringResource(R.string.settings_theme_system), selected = themeMode == ThemeMode.SYSTEM) { viewModel.setThemeMode(ThemeMode.SYSTEM) }
            OptionRow(stringResource(R.string.settings_theme_light), selected = themeMode == ThemeMode.LIGHT) { viewModel.setThemeMode(ThemeMode.LIGHT) }
            OptionRow(stringResource(R.string.settings_theme_dark), selected = themeMode == ThemeMode.DARK) { viewModel.setThemeMode(ThemeMode.DARK) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_section_precision))
            OptionRow(stringResource(R.string.settings_precision_auto), selected = exportPrecision == TimePrecision.AUTO) {
                viewModel.setExportPrecision(TimePrecision.AUTO)
            }
            OptionRow(stringResource(R.string.settings_precision_two), selected = exportPrecision == TimePrecision.TWO_MS) {
                viewModel.setExportPrecision(TimePrecision.TWO_MS)
            }
            OptionRow(stringResource(R.string.settings_precision_three), selected = exportPrecision == TimePrecision.THREE_MS) {
                viewModel.setExportPrecision(TimePrecision.THREE_MS)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_section_layout))
            OptionRow(stringResource(R.string.settings_layout_auto), selected = layoutMode == LayoutMode.AUTO) { viewModel.setLayoutMode(LayoutMode.AUTO) }
            OptionRow(stringResource(R.string.settings_layout_compact), selected = layoutMode == LayoutMode.COMPACT) { viewModel.setLayoutMode(LayoutMode.COMPACT) }
            OptionRow(stringResource(R.string.settings_layout_expanded), selected = layoutMode == LayoutMode.EXPANDED) { viewModel.setLayoutMode(LayoutMode.EXPANDED) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_section_language))
            OptionRow(stringResource(R.string.settings_lang_system), selected = langCode == "system") { viewModel.setLangCode("system") }
            OptionRow(stringResource(R.string.settings_lang_zh), selected = langCode == "zh") { viewModel.setLangCode("zh") }
            OptionRow(stringResource(R.string.settings_lang_en), selected = langCode == "en") { viewModel.setLangCode("en") }
            OptionRow(stringResource(R.string.settings_lang_ar), selected = langCode == "ar") { viewModel.setLangCode("ar") }
            OptionRow(stringResource(R.string.settings_lang_tr), selected = langCode == "tr") { viewModel.setLangCode("tr") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_section_hotkeys))
            HotkeyAction.values().forEach { action ->
                val combo = hotkeys[action]
                ListItem(
                    headlineContent = { Text(action.label()) },
                    supportingContent = { Text(combo?.display() ?: "—") },
                    modifier = Modifier.clickable { capturingAction = action },
                )
            }
            TextButton(onClick = { hotkeyVM.resetAll() }) { Text(stringResource(R.string.hotkey_reset_all)) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_section_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenAbout),
            )
        }
    }
    capturingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { capturingAction = null },
            title = { Text(stringResource(R.string.hotkey_dialog_title)) },
            text = {
                Box(
                    Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown) {
                            hotkeyVM.setBinding(
                                action,
                                KeyCombo(e.isCtrlPressed, e.isShiftPressed, e.isAltPressed, e.key.keyCode),
                            )
                            capturingAction = null
                            true
                        } else false
                    }.padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.hotkey_dialog_prompt, action.label())) }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { capturingAction = null }) { Text(stringResource(R.string.hotkey_dialog_cancel)) } },
        )
    }
}

@Composable
private fun HotkeyAction.label(): String = when (this) {
    HotkeyAction.UNDO -> stringResource(R.string.hotkey_undo)
    HotkeyAction.REDO -> stringResource(R.string.hotkey_redo)
    HotkeyAction.SAVE -> stringResource(R.string.hotkey_save)
    HotkeyAction.EXPORT -> stringResource(R.string.hotkey_export)
    HotkeyAction.FIND_REPLACE -> stringResource(R.string.hotkey_find_replace)
    HotkeyAction.DUPLICATE_LINE -> stringResource(R.string.hotkey_duplicate_line)
    HotkeyAction.DELETE_LINE -> stringResource(R.string.hotkey_delete_line)
    HotkeyAction.SPLIT_LINE -> stringResource(R.string.hotkey_split_line)
    HotkeyAction.JOIN_KEEP_FIRST -> stringResource(R.string.hotkey_join_keep_first)
    HotkeyAction.JOIN_CONCAT -> stringResource(R.string.hotkey_join_concat)
    HotkeyAction.MOVE_LINE_UP -> stringResource(R.string.hotkey_move_line_up)
    HotkeyAction.MOVE_LINE_DOWN -> stringResource(R.string.hotkey_move_line_down)
    HotkeyAction.INSERT_AFTER -> stringResource(R.string.hotkey_insert_after)
    HotkeyAction.SELECT_PREV -> stringResource(R.string.hotkey_select_prev)
    HotkeyAction.SELECT_NEXT -> stringResource(R.string.hotkey_select_next)
    HotkeyAction.PLAY_PAUSE -> stringResource(R.string.hotkey_play_pause)
    HotkeyAction.SEEK_BACK -> stringResource(R.string.hotkey_seek_back)
    HotkeyAction.SEEK_FORWARD -> stringResource(R.string.hotkey_seek_forward)
    HotkeyAction.FRAME_BACK -> stringResource(R.string.hotkey_frame_back)
    HotkeyAction.FRAME_FORWARD -> stringResource(R.string.hotkey_frame_forward)
    HotkeyAction.SET_START_TO_POS -> stringResource(R.string.hotkey_set_start_to_pos)
    HotkeyAction.SET_END_TO_POS -> stringResource(R.string.hotkey_set_end_to_pos)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null) }
        } else null,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
