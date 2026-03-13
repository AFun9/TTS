package com.k2fsa.sherpa.tts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.tts.util.AudioHistoryItem
import com.k2fsa.sherpa.tts.util.AudioHistoryStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioHistoryStoreTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        AudioHistoryStore.clear(context)
    }

    @After
    fun tearDown() {
        AudioHistoryStore.clear(context)
    }

    @Test
    fun addItem_preservesFavoriteEntriesWhenTrimming() {
        val baseTime = 1_700_000_000_000L
        AudioHistoryStore.addItem(
            context,
            AudioHistoryItem("/tmp/favorite.wav", baseTime, favorite = true)
        )

        repeat(10) { index ->
            AudioHistoryStore.addItem(
                context,
                AudioHistoryItem("/tmp/item_$index.wav", baseTime + index + 1)
            )
        }

        val items = AudioHistoryStore.getItems(context)
        assertEquals(10, items.size)
        assertTrue(items.any { it.path == "/tmp/favorite.wav" && it.favorite })
        assertTrue(items.none { it.path == "/tmp/item_0.wav" })
    }

    @Test
    fun toggleFavorite_updatesMatchingItemOnly() {
        val itemA = AudioHistoryItem("/tmp/a.wav", 1L, favorite = false)
        val itemB = AudioHistoryItem("/tmp/b.wav", 2L, favorite = false)
        AudioHistoryStore.addItem(context, itemA)
        AudioHistoryStore.addItem(context, itemB)

        val updated = AudioHistoryStore.toggleFavorite(context, "/tmp/a.wav")

        assertTrue(updated.first { it.path == "/tmp/a.wav" }.favorite)
        assertTrue(!updated.first { it.path == "/tmp/b.wav" }.favorite)
    }
}
