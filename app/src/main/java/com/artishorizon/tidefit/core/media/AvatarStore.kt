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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** Decodes a picked image for the crop screen: upright (EXIF applied) and at most about [PREVIEW] px on its long side. */
    suspend fun loadForCrop(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PREVIEW) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val rotation = resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            if (rotation == 0f) decoded
            else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation) }, true)
        }.getOrNull()
    }

    /** Saves the square starting at ([left], [top]) with edge [side] (bitmap pixels), scaled down to [TARGET]. */
    suspend fun saveCropped(source: Bitmap, left: Int, top: Int, side: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val s = side.coerceIn(1, minOf(source.width, source.height))
            val l = left.coerceIn(0, source.width - s)
            val t = top.coerceIn(0, source.height - s)
            val square = Bitmap.createBitmap(source, l, t, s, s)
            val out = if (s > TARGET) Bitmap.createScaledBitmap(square, TARGET, TARGET, true) else square
            file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        }.getOrDefault(false)
    }

    /** Raw JPEG bytes for backup, or null when no photo is set. */
    suspend fun readBytes(): ByteArray? = withContext(Dispatchers.IO) { if (file.exists()) file.readBytes() else null }

    /** Restores a photo from a backup. Returns false (and leaves the current photo) if the bytes are not a decodable image. */
    suspend fun writeBytes(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (BitmapFactory.decodeByteArray(bytes, 0, bytes.size) == null) false else runCatching { file.writeBytes(bytes); true }.getOrDefault(false)
    }

    suspend fun clear() = withContext(Dispatchers.IO) { file.delete(); Unit }

    suspend fun load(): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) null else runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
    }

    private companion object {
        const val TARGET = 512
        const val PREVIEW = 1600
    }
}

/** Exposes the current profile picture (null when none is set) and drives the pick, crop and save flow. */
@HiltViewModel
class AvatarViewModel @Inject constructor(
    private val store: AvatarStore,
    private val settings: AppSettings
) : ViewModel() {
    val image: StateFlow<ImageBitmap?> = settings.avatarVersion
        .map { if (it == 0L) null else store.load() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _pending = MutableStateFlow<Bitmap?>(null)

    /** The picked image waiting to be cropped, or null when no crop is in progress. */
    val pending: StateFlow<Bitmap?> = _pending.asStateFlow()

    fun pick(uri: Uri) {
        viewModelScope.launch { _pending.value = store.loadForCrop(uri) }
    }

    fun cancelCrop() { _pending.value = null }

    fun confirmCrop(left: Int, top: Int, side: Int) {
        val bitmap = _pending.value ?: return
        _pending.value = null
        viewModelScope.launch {
            if (store.saveCropped(bitmap, left, top, side)) settings.setAvatarVersion(System.currentTimeMillis())
        }
    }

    fun remove() {
        viewModelScope.launch { store.clear(); settings.setAvatarVersion(0L) }
    }
}
