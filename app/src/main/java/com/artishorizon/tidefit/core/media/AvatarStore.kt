package com.artishorizon.tidefit.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artishorizon.tidefit.domain.repository.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the profile picture as a small square JPEG in app-private storage. It never leaves the device. */
@Singleton
class AvatarStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val file get() = File(context.filesDir, "avatar.jpg")

    suspend fun save(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= TARGET && bounds.outHeight / (sample * 2) >= TARGET) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching false
            val rotation = resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val side = minOf(decoded.width, decoded.height)
            val matrix = Matrix().apply {
                postRotate(rotation)
                val scale = TARGET.toFloat() / side
                if (scale < 1f) postScale(scale, scale)
            }
            val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side, matrix, true)
            file.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        }.getOrDefault(false)
    }

    suspend fun clear() = withContext(Dispatchers.IO) { file.delete(); Unit }

    suspend fun load(): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) null else runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
    }

    private companion object { const val TARGET = 512 }
}

/** Exposes the current profile picture (null when none is set) to any screen that shows it. */
@HiltViewModel
class AvatarViewModel @Inject constructor(
    private val store: AvatarStore,
    private val settings: AppSettings
) : ViewModel() {
    val image: StateFlow<ImageBitmap?> = settings.avatarVersion
        .map { if (it == 0L) null else store.load() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun pick(uri: Uri) {
        viewModelScope.launch { if (store.save(uri)) settings.setAvatarVersion(System.currentTimeMillis()) }
    }

    fun remove() {
        viewModelScope.launch { store.clear(); settings.setAvatarVersion(0L) }
    }
}
