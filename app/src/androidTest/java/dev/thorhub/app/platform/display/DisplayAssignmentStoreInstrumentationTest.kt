package dev.thorhub.app.platform.display

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisplayAssignmentStoreInstrumentationTest {
    private lateinit var store: DisplayAssignmentStore

    @Before
    fun setUp() = runBlocking {
        store = DisplayAssignmentStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.clear()
    }

    @After
    fun tearDown() = runBlocking {
        store.clear()
    }

    @Test
    fun assignmentRoundTripsWithoutTransientDisplayIds() = runBlocking {
        val upper = descriptor(id = 0, name = "Virtual upper", width = 1080, height = 2400)
        val lower = descriptor(id = 6, name = "Virtual lower", width = 1080, height = 1240)

        store.save("build-a", upper, lower, verified = true)
        val loaded = store.load("build-a")

        assertThat(loaded).isEqualTo(
            StoredDisplayAssignment(
                upperSignatureHash = DisplayAssignmentStore.sha256(upper.identitySignature),
                lowerSignatureHash = DisplayAssignmentStore.sha256(lower.identitySignature),
                verified = true,
            ),
        )
        assertThat(loaded?.upperSignatureHash).doesNotContain(upper.name)
        assertThat(loaded?.lowerSignatureHash).doesNotContain(lower.name)
    }

    @Test
    fun firmwareChangeInvalidatesPersistedAssignment() = runBlocking {
        store.save(
            buildFingerprintHash = "old-build",
            upper = descriptor(id = 0, name = "Upper"),
            lower = descriptor(id = 2, name = "Lower"),
            verified = true,
        )

        assertThat(store.load("new-build")).isNull()
        assertThat(store.load("old-build")?.verified).isTrue()
    }

    @Test
    fun clearRemovesAssignment() = runBlocking {
        store.save(
            buildFingerprintHash = "build-a",
            upper = descriptor(id = 0, name = "Upper"),
            lower = descriptor(id = 2, name = "Lower"),
            verified = false,
        )

        store.clear()

        assertThat(store.load("build-a")).isNull()
    }

    private fun descriptor(
        id: Int,
        name: String,
        width: Int = 1280,
        height: Int = 720,
    ) = DisplayDescriptor(
        id = id,
        uniqueId = null,
        name = name,
        type = 0,
        typeLabel = "UNKNOWN_PUBLIC_API",
        flags = 0,
        state = 2,
        stateLabel = "ON",
        widthPx = width,
        heightPx = height,
        densityDpi = 320,
        rotation = 0,
        refreshRateHz = 60f,
        supportedRefreshRatesHz = listOf(60f),
        isDefault = id == 0,
    )
}
