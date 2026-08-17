package com.wkq.localsignage.feature.app.player

import android.graphics.Color
import android.graphics.Matrix
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.ImageLoader
import coil.load
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.wkq.localsignage.feature.app.model.SignageResource
import com.wkq.localsignage.feature.app.model.SignageScene
import com.wkq.localsignage.feature.app.runtime.SignageRuntime

internal data class ImageSlide(
    val scene: SignageScene,
    val resource: SignageResource
) {
    val data: Any
        get() = if (resource.isLocalFile) SignageRuntime.fileFor(resource) else resource.sourceUri.orEmpty()
}

internal class ImageSlideshowRenderer(private val pager: ViewPager2) {
    private val imageLoader = ImageLoader.Builder(pager.context.applicationContext)
        .diskCachePolicy(CachePolicy.ENABLED)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .build()
    private val adapter = SlideAdapter(imageLoader)
    private var slideKeys: List<SlideKey> = emptyList()
    private var renderGeneration = 0L
    private var released = false

    init {
        pager.adapter = adapter
        pager.isUserInputEnabled = false
        pager.offscreenPageLimit = 1
    }

    fun show(slides: List<ImageSlide>, index: Int) {
        val nextSlides = slides.toList()
        val keys = nextSlides.map {
            SlideKey(
                sceneId = it.scene.id,
                fitMode = it.scene.fitMode,
                cropGravity = it.scene.cropGravity,
                backgroundColor = it.scene.backgroundColor,
                resourceId = it.resource.id,
                sourceUri = it.resource.sourceUri
            )
        }
        val generation = ++renderGeneration
        pager.post {
            if (released || generation != renderGeneration) return@post
            if (keys != slideKeys) {
                slideKeys = keys
                adapter.submit(nextSlides)
            }
            if (nextSlides.isNotEmpty()) {
                val target = index.coerceIn(0, nextSlides.lastIndex)
                // 广告屏切图不能露出 ViewPager2 横向滚动过程中的空白区域。
                pager.setCurrentItem(target, false)
                if (nextSlides.size > 1) {
                    prefetch(nextSlides[(target + 1) % nextSlides.size])
                }
            }
        }
    }

    fun release() {
        released = true
        renderGeneration += 1
        slideKeys = emptyList()
        pager.adapter = null
        imageLoader.shutdown()
    }

    private fun prefetch(slide: ImageSlide) {
        val targetWidth = pager.width
        val targetHeight = pager.height
        if (targetWidth <= 0 || targetHeight <= 0) return
        imageLoader.enqueue(
            ImageRequest.Builder(pager.context)
                .data(slide.data)
                .size(targetWidth, targetHeight)
                .precision(Precision.INEXACT)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
        )
    }

    private class SlideAdapter(private val imageLoader: ImageLoader) :
        RecyclerView.Adapter<SlideViewHolder>() {
        private var slides: List<ImageSlide> = emptyList()

        fun submit(value: List<ImageSlide>) {
            slides = value
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = slides.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
            val image = ImageView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            return SlideViewHolder(image, imageLoader)
        }

        override fun onBindViewHolder(holder: SlideViewHolder, position: Int) = holder.bind(slides[position])

        override fun onViewRecycled(holder: SlideViewHolder) = holder.recycle()
    }

    private class SlideViewHolder(
        private val image: ImageView,
        private val imageLoader: ImageLoader
    ) : RecyclerView.ViewHolder(image) {
        fun bind(slide: ImageSlide) {
            image.setBackgroundColor(parseColor(slide.scene.backgroundColor, Color.BLACK))
            image.scaleType = when (slide.scene.fitMode.uppercase()) {
                "FILL", "CROP" -> ImageView.ScaleType.MATRIX
                "STRETCH" -> ImageView.ScaleType.FIT_XY
                "CENTER" -> ImageView.ScaleType.CENTER
                else -> ImageView.ScaleType.FIT_CENTER
            }
            image.load(slide.data, imageLoader) {
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
                listener(onSuccess = { _, _ -> image.post { applyCropMatrix(slide.scene.cropGravity) } })
            }
        }

        private fun applyCropMatrix(gravity: String) {
            if (image.scaleType != ImageView.ScaleType.MATRIX) return
            val drawable = image.drawable ?: return
            val sourceWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: return
            val sourceHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: return
            val targetWidth = image.width.takeIf { it > 0 } ?: return
            val targetHeight = image.height.takeIf { it > 0 } ?: return
            val scale = maxOf(targetWidth.toFloat() / sourceWidth, targetHeight.toFloat() / sourceHeight)
            val overflowX = targetWidth - sourceWidth * scale
            val overflowY = targetHeight - sourceHeight * scale
            val offsetX = when (gravity.uppercase()) {
                "LEFT" -> 0f
                "RIGHT" -> overflowX
                else -> overflowX / 2f
            }
            val offsetY = when (gravity.uppercase()) {
                "TOP" -> 0f
                "BOTTOM" -> overflowY
                else -> overflowY / 2f
            }
            image.imageMatrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(offsetX, offsetY)
            }
        }

        fun recycle() {
            image.setImageDrawable(null)
        }

        private fun parseColor(value: String?, fallback: Int): Int =
            value?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: fallback
    }

    private data class SlideKey(
        val sceneId: String,
        val fitMode: String,
        val cropGravity: String,
        val backgroundColor: String?,
        val resourceId: String,
        val sourceUri: String?
    )
}
