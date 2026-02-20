package com.samsung.hybridlauncher.workspace

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Core Workspace View for the Hybrid Launcher.
 * Utilizes a high-density micro-grid to simulate freeform icon/widget placement
 * while keeping bounding box mathematics strictly integer-based for 60fps drag-and-drop.
 */
class FreeformWorkspaceLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    companion object {
        // High-density sub-grid dimensions.
        // A standard app icon might take up 4x4 cells, while a widget takes 12x8.
        const val MICRO_GRID_COLUMNS = 24
        const val MICRO_GRID_ROWS = 48
    }

    private var cellWidth: Float = 0f
    private var cellHeight: Float = 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        // Calculate the exact pixel size of a single micro-grid cell
        cellWidth = widthSize.toFloat() / MICRO_GRID_COLUMNS
        cellHeight = heightSize.toFloat() / MICRO_GRID_ROWS

        var maxWidth = 0
        var maxHeight = 0

        // Measure all children based on their micro-grid span
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                val lp = child.layoutParams as LayoutParams

                val childWidthPixels = (lp.columnSpan * cellWidth).roundToInt()
                val childHeightPixels = (lp.rowSpan * cellHeight).roundToInt()

                val childWidthSpec = MeasureSpec.makeMeasureSpec(childWidthPixels, MeasureSpec.EXACTLY)
                val childHeightSpec = MeasureSpec.makeMeasureSpec(childHeightPixels, MeasureSpec.EXACTLY)

                child.measure(childWidthSpec, childHeightSpec)

                maxWidth = max(maxWidth, child.measuredWidth)
                maxHeight = max(maxHeight, child.measuredHeight)
            }
        }

        setMeasuredDimension(
            resolveSize(maxWidth, widthMeasureSpec),
            resolveSize(maxHeight, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                val lp = child.layoutParams as LayoutParams

                // Calculate absolute physical positions based on micro-grid coordinates
                val childLeft = (lp.column * cellWidth).roundToInt()
                val childTop = (lp.row * cellHeight).roundToInt()
                val childRight = childLeft + child.measuredWidth
                val childBottom = childTop + child.measuredHeight

                child.layout(childLeft, childTop, childRight, childBottom)
            }
        }
    }

    /**
     * Custom LayoutParams strictly enforcing micro-grid placement coordinates.
     */
    class LayoutParams(
        var column: Int,
        var row: Int,
        var columnSpan: Int,
        var rowSpan: Int
    ) : ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT) {

        // Constructor for XML inflation if needed
        constructor(context: Context, attrs: AttributeSet) : this(0, 0, 4, 4) {
            // In a full implementation, you'd extract custom styleables here
        }

        constructor(source: ViewGroup.LayoutParams) : this(
            0, 0, 4, 4 // Default to a standard 4x4 icon size if unsupported params are passed
        )
    }

    override fun generateLayoutParams(attrs: AttributeSet): ViewGroup.LayoutParams {
        return LayoutParams(context, attrs)
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams {
        return LayoutParams(0, 0, 4, 4) // Default 4x4 micro-grid footprint
    }

    override fun generateLayoutParams(p: ViewGroup.LayoutParams): ViewGroup.LayoutParams {
        return if (p is LayoutParams) {
            LayoutParams(p.column, p.row, p.columnSpan, p.rowSpan)
        } else {
            generateDefaultLayoutParams()
        }
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams): Boolean {
        return p is LayoutParams
    }

    /**
     * Translates raw pixel coordinates from a touch event into the nearest micro-grid cell.
     * Used by the SpringPhysicsEngine for snap-to-grid dropping.
     */
    fun getCellFromPixels(x: Float, y: Float): Pair<Int, Int> {
        val col = (x / cellWidth).toInt().coerceIn(0, MICRO_GRID_COLUMNS - 1)
        val row = (y / cellHeight).toInt().coerceIn(0, MICRO_GRID_ROWS - 1)
        return Pair(col, row)
    }

    /**
     * Checks if a proposed drop zone is occupied.
     * Because we use an integer grid, this is incredibly fast and generates zero garbage.
     */
    fun isSpaceVacant(targetCol: Int, targetRow: Int, spanX: Int, spanY: Int, ignoreView: View? = null): Boolean {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child == ignoreView || child.visibility == GONE) continue

            val lp = child.layoutParams as LayoutParams

            // Fast AABB (Axis-Aligned Bounding Box) collision detection
            val overlapX = targetCol < lp.column + lp.columnSpan && targetCol + spanX > lp.column
            val overlapY = targetRow < lp.row + lp.rowSpan && targetRow + spanY > lp.row

            if (overlapX && overlapY) return false
        }
        return true
    }
}