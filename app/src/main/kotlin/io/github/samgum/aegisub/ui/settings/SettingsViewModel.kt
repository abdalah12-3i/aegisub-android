package io.github.samgum.aegisub.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.settings.LayoutMode
import io.github.samgum.aegisub.data.settings.SettingsRepository
import io.github.samgum.aegisub.data.settings.ThemeMode
import io.github.samgum.aegisub.domain.format.TimePrecision
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = repo.settings
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val exportPrecision: StateFlow<TimePrecision> = repo.settings
        .map { it.exportPrecision }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TimePrecision.AUTO)

    val layoutMode: StateFlow<LayoutMode> = repo.settings
        .map { it.layoutMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LayoutMode.AUTO)

    val langCode: StateFlow<String> = repo.settings
        .map { it.langCode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repo.setThemeMode(mode) }
    }

    fun setExportPrecision(precision: TimePrecision) {
        viewModelScope.launch { repo.setExportPrecision(precision) }
    }

    fun setLayoutMode(mode: LayoutMode) {
        viewModelScope.launch { repo.setLayoutMode(mode) }
    }

    fun setLangCode(code: String) {
        viewModelScope.launch {
            repo.setLangCode(code)
            AppCompatDelegate.setApplicationLocales(localeList(code))
        }
    }

    private fun localeList(code: String): LocaleListCompat = when (code) {
        "zh" -> LocaleListCompat.forLanguageTags("zh")
        "en" -> LocaleListCompat.forLanguageTags("en")
        "ar" -> LocaleListCompat.forLanguageTags("ar")
        "tr" -> LocaleListCompat.forLanguageTags("tr")
        else -> LocaleListCompat.getEmptyLocaleList()
    }
}
