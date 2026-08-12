package your.package.name

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels

/**
 * 页面说明：TODO 替换为当前 Figma Frame 对应页面用途。
 * Figma 节点：TODO 填写 node id。
 *
 * 当前页面必须是专用 Fragment，不能用通用 UiBlock / ScreenFactory 作为最终实现。
 * 如果目标项目已有 Fragment / ViewModel 基类，优先替换为项目基类；没有时使用 AndroidX 标准 Fragment。
 */
class XxxPageFragment : Fragment() {

    private var _binding: FragmentXxxBinding? = null
    private val binding: FragmentXxxBinding
        get() = requireNotNull(_binding)

    private val viewModel: XxxViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentXxxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initClick()
        initList()
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            renderState(state)
        }
        viewModel.loadData()
    }

    private fun initClick() {
        // TODO 绑定点击事件
    }

    private fun initList() {
        // TODO 初始化当前页面专用 RecyclerView / Adapter
    }

    private fun renderState(state: XxxUiState) {
        // TODO 渲染 UI 状态
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
