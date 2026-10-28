package eu.kanade.tachiyomi.data.coil

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import ca.mpreg.imagedecoder.ImageDecoder
import coil3.Canvas
import coil3.Image
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import logcat.LogPriority
import okio.BufferedSource
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat

/**
 * A [Decoder] that uses [ImageDecoder] (libvips-based) to decode image formats not supported
 * by the Android system decoder (AVIF, JXL, HEIF, etc.).
 */
class ImageDecoder(private val resources: ImageSource, private val options: Options) : Decoder {

    /**
     * Wraps a raw [ImageDecoder.DecodeResult] as a Coil [Image] for callers that want
     * direct access to the RGBA [java.nio.ByteBuffer] (e.g. the new-decoder path).
     */
    class DecodeResultImage(val res: ImageDecoder.DecodeResult) : Image {
        override val size: Long get() = res.image.capacity().toLong()
        override val width: Int get() = res.width
        override val height: Int get() = res.height
        override val shareable: Boolean get() = true
        override fun draw(canvas: Canvas) {}
    }

    override suspend fun decode(): DecodeResult {
        val res = resources.source().use { source ->
            val decoded = try {
                ImageDecoder.new(source.peek().inputStream())
                    .takeIf { it.pages > 0 }
                    ?.decode()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "ImageDecoder failed: ${e.message}" }
                null
            }

            // The native decoder can't open some images Android can, and the reader sends every
            // long-strip page through it: fall back to the system decoder rather than lose the page.
            if (decoded == null && !options.newDecoder) return decodeWithSystem(source)
            decoded
        }

        check(res != null) { "Failed to initialize decoder" }

        val srcWidth = res.width
        val srcHeight = res.height

        // newDecoder path: caller wants the raw DecodeResult (e.g. for custom rendering).
        // Hand it back as-is; sampling is the caller's responsibility.
        if (options.newDecoder) {
            return DecodeResult(
                image = DecodeResultImage(res),
                isSampled = false,
            )
        }

        // Normal path: produce a Bitmap scaled to the requested output size.
        val dstWidth = options.size.widthPx(options.scale) { srcWidth }
        val dstHeight = options.size.heightPx(options.scale) { srcHeight }
        val sampleSize = DecodeUtils.calculateInSampleSize(
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            dstWidth = dstWidth,
            dstHeight = dstHeight,
            scale = options.scale,
        )

        // Copy RGBA pixels from the native buffer into a full-resolution bitmap.
        // We must do this while `res` (and its native memory) is still alive.
        val fullBitmap = createBitmap(srcWidth, srcHeight)
        res.image.rewind()
        fullBitmap.copyPixelsFromBuffer(res.image)

        // Downsample if needed. sampleSize is a power-of-two factor; the target
        // dimensions are src / sampleSize, matching BitmapFactory inSampleSize behaviour.
        val bitmap = if (sampleSize > 1) {
            val scaledWidth = (srcWidth / sampleSize).coerceAtLeast(1)
            val scaledHeight = (srcHeight / sampleSize).coerceAtLeast(1)
            val scaled = fullBitmap.scale(scaledWidth, scaledHeight)
            fullBitmap.recycle()
            scaled
        } else {
            fullBitmap
        }

        return DecodeResult(
            image = bitmap.asImage(),
            isSampled = sampleSize > 1,
        )
    }

    private fun decodeWithSystem(source: BufferedSource): DecodeResult {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(source.peek().inputStream(), null, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Failed to initialize decoder" }

        val sampleSize = DecodeUtils.calculateInSampleSize(
            srcWidth = bounds.outWidth,
            srcHeight = bounds.outHeight,
            dstWidth = options.size.widthPx(options.scale) { bounds.outWidth },
            dstHeight = options.size.heightPx(options.scale) { bounds.outHeight },
            scale = options.scale,
        )
        val bitmap = BitmapFactory.decodeStream(
            source.inputStream(),
            null,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
        checkNotNull(bitmap) { "Failed to initialize decoder" }

        return DecodeResult(
            image = bitmap.asImage(),
            isSampled = sampleSize > 1,
        )
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            return if (options.newDecoder || options.customDecoder || isApplicable(result.source.source())) {
                ImageDecoder(result.source, options)
            } else {
                null
            }
        }

        private fun isApplicable(source: BufferedSource): Boolean {
            val type = source.peek().inputStream().use {
                ImageUtil.findImageType(it)
            }
            return when (type) {
                ImageUtil.ImageType.AVIF,
                ImageUtil.ImageType.JXL,
                ImageUtil.ImageType.HEIF,
                ImageUtil.ImageType.JP2,
                -> true

                else -> false
            }
        }

        override fun equals(other: Any?) = other is Factory

        override fun hashCode() = javaClass.hashCode()
    }
}
