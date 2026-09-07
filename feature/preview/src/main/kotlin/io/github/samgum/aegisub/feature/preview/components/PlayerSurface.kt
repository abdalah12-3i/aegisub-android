package io.github.samgum.aegisub.feature.preview.components

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import io.github.samgum.aegisub.feature.preview.Media3VideoPlayer
import io.github.samgum.aegisub.feature.preview.VideoPlayer

@Composable
fun PlayerSurface(player: VideoPlayer, modifier: Modifier = Modifier) {
    val exo = (player as? Media3VideoPlayer)?.exoPlayer
    if (exo != null) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                PlayerView(context).apply {
                    this.player = exo
                    useController = false
                    // إخفاء عارض الترجمات الافتراضي ذو المربع الأسود
                    subtitleView?.visibility = View.GONE
                }
            },
            update = { view ->
                view.player = exo
                view.subtitleView?.visibility = View.GONE
            },
        )
    } else {
        Box(modifier.fillMaxSize().background(Color.Black))
    }
}
