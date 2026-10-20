package com.example

import com.example.core.AppChoice
import com.example.core.Selection
import com.example.core.formatBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SelectionTest {
    @Test fun roundTrip() {
        val s = Selection(listOf(AppChoice("a.b", true)), listOf("/sdcard/DCIM"), contacts = true, sms = false, calls = true)
        assertEquals(s, Selection.fromJson(s.toJson()))
    }

    @Test fun emptyAndBroken() {
        assertTrue(Selection.fromJson(null).isEmpty)
        assertTrue(Selection.fromJson("{broken").isEmpty)
    }

    @Test fun bytes() {
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("غير معروف", formatBytes(null))
    }
}
