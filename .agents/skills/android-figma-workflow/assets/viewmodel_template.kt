package your.package.name

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * 当前 Figma Frame 专用 ViewModel：负责组织 UI 状态和业务调度，不持有 View 或 Context。
 * 如果目标项目已有 ViewModel 基类，优先替换为项目基类；没有时使用 AndroidX ViewModel。
 */
class XxxViewModel : ViewModel() {

    private val _uiState = MutableLiveData(XxxUiState())
    val uiState: LiveData<XxxUiState> = _uiState

    fun loadData() {
        // TODO 加载真实数据或临时静态数据
    }

    private fun updateState(reducer: (XxxUiState) -> XxxUiState) {
        _uiState.value = reducer(_uiState.value ?: XxxUiState())
    }
}

data class XxxUiState(
    val loading: Boolean = false,
    val items: List<XxxItem> = emptyList(),
    val errorMessage: String = ""
)

/**
 * 当前页面专用 UI item，只承载展示字段。
 * 文案、颜色、尺寸优先使用资源 id，不要把业务逻辑写进 Adapter。
 */
data class XxxItem(
    val id: Long,
    val title: String,
    val imageUrl: String = "",
    val fallbackImageRef: String = ""
)
