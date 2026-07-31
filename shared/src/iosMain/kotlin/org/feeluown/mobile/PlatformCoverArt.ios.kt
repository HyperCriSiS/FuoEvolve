package org.feeluown.mobile

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Pixmap
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.impl.use
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithURL
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val MAX_COVER_SIZE_PX = 768

@Composable
actual fun PlatformCoverArt(
    title: String,
    imageUrl: String?,
    modifier: Modifier,
    placeholder: CoverPlaceholder,
) {
    val bitmap = rememberPlatformCoverImage(imageUrl)
    if (bitmap != null) {
        Image(bitmap, title, modifier, contentScale = ContentScale.Crop)
    } else {
        Surface(modifier = modifier, color = MaterialTheme.colorScheme.primaryContainer) {
            BoxWithConstraints(contentAlignment = Alignment.Center) {
                val containerSize = minOf(maxWidth, maxHeight)
                Icon(
                    imageVector = when (placeholder) {
                        CoverPlaceholder.Song -> Icons.Filled.MusicNote
                        CoverPlaceholder.Album -> Icons.Filled.Album
                        CoverPlaceholder.Artist -> Icons.Filled.Mic
                        CoverPlaceholder.Playlist -> Icons.AutoMirrored.Filled.QueueMusic
                        CoverPlaceholder.DailyRecommendation -> Icons.Filled.CalendarMonth
                    },
                    contentDescription = null,
                    modifier = Modifier.size(containerSize * 0.45f),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
internal actual fun rememberPlatformCoverImage(imageUrl: String?): ImageBitmap? {
    var image by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(imageUrl) {
        image = imageUrl?.takeIf { it.isNotBlank() }?.let {
            runCatching { PlatformCoverImageCache.getOrLoad(it) { loadImage(it) } }.getOrNull()
        }
    }
    return image
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun loadImage(imageUrl: String): ImageBitmap? = withContext(Dispatchers.Default) {
    val resolvedUrl = when {
        imageUrl.startsWith("fuo-cover:") -> {
            val query = imageUrl.substringAfter('?', "")
            query.split('&').firstNotNullOfOrNull { entry ->
                val parts = entry.split('=', limit = 2)
                parts.getOrNull(1)?.takeIf { parts.firstOrNull() == "albumArt" && it.isNotBlank() }
            }
        }
        else -> imageUrl
    } ?: return@withContext null
    val url = NSURL.URLWithString(resolvedUrl) ?: return@withContext null
    val data = if (url.scheme == "file") {
        url.path?.let { NSFileManager.defaultManager.contentsAtPath(it) }
    } else {
        fetchData(url)
    } ?: return@withContext null
    val bytes = data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt()) ?: return@withContext null
    runCatching {
        Image.makeFromEncoded(bytes).toCoverImageBitmap()
    }.getOrNull()
}

private fun Image.toCoverImageBitmap(): ImageBitmap {
    if (width <= MAX_COVER_SIZE_PX && height <= MAX_COVER_SIZE_PX) {
        return toComposeImageBitmap()
    }

    val scale = minOf(
        MAX_COVER_SIZE_PX.toFloat() / width,
        MAX_COVER_SIZE_PX.toFloat() / height,
    )
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    val imageInfo = ImageInfo.makeN32(targetWidth, targetHeight, ColorAlphaType.PREMUL)
    val data = Data.makeUninitialized(imageInfo.computeMinByteSize())
    return data.use { pixelData ->
        Pixmap().use { pixmap ->
            pixmap.reset(
                info = imageInfo,
                addr = pixelData.writableData(),
                rowBytes = imageInfo.minRowBytes,
                underlyingMemoryOwner = pixelData,
            )
            check(scalePixels(pixmap, SamplingMode.MITCHELL, cache = false))
            Image.makeFromPixmap(pixmap).toComposeImageBitmap()
        }
    }
}

private suspend fun fetchData(url: NSURL): NSData? = suspendCoroutine { continuation ->
    NSURLSession.sharedSession.dataTaskWithURL(url) { data, _, _ ->
        continuation.resume(data)
    }.resume()
}
