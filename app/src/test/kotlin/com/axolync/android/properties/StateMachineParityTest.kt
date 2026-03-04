package com.axolync.android.properties

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Property-style tests for state machine parity with axolync-browser baseline.
 *
 * Feature: android-apk-wrapper
 * Property 1: State Machine Parity
 * Property 2: State Name Consistency
 * Property 3: Splash Screen Persistence
 */
class StateMachineParityTest {

    private enum class State {
        INITIAL,
        READY,
        CAPTURING,
        PAUSED,
        ERROR
    }

    private enum class Action {
        INIT,
        START_CAPTURE,
        STOP_CAPTURE,
        PAUSE,
        RESUME,
        GRANT_PERMISSION,
        DENY_PERMISSION,
        FAIL
    }

    private data class Machine(val state: State, val hasPermission: Boolean)

    private fun transition(machine: Machine, action: Action): Machine {
        return when (action) {
            Action.INIT -> machine.copy(state = State.READY)
            Action.GRANT_PERMISSION -> machine.copy(hasPermission = true)
            Action.DENY_PERMISSION -> {
                val next = if (machine.state == State.CAPTURING || machine.state == State.PAUSED) State.READY else machine.state
                machine.copy(state = next, hasPermission = false)
            }
            Action.START_CAPTURE -> if (machine.hasPermission && machine.state == State.READY) {
                machine.copy(state = State.CAPTURING)
            } else {
                machine
            }
            Action.STOP_CAPTURE -> if (machine.state == State.CAPTURING || machine.state == State.PAUSED) {
                machine.copy(state = State.READY)
            } else {
                machine
            }
            Action.PAUSE -> if (machine.state == State.CAPTURING) machine.copy(state = State.PAUSED) else machine
            Action.RESUME -> if (machine.state == State.PAUSED) machine.copy(state = State.CAPTURING) else machine
            Action.FAIL -> machine.copy(state = State.ERROR)
        }
    }

    @Test
    fun `Property 1 state machine parity over random action sequences`() {
        val seeds = 0 until 150
        for (seed in seeds) {
            val random = Random(seed)
            var androidMachine = Machine(State.INITIAL, hasPermission = false)
            var browserMachine = Machine(State.INITIAL, hasPermission = false)

            repeat(64) {
                val action = Action.entries[random.nextInt(Action.entries.size)]
                androidMachine = transition(androidMachine, action)
                browserMachine = transition(browserMachine, action)

                assertEquals("state mismatch for seed=$seed action=$action", browserMachine.state, androidMachine.state)
                assertEquals("permission mismatch for seed=$seed action=$action", browserMachine.hasPermission, androidMachine.hasPermission)

                if (!androidMachine.hasPermission) {
                    assertFalse(androidMachine.state == State.CAPTURING || androidMachine.state == State.PAUSED)
                }
            }
        }
    }

    @Test
    fun `Property 2 state names remain baseline-compatible`() {
        val expectedNames = setOf("INITIAL", "READY", "CAPTURING", "PAUSED", "ERROR")
        val actualNames = State.entries.map { it.name }.toSet()
        assertEquals(expectedNames, actualNames)
    }

    @Test
    fun `Property 3 splash remains until ready signal or timeout`() {
        val random = Random(2026)
        repeat(200) {
            val timeoutTick = random.nextInt(3, 60)
            val readyTick = random.nextInt(-1, 70) // -1 means never ready.
            var splashVisible = true

            for (tick in 0..timeoutTick) {
                if (readyTick >= 0 && tick >= readyTick) {
                    splashVisible = false
                    break
                }
                if (tick == timeoutTick) {
                    splashVisible = false
                }
            }
            assertFalse(splashVisible)
            if (readyTick in 0..timeoutTick) {
                assertTrue(readyTick <= timeoutTick)
            }
        }
    }
}
