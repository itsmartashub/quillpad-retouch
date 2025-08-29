package org.qosp.notes.ui.utils

import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.RecyclerView
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView

fun Toolbar.setOverflowLongPressAction(onLongPress: () -> Unit) {
    post {
        val overflowDesc = resources.getString(
            androidx.appcompat.R.string.abc_action_menu_overflow_description
        )
        val overflowButtons = ArrayList<View>()
        findViewsWithText(
            overflowButtons,
            overflowDesc,
            View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION
        )
        overflowButtons.firstOrNull()?.setOnLongClickListener {
            onLongPress()
            true
        }
    }
}

// Smoothly scroll a ScrollView to the bottom.
fun ScrollView.scrollToBottomSmooth() {
    doOnLayout {
        smoothScrollTo(0, (getChildAt(0)?.bottom ?: 0) + paddingBottom)
    }
}

// Smoothly scroll a NestedScrollView to the bottom.
fun NestedScrollView.scrollToBottomSmooth() {
    doOnLayout {
        smoothScrollTo(0, (getChildAt(0)?.bottom ?: 0) + paddingBottom)
    }
}

// Smoothly scroll a RecyclerView to the last item.
fun RecyclerView.scrollToLastSmooth() {
    post {
        val last = adapter?.itemCount?.minus(1) ?: -1
        if (last >= 0) smoothScrollToPosition(last)
    }
}
