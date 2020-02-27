package com.almadevelop.comixreader.logic.glide.transformation

import android.graphics.*
import android.os.Build
import com.almadevelop.comixreader.logic.CornerRadius
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.nio.ByteBuffer
import java.security.MessageDigest

internal class DifferentRoundedCorners(
    private val cornerRadius: CornerRadius
) : BitmapTransformation() {
    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val (topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius) = cornerRadius

        val realToTransform = alphaBitmap(pool, toTransform).also { it.setHasAlpha(true) }

        val result = pool.get(toTransform.width, toTransform.height, alphaConfig(toTransform))

        val path = Path().apply {
            val top = .0f
            val left = .0f
            val right = result.width.toFloat()
            val bottom = result.height.toFloat()

            if (cornerRadius.equalCorners) {
                addRoundRect(
                    RectF(left, top, right, bottom),
                    topLeftRadius,
                    topLeftRadius,
                    Path.Direction.CW
                )
            } else {
                //top-left
                moveTo(left, top + topLeftRadius)
                quadTo(0.0f, 0.0f, left + topLeftRadius, top)
                //top-right
                rLineTo((right - topRightRadius) - (left + topLeftRadius), 0.0f)
                rQuadTo(topRightRadius, 0.0f, topRightRadius, topRightRadius)
                //bottom-right
                rLineTo(0.0f, (bottom - bottomRightRadius) - (top + topRightRadius))
                rQuadTo(0.0f, bottomRightRadius, -bottomRightRadius, bottomRightRadius)
                //bottom-left
                rLineTo((left + bottomLeftRadius) - (right - bottomRightRadius), 0.0f)
                rQuadTo(-bottomLeftRadius, 0.0f, -bottomLeftRadius, -bottomLeftRadius)

                close()
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = BitmapShader(toTransform, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        Canvas(result).also {
            it.drawPath(path, paint)
            it.setBitmap(null)
        }

        if (realToTransform !== toTransform) {
            pool.put(realToTransform)
        }

        return result
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)

        //4 floats by 4 bytes each = 16 bytes
        val data = ByteBuffer.allocate(16)
            .putFloat(cornerRadius.topLeftRadius)
            .putFloat(cornerRadius.topRightRadius)
            .putFloat(cornerRadius.bottomRightRadius)
            .putFloat(cornerRadius.bottomLeftRadius)
            .array()

        messageDigest.update(data)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DifferentRoundedCorners

        if (cornerRadius != other.cornerRadius) return false

        return true
    }

    override fun hashCode(): Int {
        return cornerRadius.hashCode()
    }


    companion object {
        private const val ID =
            "com.almadevelop.comixreader.logic.glide.transformation.DifferentRoundedCorners"

        private val ID_BYTES by lazy { ID.toByteArray(Key.CHARSET) }

        private fun alphaConfig(bitmap: Bitmap): Bitmap.Config {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.RGBA_F16) {
                bitmap.config
            } else {
                Bitmap.Config.ARGB_8888
            }
        }

        private fun alphaBitmap(pool: BitmapPool, bitmap: Bitmap): Bitmap {
            val alphaBitmapConfig = alphaConfig(bitmap)

            if (bitmap.config == alphaBitmapConfig) {
                return bitmap
            }

            val argbBitmap = pool.get(bitmap.width, bitmap.height, alphaBitmapConfig)

            Canvas(argbBitmap).drawBitmap(bitmap, .0f, .0f, null)

            return argbBitmap
        }
    }
}