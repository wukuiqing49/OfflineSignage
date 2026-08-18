package com.wkq.base.util.pop

import android.content.Context
import android.view.View
import com.wkq.base.dialog.CommonDialog
import com.wkq.base.dialog.DialogKit
import com.wkq.base.dialog.DialogOptions
import com.wkq.base.dialog.DialogTone
import com.wkq.base.dialog.PopupHandle as BasePopupHandle

object SiteReportPopup {

    interface PopupHandle {
        fun dismiss()
        fun isShowing(): Boolean
    }

    fun showConfirm(
        context: Context,
        title: String,
        message: String,
        confirmText: String = context.getString(com.wkq.base.R.string.base_confirm),
        cancelText: String = context.getString(com.wkq.base.R.string.base_cancel),
        confirmDanger: Boolean = false,
        onCancel: (() -> Unit)? = null,
        onConfirm: () -> Unit
    ): PopupHandle {
        val tone = if (confirmDanger) DialogTone.ERROR else DialogTone.NORMAL
        return DialogKit.confirm(
            context = context,
            title = title,
            message = message,
            confirmText = confirmText,
            cancelText = cancelText,
            tone = tone,
            onConfirm = { onConfirm(); true },
            onCancel = { onCancel?.invoke() }
        ).asSiteReportHandle()
    }

    fun showBottomConfirm(
        context: Context,
        title: String,
        message: String,
        confirmText: String = context.getString(com.wkq.base.R.string.base_confirm),
        cancelText: String = context.getString(com.wkq.base.R.string.base_cancel),
        confirmDanger: Boolean = false,
        onConfirm: () -> Unit
    ): PopupHandle {
        return showConfirm(
            context = context,
            title = title,
            message = message,
            confirmText = confirmText,
            cancelText = cancelText,
            confirmDanger = confirmDanger,
            onConfirm = onConfirm
        )
    }

    fun showContent(
        context: Context,
        title: String,
        contentView: View,
        confirmText: String = context.getString(com.wkq.base.R.string.base_confirm),
        cancelText: String = context.getString(com.wkq.base.R.string.base_cancel),
        neutralText: String? = null,
        confirmDanger: Boolean = false,
        scrollable: Boolean = true,
        onConfirm: (() -> Boolean)? = null,
        onNeutral: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ): PopupHandle {
        return CommonDialog.showContent(
            context = context,
            title = title,
            contentView = contentView,
            confirmText = confirmText,
            cancelText = cancelText,
            neutralText = neutralText,
            confirmDanger = confirmDanger,
            scrollable = scrollable,
            onConfirm = onConfirm,
            onNeutral = onNeutral,
            onCancel = onCancel,
            onDismiss = onDismiss
        ).asSiteReportHandle()
    }

    fun showRawCenter(
        context: Context,
        contentView: View,
        onDismiss: (() -> Unit)? = null
    ): PopupHandle {
        val options = DialogOptions(cancelable = false, onDismiss = onDismiss)
        return DialogKit.rawView(
            context,
            contentView,
            options,
            false
        ).asSiteReportHandle()
    }

    fun showRawBottom(
        context: Context,
        contentView: View,
        onDismiss: (() -> Unit)? = null
    ): PopupHandle {
        val options = DialogOptions(cancelable = true, onDismiss = onDismiss)
        return DialogKit.rawView(
            context,
            contentView,
            options,
            true
        ).asSiteReportHandle()
    }

    private fun BasePopupHandle.asSiteReportHandle(): PopupHandle {
        val delegate = this
        return object : PopupHandle {
            override fun dismiss() {
                delegate.dismiss()
            }

            override fun isShowing(): Boolean = delegate.isShowing()
        }
    }
}
