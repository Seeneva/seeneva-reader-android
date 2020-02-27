package com.almadevelop.comixreader.logic.glide.decoder

import android.graphics.Bitmap
import android.os.Build
import com.almadevelop.comixreader.data.entity.ComicImage
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.bumptech.glide.load.resource.bitmap.Downsampler
import java.lang.reflect.Method

/**
 * Decodes [ComicImage] into [Bitmap]
 */
internal class ComicImageBitmapDecoder(private val bitmapPool: BitmapPool) :
    ResourceDecoder<ComicImage, Bitmap> {
    override fun handles(source: ComicImage, options: Options) = true

    override fun decode(
        source: ComicImage,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val (colors, res_w, res_h) = source

        val bitmap = Bitmap.createBitmap(colors, res_w, res_h, bitmapConfig(res_w, res_h, options))

        return BitmapResource.obtain(bitmap, bitmapPool)
    }

    companion object {
        private const val MIN_HARDWARE_DIMENSION = 128

        private var hardwareConfigState: HardwareConfigState? = null

        @JvmStatic
        private fun bitmapConfig(width: Int, height: Int, options: Options): Bitmap.Config {
            val isHardwareConfigAllowed = {
                runCatching {
                    (options.get<Boolean>(Downsampler.ALLOW_HARDWARE_CONFIG) ?: false)
                            && width >= MIN_HARDWARE_DIMENSION
                            && height >= MIN_HARDWARE_DIMENSION
                            && hardwareConfigState().isFdSizeBelowHardwareLimit()
                }.getOrDefault(false)
            }

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isHardwareConfigAllowed()) {
                Bitmap.Config.HARDWARE
            } else {
                when (options.get<DecodeFormat>(Downsampler.DECODE_FORMAT)) {
                    DecodeFormat.PREFER_RGB_565 -> Bitmap.Config.RGB_565
                    else -> Bitmap.Config.ARGB_8888
                }
            }
        }

        @JvmStatic
        @Synchronized
        private fun hardwareConfigState(): HardwareConfigState {
            return if (hardwareConfigState != null) {
                hardwareConfigState!!
            } else {
                val clazz =
                    Class.forName("com.bumptech.glide.load.resource.bitmap.HardwareConfigState")

                val instance = clazz.getDeclaredMethod("getInstance").run {
                    isAccessible = true
                    requireNotNull(invoke(null))
                }

                val isFdSizeBelowHardwareLimit =
                    clazz.getDeclaredMethod("isFdSizeBelowHardwareLimit")
                        .apply { isAccessible = true }

                HardwareConfigState(instance, isFdSizeBelowHardwareLimit).also {
                    hardwareConfigState = it
                }
            }
        }

        private data class HardwareConfigState(
            private val instance: Any,
            private val isFdSizeBelowHardwareLimit: Method
        ) {
            fun isFdSizeBelowHardwareLimit(): Boolean {
                return isFdSizeBelowHardwareLimit.invoke(instance) as Boolean
            }
        }
    }
}