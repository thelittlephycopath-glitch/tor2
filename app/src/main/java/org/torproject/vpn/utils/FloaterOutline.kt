package org.torproject.vpn.utils

import android.graphics.Outline
import android.graphics.Rect
import android.view.View
import android.view.ViewOutlineProvider

class FloaterOutline(
    var scaleX: Float,
    var scaleY: Float,
    var yShift: Int,
    val cornerRad: Float
) : ViewOutlineProvider() {
    override fun getOutline(view: View?, p1: Outline?) {
        val rect = Rect()
        view?.background?.copyBounds(rect)
        rect.scale(scaleX, scaleY)
        rect.offset(0, yShift)

        p1?.setRoundRect(rect, cornerRad)
    }
}
