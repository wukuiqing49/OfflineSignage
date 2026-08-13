package com.wkq.localsignage.feature.app.player

import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.ImageLoader
import coil.load
import coil.request.CachePolicy
import coil.request.ImageRequest
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
    private var slideIds: List<String> = emptyList()

    init {
        pager.adapter = adapter
        pager.isUserInputEnabled = false
        pager.offscreenPageLimit = 1
    }

    fun show(slides: List<ImageSlide>, index: Int) {
        val ids = slides.map { it.scene.id }
        if (ids != slideIds) {
            slideIds = ids
            adapter.submit(slides)
        }
        if (slides.isNotEmpty()) {
            val target = index.coerceIn(0, slides.lastIndex)
            // 广告屏切图不能露出 ViewPager2 横向滚动过程中的空白区域。
            pager.setCurrentItem(target, false)
            prefetch(slides[(target + 1) % slides.size])
        }
    }

    fun release() {
        slideIds = emptyList()
        adapter.submit(emptyList())
        pager.adapter = null
        imageLoader.shutdown()
    }

    private fun prefetch(slide: ImageSlide) {
        imageLoader.enqueue(
            ImageRequest.Builder(pager.context)
                .data(slide.data)
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
                "FILL", "STRETCH" -> ImageView.ScaleType.FIT_XY
                "CROP" -> ImageView.ScaleType.CENTER_CROP
                "CENTER" -> ImageView.ScaleType.CENTER
                else -> ImageView.ScaleType.FIT_CENTER
            }
            image.load(slide.data, imageLoader) {
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
            }
        }

        fun recycle() {
            image.setImageDrawable(null)
        }

        private fun parseColor(value: String?, fallback: Int): Int =
            value?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: fallback
    }
}
