/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.wearable.wear.alwayson

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityTests {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val testDispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()

    /**
     * A timestamp in the relatively far, far future (year 2200).
     *
     * This ensures the real alarm manager won't actually trigger.
     */
    private var instant = YEAR_2200_INSTANT

    private lateinit var scenario: ActivityScenario<MainActivity>

    private lateinit var uiDevice: UiDevice

    @Before
    fun setup() {
        uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // Ensure we are starting in active mode
        uiDevice.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)

        // Override the active dispatcher with a paused test one.
        testDispatcher.pauseDispatcher()
        activeDispatcher = testDispatcher

        updateClock()
        scenario = launchActivity()
    }

    @After
    fun teardown() {
        scenario.close()
    }

    @Test
    fun initialTextIsCorrect(): Unit = runBlocking {
        scenario.moveToState(Lifecycle.State.RESUMED)

        onView(withId(R.id.time)).check(matches(withText(ZERO_SEC_DISPLAY)))
        onView(withId(R.id.time_stamp))
            .check(
                matches(
                    withText(
                        context.getString(
                            R.string.timestamp_label,
                            YEAR_2200_INSTANT.toEpochMilli()
                        )
                    )
                )
            )
        onView(withId(R.id.state)).check(matches(withText(context.getString(R.string.mode_active_label))))
        onView(withId(R.id.draw_count)).check(matches(withText(context.getString(R.string.draw_count_label, 1))))
    }

    @Test
    fun textIsCorrectAfterFiveSeconds(): Unit = runBlocking {
        scenario.moveToState(Lifecycle.State.RESUMED)

        // Advance 5 seconds, one at a time
        repeat(5) {
            advanceTime(Duration.ofSeconds(1))
        }

        onView(withId(R.id.time)).check(matches(withText(FIVE_SEC_DISPLAY)))
        onView(withId(R.id.time_stamp))
            .check(
                matches(
                    withText(
                        context.getString(
                            R.string.timestamp_label,
                            YEAR_2200_INSTANT.plusSeconds(5).toEpochMilli()
                        )
                    )
                )
            )
        onView(withId(R.id.state)).check(matches(withText(context.getString(R.string.mode_active_label))))
        onView(withId(R.id.draw_count)).check(matches(withText(context.getString(R.string.draw_count_label, 6))))
    }

    @Test
    fun textIsCorrectAfterGoingIntoAmbientMode(): Unit = runBlocking {
        scenario.moveToState(Lifecycle.State.RESUMED)

        // Advance 5 seconds, one at a time
        repeat(5) {
            advanceTime(Duration.ofSeconds(1))
        }

        uiDevice.pressKeyCode(KeyEvent.KEYCODE_SLEEP)
        Thread.sleep(1000) // Ugly sleep, without it sometimes ambient mode won't be entered
        Espresso.onIdle()

        onView(withId(R.id.time)).check(matches(withText(FIVE_SEC_DISPLAY)))
        onView(withId(R.id.time_stamp))
            .check(
                matches(
                    withText(
                        context.getString(
                            R.string.timestamp_label,
                            YEAR_2200_INSTANT.plusSeconds(5).toEpochMilli()
                        )
                    )
                )
            )
        onView(withId(R.id.state)).check(matches(withText(context.getString(R.string.mode_ambient_label))))
        onView(withId(R.id.draw_count)).check(matches(withText(context.getString(R.string.draw_count_label, 7))))
    }

    @Test
    fun textIsCorrectAfterGoingIntoAmbientModeAndReceivingIntent(): Unit = runBlocking {
        scenario.moveToState(Lifecycle.State.RESUMED)

        // Advance 5 seconds, one at a time
        repeat(5) {
            advanceTime(Duration.ofSeconds(1))
        }

        advanceTime(Duration.ofMillis(500))

        uiDevice.pressKeyCode(KeyEvent.KEYCODE_SLEEP)
        Thread.sleep(1000) // Ugly sleep, without it sometimes ambient mode won't be entered
        Espresso.onIdle()

        // Simulate a sent broadcast
        advanceTime(Duration.ofSeconds(5))
        scenario.onActivity {
            PendingIntent.getBroadcast(
                it, 0, Intent(MainActivity.AMBIENT_UPDATE_ACTION), PendingIntent.FLAG_UPDATE_CURRENT
            ).send()
        }

        Thread.sleep(1000) // Ugly sleep, without it sometimes the broadcast won't be received
        Espresso.onIdle()

        onView(withId(R.id.time)).check(matches(withText(TEN_SEC_DISPLAY)))
        onView(withId(R.id.time_stamp))
            .check(
                matches(
                    withText(
                        context.getString(
                            R.string.timestamp_label,
                            YEAR_2200_INSTANT.plusSeconds(10).plusMillis(500).toEpochMilli()
                        )
                    )
                )
            )
        onView(withId(R.id.state)).check(matches(withText(context.getString(R.string.mode_ambient_label))))
        onView(withId(R.id.draw_count)).check(matches(withText(context.getString(R.string.draw_count_label, 8))))
    }

    @Test
    fun textIsCorrectAfterReturningToActiveMode(): Unit = runBlocking {
        scenario.moveToState(Lifecycle.State.RESUMED)

        // Advance 5 seconds, one at a time
        repeat(5) {
            advanceTime(Duration.ofSeconds(1))
        }

        advanceTime(Duration.ofMillis(500))

        // Enter ambient mode
        uiDevice.pressKeyCode(KeyEvent.KEYCODE_SLEEP)
        Thread.sleep(1000) // Ugly sleep, without it sometimes ambient mode won't be entered
        Espresso.onIdle()

        // Simulate a sent broadcast
        advanceTime(Duration.ofSeconds(5))
        scenario.onActivity {
            PendingIntent.getBroadcast(
                it, 0, Intent(MainActivity.AMBIENT_UPDATE_ACTION), PendingIntent.FLAG_UPDATE_CURRENT
            ).send()
        }

        Espresso.onIdle()

        advanceTime(Duration.ofSeconds(2))

        // Exit ambient mode
        uiDevice.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)
        Thread.sleep(1000) // Ugly sleep, without it sometimes ambient mode won't be exited
        Espresso.onIdle()

        onView(withId(R.id.time)).check(matches(withText(TWELVE_SEC_DISPLAY)))
        onView(withId(R.id.time_stamp))
            .check(
                matches(
                    withText(
                        context.getString(
                            R.string.timestamp_label,
                            YEAR_2200_INSTANT.plusSeconds(12).plusMillis(500).toEpochMilli()
                        )
                    )
                )
            )
        onView(withId(R.id.state)).check(matches(withText(context.getString(R.string.mode_active_label))))
        onView(withId(R.id.draw_count)).check(matches(withText(context.getString(R.string.draw_count_label, 9))))
    }

    /**
     * Advances the simulated time by the given [duration], updating the [testDispatcher], the [clock] and running
     * any updates due to those changes.
     */
    private fun advanceTime(duration: Duration) {
        instant += duration
        updateClock()
        testDispatcher.advanceTimeBy(duration.toMillis())
        Espresso.onIdle()
    }

    /**
     * Updates the [clock] to be fixed at the given [instant].
     */
    private fun updateClock() {
        clock = Clock.fixed(instant, ZoneId.of("UTC"))
    }
}

private val YEAR_2200_INSTANT = Instant.ofEpochMilli(7258118400000L)

private const val ZERO_SEC_DISPLAY = "00:00:00"
private const val FIVE_SEC_DISPLAY = "00:00:05"
private const val TEN_SEC_DISPLAY = "00:00:10"
private const val TWELVE_SEC_DISPLAY = "00:00:12"
