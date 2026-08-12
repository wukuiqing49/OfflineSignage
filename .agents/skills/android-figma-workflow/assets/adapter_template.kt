package your.package.name

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * 当前 Figma Frame 专用列表 Adapter：只负责视图绑定，不写业务逻辑。
 *
 * Figma 中重复 item 必须优先抽成 RecyclerView。
 * 如果目标项目已有 Adapter 基类，优先替换为项目封装；没有时使用标准 ListAdapter / RecyclerView.Adapter。
 * 缺失 Figma 图标如临时使用 Material Design 图标，必须在资源清单标注“MD 图标替代”。
 */
class XxxAdapter(
    private val onItemClick: (XxxItem) -> Unit
) : ListAdapter<XxxItem, XxxAdapter.XxxViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): XxxViewHolder {
        val binding = ItemXxxBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return XxxViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: XxxViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class XxxViewHolder(
        private val binding: ItemXxxBinding,
        private val onItemClick: (XxxItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: XxxItem) {
            binding.root.setOnClickListener { onItemClick(item) }
            binding.tvTitle.text = item.title
            // TODO 绑定图片、状态和辅助文案
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<XxxItem>() {
            override fun areItemsTheSame(oldItem: XxxItem, newItem: XxxItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: XxxItem, newItem: XxxItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
