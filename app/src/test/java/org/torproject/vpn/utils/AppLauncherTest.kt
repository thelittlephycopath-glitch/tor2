package org.torproject.vpn.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.material3.SnackbarHostState
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.torproject.vpn.R
import org.torproject.vpn.database.app.App
import org.torproject.vpn.database.app.Category

@OptIn(ExperimentalCoroutinesApi::class)
class AppLauncherTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val context = mockk<Context>(relaxed = true)
    private val snackBarHostState = mockk<SnackbarHostState>(relaxed = true)
    private val intent = mockk<Intent>(relaxed = true)

    private val app = App(
        packageName = "com.example.testapp",
        name = "Test App",
        uid = 10001,
        category = Category.OTHER,
        isRoutingEnabled = true
    )

    private val appLauncher = AppLauncher(
        context = context,
        snackBarHostState = snackBarHostState,
        backgroundContext = testDispatcher
    )

    @Before
    fun setUp() {
        mockkStatic("org.torproject.vpn.utils.UtilsKt")
        mockkStatic(Log::class)

        every { Log.e(any(), any(), any()) } returns 0
        every { context.getString(R.string.error_app_open) } returns "Failed to open app"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun canLaunch_returnsTrue_whenLaunchIntentIsNotNull() {
        every { getLaunchIntent(context, app.packageName) } returns intent

        assertTrue(appLauncher.canLaunch(app))
    }

    @Test
    fun canLaunch_returnsFalse_whenLaunchIntentIsNull() {
        every { getLaunchIntent(context, app.packageName) } returns null

        assertFalse(appLauncher.canLaunch(app))
    }

    @Test
    fun invoke_successfulLaunch_startsActivityAndNoSnackbar() = runTest(testDispatcher) {
        every { getLaunchIntent(context, app.packageName) } returns intent

        appLauncher(app)

        verify { context.startActivity(intent) }
        coVerify(exactly = 0) { snackBarHostState.showSnackbar(message = any()) }
    }

    @Test
    fun invoke_failedLaunch_logsErrorAndShowsSnackbar() = runTest(testDispatcher) {
        every { getLaunchIntent(context, app.packageName) } returns intent
        val exception = ActivityNotFoundException("Activity not found")
        every { context.startActivity(intent) } throws exception

        appLauncher(app)

        verify { context.startActivity(intent) }
        verify { Log.e(AppLauncher.TAG, "Failed to launch ${app.packageName}", exception) }
        coVerify { snackBarHostState.showSnackbar(message = "Failed to open app") }
    }
}
