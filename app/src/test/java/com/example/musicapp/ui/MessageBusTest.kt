package com.example.musicapp.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageBusTest {

    @Test
    fun post_sameIdTwice_secondIsIgnored() {
        val bus = MessageBus()
        assertTrue(bus.post(UiMessage("a", "첫 번째")))
        assertFalse(bus.post(UiMessage("a", "두 번째")))
    }

    @Test
    fun post_afterRelease_sameIdIsAcceptedAgain() {
        val bus = MessageBus()
        assertTrue(bus.post(UiMessage("a", "첫 번째")))
        bus.release("a")
        assertTrue(bus.post(UiMessage("a", "다시")))
    }

    @Test
    fun post_differentIds_areAllAccepted() {
        val bus = MessageBus()
        assertTrue(bus.post(UiMessage("a", "A")))
        assertTrue(bus.post(UiMessage("b", "B")))
    }
}
