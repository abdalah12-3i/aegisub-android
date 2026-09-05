package io.github.samgum.aegisub.navigation

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.samgum.aegisub.feature.editor.navigation.editorRoute
import io.github.samgum.aegisub.feature.editor.navigation.editorScreen
import io.github.samgum.aegisub.feature.editor.navigation.styleEditorScreen
import io.github.samgum.aegisub.feature.editor.navigation.stylesRoute
import io.github.samgum.aegisub.feature.preview.navigation.previewRoute
import io.github.samgum.aegisub.feature.preview.navigation.previewScreen
import io.github.samgum.aegisub.ui.about.AboutScreen
import io.github.samgum.aegisub.ui.home.HomeScreen
import io.github.samgum.aegisub.ui.settings.SettingsScreen
import io.github.samgum.aegisub.ui.settings.SettingsViewModel
import io.github.samgum.aegisub.ui.theme.AegisubTheme

private const val HOME_ROUTE = "home"
private const val SETTINGS_ROUTE = "settings"
private const val ABOUT_ROUTE = "about"

@Composable
fun AppNavigation() {
    val settings: SettingsViewModel = hiltViewModel()
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()
    val langCode by settings.langCode.collectAsStateWithLifecycle()

    LaunchedEffect(langCode) {
        AppCompatDelegate.setApplicationLocales(
            when (langCode) {
                "zh" -> LocaleListCompat.forLanguageTags("zh")
                "en" -> LocaleListCompat.forLanguageTags("en")
                "ar" -> LocaleListCompat.forLanguageTags("ar")
                "tr" -> LocaleListCompat.forLanguageTags("tr")
                else -> LocaleListCompat.getEmptyLocaleList()
            },
        )
    }
    AegisubTheme(themeMode) {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = HOME_ROUTE) {
            composable(HOME_ROUTE) {
                HomeScreen(
                    onOpenProject = { id -> nav.navigate(editorRoute(id)) },
                    onOpenSettings = { nav.navigate(SETTINGS_ROUTE) },
                )
            }
            composable(SETTINGS_ROUTE) {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onOpenAbout = { nav.navigate(ABOUT_ROUTE) },
                )
            }
            composable(ABOUT_ROUTE) {
                AboutScreen(onBack = { nav.popBackStack() })
            }
            editorScreen(
                onBack = { nav.popBackStack() },
                onOpenPreview = { id -> nav.navigate(previewRoute(id)) },
                onOpenStyles = { id -> nav.navigate(stylesRoute(id)) },
            )
            styleEditorScreen(onBack = { nav.popBackStack() })
            previewScreen(onBack = { nav.popBackStack() })
        }
    }
}
