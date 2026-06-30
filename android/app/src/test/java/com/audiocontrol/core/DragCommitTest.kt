package com.audiocontrol.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DragCommitTest {
    @Test fun emitsOnlyAfterThresholdMovement() {
        val gate = DragCommitGate()
        gate.reset(0.0)
        assertThat(gate.shouldEmit(0.5, 1.0)).isFalse()   // moved 0.5 < 1.0
        assertThat(gate.shouldEmit(1.0, 1.0)).isTrue()    // moved 1.0 >= threshold
        assertThat(gate.shouldEmit(1.4, 1.0)).isFalse()   // only 0.4 since last emit
        assertThat(gate.shouldEmit(2.0, 1.0)).isTrue()
    }
}
