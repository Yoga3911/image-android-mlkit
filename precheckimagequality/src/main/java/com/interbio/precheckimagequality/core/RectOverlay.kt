package com.interbio.precheckimagequality.core

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min


class RectOverlay(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var redPaint: Paint
    private var blackPaint: Paint
    private var transparentPaint: Paint
    private var greenPaint: Paint

    public var isGoodFrame: Boolean = false

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null);

        redPaint = Paint().apply { setARGB(255, 255, 0, 0) }
        blackPaint = Paint().apply { setARGB(128, 0, 0, 0) }
        greenPaint = Paint().apply { setARGB(255, 0, 255, 0) }

        transparentPaint = Paint()
        transparentPaint.setARGB(0, 0, 0, 0)
        transparentPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }


    override fun onDraw(canvas: Canvas) {
        val radius = min(width, height).toFloat() / 2f - 20
        super.onDraw(canvas)
        canvas.drawRect(0.0F,0.0F, width.toFloat(), height.toFloat(), blackPaint)
        // Draw a red circle in the center
        if(isGoodFrame) {
            canvas.drawCircle((width / 2).toFloat(), (height / 2).toFloat(), radius, greenPaint)
        } else {
            canvas.drawCircle((width / 2).toFloat(), (height / 2).toFloat(), radius, redPaint)
        }
        canvas.drawCircle((width / 2).toFloat(), (height / 2).toFloat(), radius - 20, transparentPaint)
    }

    fun setGoodFrame() {
        if(!isGoodFrame) {
            isGoodFrame = true
            invalidate()
        }
    }

    fun setBadFrame() {
        if(isGoodFrame) {
            isGoodFrame = false
            invalidate()
        }
    }
}