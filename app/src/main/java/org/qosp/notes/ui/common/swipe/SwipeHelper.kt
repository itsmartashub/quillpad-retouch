package org.qosp.notes.ui.common.swipe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.qosp.notes.R
import org.qosp.notes.data.model.Note

class SwipeHelper(
    private val context: Context,
    private val onSwipeAction: (Int, SwipeAction) -> Unit,
    private val getNoteAtPosition: (Int) -> Note? = { null }
) {
    // Optimized paint objects with proper initialization
    private val backgroundPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    // Pre-cached drawables - loaded once for performance
    private val iconMap = mapOf(
        SwipeAction.PIN to ContextCompat.getDrawable(context, R.drawable.ic_pin)!!,
        SwipeAction.UNPIN to ContextCompat.getDrawable(context, R.drawable.ic_unpin)!!,
        SwipeAction.DELETE to ContextCompat.getDrawable(context, R.drawable.ic_bin)!!,
        SwipeAction.RESTORE to ContextCompat.getDrawable(context, R.drawable.ic_restore)!!
    )

    // Pre-calculated dimensions for performance
    private val cornerRadius = context.resources.getDimensionPixelSize(R.dimen.card_note_corner_radius).toFloat()
    private val iconSize = context.resources.getDimensionPixelSize(R.dimen.swipe_icon_size)
    private val iconMargin = context.resources.getDimensionPixelSize(R.dimen.swipe_icon_margin)
    private val iconTextGap = context.resources.getDimensionPixelSize(R.dimen.card_note_padding)
    private val halfIconSize = iconSize / 2
    private val swipeThreshold = 0.2f
    private val swipeEscapeVelocity = 500f // dp per second
    private val swipeDistanceThreshold = 0.3f // 30% of view width minimum

    // Cached colors with theme support
    private val colorMap by lazy {
        mapOf(
            SwipeAction.PIN to getThemeColor(R.attr.colorSecondaryContainer, R.color.pin_color),
            SwipeAction.UNPIN to getThemeColor(R.attr.colorSecondaryContainer, R.color.pin_color),
            SwipeAction.DELETE to ContextCompat.getColor(context, R.color.delete_color),
            SwipeAction.RESTORE to ContextCompat.getColor(context, R.color.restore_color)
        )
    }

    // Text colors that complement the background colors
    private val textColorMap by lazy {
        mapOf(
            SwipeAction.PIN to Color.WHITE,
            SwipeAction.UNPIN to Color.WHITE,
            SwipeAction.DELETE to Color.WHITE,
            SwipeAction.RESTORE to Color.WHITE
        )
    }

    // Pre-loaded strings
    private val actionTexts = mapOf(
        SwipeAction.PIN to context.getString(R.string.action_pin),
        SwipeAction.UNPIN to context.getString(R.string.action_unpin),
        SwipeAction.DELETE to context.getString(R.string.action_delete),
        SwipeAction.RESTORE to context.getString(R.string.action_restore)
    )

    // Reusable objects to avoid GC pressure
    private val backgroundRect = RectF()

    init {
        textPaint.textSize = context.resources.getDimensionPixelSize(R.dimen.swipe_text_size).toFloat()
    }

    private fun getThemeColor(attrRes: Int, fallbackColorRes: Int): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(attrRes, typedValue, true)) {
            typedValue.data
        } else {
            ContextCompat.getColor(context, fallbackColorRes)
        }
    }

    fun createItemTouchHelper(): ItemTouchHelper {
        return ItemTouchHelper(SwipeCallback())
    }

    private inner class SwipeCallback : ItemTouchHelper.SimpleCallback(
        0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean = false

        override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
            return swipeDistanceThreshold
        }

        override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
            return swipeEscapeVelocity
        }

        override fun getSwipeVelocityThreshold(defaultValue: Float): Float {
            return swipeEscapeVelocity * 0.5f
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return

            val note = getNoteAtPosition(position)
            val action = determineSwipeAction(direction, note)
            onSwipeAction(position, action)
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
                handleSwipeDraw(c, viewHolder, dX)
            }
            // This MUST be called to handle the actual item sliding animation
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            with(viewHolder.itemView) {
                alpha = 1f
                translationX = 0f
            }
        }
    }

    private fun determineSwipeAction(direction: Int, note: Note?): SwipeAction {
        return when (direction) {
            ItemTouchHelper.RIGHT -> if (note?.isPinned == true) SwipeAction.UNPIN else SwipeAction.PIN
            ItemTouchHelper.LEFT -> if (note?.isDeleted == true) SwipeAction.RESTORE else SwipeAction.DELETE
            else -> SwipeAction.PIN // Fallback
        }
    }

    private fun handleSwipeDraw(canvas: Canvas, viewHolder: RecyclerView.ViewHolder, dX: Float) {
        val itemView = viewHolder.itemView
        val position = viewHolder.bindingAdapterPosition

        if (position == RecyclerView.NO_POSITION || dX == 0f) return

        val note = getNoteAtPosition(position)
        val isRightSwipe = dX > 0
        val swipeProgress = kotlin.math.abs(dX) / itemView.width

        val action = determineSwipeAction(
            if (isRightSwipe) ItemTouchHelper.RIGHT else ItemTouchHelper.LEFT,
            note
        )

        // Draw background that reveals as the note moves
        drawRevealingBackground(canvas, itemView, action, dX, isRightSwipe)

        // Show content only after small threshold for better UX
        if (swipeProgress > 0.15f) {
            drawRevealingContent(canvas, itemView, action, dX, swipeProgress, isRightSwipe)
        }
    }

    private fun drawRevealingBackground(
        canvas: Canvas,
        itemView: android.view.View,
        action: SwipeAction,
        dX: Float,
        isRightSwipe: Boolean
    ) {
        backgroundPaint.color = colorMap[action] ?: Color.GRAY

        // Draw FULL background - the note slides OVER this
        backgroundRect.set(
            itemView.left.toFloat(),
            itemView.top.toFloat(),
            itemView.right.toFloat(),
            itemView.bottom.toFloat()
        )

        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, backgroundPaint)
    }

//    private fun drawRevealingContent(
//        canvas: Canvas,
//        itemView: android.view.View,
//        action: SwipeAction,
//        dX: Float,
//        swipeProgress: Float,
//        isRightSwipe: Boolean
//    ) {
//        val icon = iconMap[action] ?: return
//        val text = actionTexts[action] ?: ""
//        val textColor = textColorMap[action] ?: Color.WHITE
//
//        // Fade in content based on swipe distance
//        val contentAlpha = ((swipeProgress - 0.15f) / 0.15f).coerceIn(0f, 1f)
//        val alpha = (contentAlpha * 255).toInt()
//
//        // Calculate positions
//        val iconY = itemView.top + (itemView.height - iconSize) / 2
//        val textY = itemView.top + (itemView.height + textPaint.textSize) / 2 - textPaint.descent()
//
//        if (isRightSwipe) {
//            // Right swipe - position content in revealed area
//            val revealedWidth = kotlin.math.abs(dX)
//            if (revealedWidth > iconMargin + iconSize) {
//                val iconX = itemView.left + iconMargin
//                val textX = (iconX + iconSize + iconTextGap).toFloat()
//
//                // Only draw if there's enough space
//                if (textX < itemView.left + revealedWidth - 20) {
//                    drawIconAndText(canvas, icon, iconX, iconY, text, textX, textY, alpha, textColor, Paint.Align.LEFT)
//                }
//            }
//        } else {
//            // Left swipe - position content in revealed area
//            val revealedWidth = kotlin.math.abs(dX)
//            if (revealedWidth > iconMargin + iconSize) {
//                val iconX = itemView.right - iconMargin - iconSize
//                val textX = (iconX - iconTextGap).toFloat()
//
//                // Only draw if there's enough space
//                if (textX > itemView.right + dX + 20) {
//                    drawIconAndText(canvas, icon, iconX, iconY, text, textX, textY, alpha, textColor, Paint.Align.RIGHT)
//                }
//            }
//        }
//    }

    private fun drawRevealingContent(
        canvas: Canvas,
        itemView: android.view.View,
        action: SwipeAction,
        dX: Float,
        swipeProgress: Float,
        isRightSwipe: Boolean
    ) {
        val icon = iconMap[action] ?: return
        val text = actionTexts[action] ?: ""
        val textColor = textColorMap[action] ?: Color.WHITE

        // SLOWER: Opacity increases gradually throughout the entire swipe (0% to 100%)
        val contentAlpha = swipeProgress.coerceIn(0f, 1f)
        val alpha = (contentAlpha * 255).toInt()

        // Calculate positions
        val iconY = itemView.top + (itemView.height - iconSize) / 2
        val textY = itemView.top + (itemView.height + textPaint.textSize) / 2 - textPaint.descent()

        if (isRightSwipe) {
            val iconX = itemView.left + iconMargin
            val textX = (iconX + iconSize + iconTextGap).toFloat()

            drawIconAndText(canvas, icon, iconX, iconY, text, textX, textY, alpha, textColor, Paint.Align.LEFT)
        } else {
            val iconX = itemView.right - iconMargin - iconSize
            val textX = (iconX - iconTextGap).toFloat()

            drawIconAndText(canvas, icon, iconX, iconY, text, textX, textY, alpha, textColor, Paint.Align.RIGHT)
        }
    }

    private fun drawIconAndText(
        canvas: Canvas,
        icon: Drawable,
        iconX: Int,
        iconY: Int,
        text: String,
        textX: Float,
        textY: Float,
        alpha: Int,
        textColor: Int,
        textAlign: Paint.Align
    ) {
        // Draw icon with alpha
        icon.alpha = alpha
        icon.setBounds(iconX, iconY, iconX + iconSize, iconY + iconSize)
        icon.draw(canvas)

        // Draw text with dynamic color and alpha
        val previousAlign = textPaint.textAlign
        textPaint.color = textColor
        textPaint.alpha = alpha
        textPaint.textAlign = textAlign

        canvas.drawText(text, textX, textY, textPaint)

        // Reset paint properties
        textPaint.alpha = 255
        textPaint.textAlign = previousAlign
    }
}

// Consolidated data class and enum
data class SwipeContent(
    val icon: Drawable,
    val text: String,
    val backgroundColor: Int,
    val action: SwipeAction
)

enum class SwipeAction {
    PIN, UNPIN, DELETE, RESTORE
}
