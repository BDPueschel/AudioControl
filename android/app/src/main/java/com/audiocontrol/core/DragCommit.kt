package com.audiocontrol.core

import kotlin.math.abs

class DragCommitGate {
    private var lastEmitted = 0.0
    fun reset(value: Double) { lastEmitted = value }
    fun shouldEmit(value: Double, stepsThreshold: Double): Boolean {
        if (abs(value - lastEmitted) >= stepsThreshold) { lastEmitted = value; return true }
        return false
    }
}
