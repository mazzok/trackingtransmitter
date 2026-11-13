package com.trackingtransmitter.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

private const val WOOD_LIGHT = 0xFF8D5524.toInt()
private const val WOOD_DARK = 0xFF6D3F1A.toInt()
private const val WOOD_INSIDE = 0xFF4A2A10.toInt()
private const val GOLD = 0xFFFFC107.toInt()
private const val GOLD_SHINE = 0xFFFFE082.toInt()
private const val EDGE = 0xFF3E2210.toInt()

/**
 * A small treasure chest for the map, [sizePx] wide: closed (domed lid, gold bands and lock) for a hidden treasure,
 * [open] (lid folded back, full of gold) for a found one.
 */
internal fun chestBitmap(sizePx: Int, open: Boolean = false): Bitmap {
    val w = sizePx.toFloat()
    val h = w * 0.85f
    val bmp = Bitmap.createBitmap(sizePx, h.toInt() + 1, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = w * 0.06f
        color = EDGE
    }
    val inset = outline.strokeWidth / 2
    val lidBottom = h * 0.45f
    val box = RectF(inset, lidBottom, w - inset, h - inset)
    val r = w * 0.12f
    val band = w * 0.1f

    fun shape(rect: RectF, radius: Float, color: Int) {
        fill.color = color
        c.drawRoundRect(rect, radius, radius, fill)
        c.drawRoundRect(rect, radius, radius, outline)
    }

    if (open) {
        // the lid stands up behind the box, its inside facing us
        shape(RectF(inset, inset, w - inset, lidBottom + r), r, WOOD_INSIDE)
        // a heap of gold coins sticking out of the box
        val coin = w * 0.13f
        val coinY = lidBottom - coin * 0.2f
        for (x in listOf(0.25f, 0.5f, 0.75f, 0.37f, 0.63f)) {
            val y = if (x == 0.37f || x == 0.63f) coinY - coin * 0.8f else coinY
            fill.color = GOLD
            c.drawCircle(w * x, y, coin, fill)
            fill.color = GOLD_SHINE
            c.drawCircle(w * x - coin * 0.3f, y - coin * 0.3f, coin * 0.35f, fill)
        }
        shape(box, r, WOOD_DARK)
        fill.color = GOLD
        c.drawRect(w * 0.2f, lidBottom + inset, w * 0.2f + band, h - inset * 2, fill)
        c.drawRect(w * 0.8f - band, lidBottom + inset, w * 0.8f, h - inset * 2, fill)
    } else {
        shape(RectF(inset, inset, w - inset, lidBottom + h * 0.2f), r * 2, WOOD_LIGHT)
        shape(box, r, WOOD_DARK)
        fill.color = GOLD
        c.drawRect(w * 0.2f, inset * 2, w * 0.2f + band, h - inset * 2, fill)
        c.drawRect(w * 0.8f - band, inset * 2, w * 0.8f, h - inset * 2, fill)
        c.drawRect(inset * 2, lidBottom - band / 2, w - inset * 2, lidBottom + band / 2, fill)
        val lock = RectF(w * 0.4f, lidBottom - w * 0.08f, w * 0.6f, lidBottom + w * 0.16f)
        shape(lock, w * 0.04f, GOLD)
    }
    return bmp
}
