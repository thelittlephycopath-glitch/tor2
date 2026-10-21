package org.torproject.vpn.ui.approuting

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.torproject.vpn.database.AppDatabase
import org.torproject.vpn.database.app.App
import org.torproject.vpn.database.app.Category
import org.torproject.vpn.ui.approuting.data.AppManager
import org.torproject.vpn.ui.approuting.data.AppQueryReceiver
import org.torproject.vpn.utils.PreferenceHelper

@RunWith(AndroidJUnit4::class)
class AppQueryReceiverTest {

    private lateinit var receiver: AppQueryReceiver
    private lateinit var appManager: AppManager
    private lateinit var preferenceHelper: PreferenceHelper

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        receiver = AppQueryReceiver()
        appManager = AppManager(context)
        preferenceHelper = PreferenceHelper(context)
        preferenceHelper.clear()
        runBlocking {
            AppDatabase.getDatabase(context).appDao().delete(appDaoPackages().toSet())
        }
    }

    private fun appDaoPackages(): List<String> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return AppDatabase.getDatabase(context).appDao().packages()
    }

    @Test
    fun testOnReceive_packageAdded() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageName = context.packageName // Use a real package name that exists on the device
        val intent = Intent(Intent.ACTION_PACKAGE_ADDED).apply {
            data = Uri.parse("package:$packageName")
        }

        receiver.onReceive(context, intent)

        // Poll for database update as it happens in a coroutine
        var found = false
        runBlocking {
            for (i in 1..20) {
                val protectedApps = AppDatabase.getDatabase(context).appDao().protectedApps()
                if (protectedApps.any { it.packageName == packageName }) {
                    found = true
                    break
                }
                delay(100.milliseconds)
            }
        }
        assertTrue("expected $packageName in protectedApps", found)
    }

    @Test
    fun testOnReceive_packageRemoved() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageName = "com.example.testapp"
        val intent = Intent(Intent.ACTION_PACKAGE_REMOVED).apply {
            data = Uri.parse("package:$packageName")
        }

        // Add the package first to ensure it can be removed.
        runBlocking {
            AppDatabase.getDatabase(context).appDao().insert(
                App(
                    packageName = packageName,
                    name = "Test App",
                    uid = 1000,
                    category = Category.OTHER,
                    isRoutingEnabled = true
                )
            )
        }

        assertTrue(
            "expected $packageName part of protected apps",
            AppDatabase.getDatabase(context).appDao().protectedApps().any {
                it.packageName ==
                    packageName
            }
        )

        receiver.onReceive(context, intent)

        // Poll for database update
        var removed = false
        runBlocking {
            for (i in 1..20) {
                val protectedApps = AppDatabase.getDatabase(context).appDao().protectedApps()
                if (protectedApps.none { it.packageName == packageName }) {
                    removed = true
                    break
                }
                delay(100.milliseconds)
            }
        }

        assertTrue("expected $packageName not in protectedApps", removed)
    }

    @Test
    fun testOnReceive_invalidAction_doesNothing() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageName = "com.example.testapp"
        val intent = Intent(Intent.ACTION_PACKAGE_REPLACED).apply {
            data = Uri.parse("package:$packageName")
        }
        val initialProtectedApps = preferenceHelper.protectedApps
        receiver.onReceive(context, intent)
        assert(initialProtectedApps == preferenceHelper.protectedApps)
    }

    @Test
    fun testOnReceive_nullContext_doesNothing() {
        val packageName = "com.example.testapp"
        val intent = Intent(Intent.ACTION_PACKAGE_ADDED).apply {
            data = Uri.parse("package:$packageName")
        }
        val initialProtectedApps = preferenceHelper.protectedApps
        receiver.onReceive(null, intent)
        assert(initialProtectedApps == preferenceHelper.protectedApps)
    }

    @Test
    fun testOnReceive_nullIntent_doesNothing() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val initialProtectedApps = preferenceHelper.protectedApps
        receiver.onReceive(context, null)
        assert(initialProtectedApps == preferenceHelper.protectedApps)
    }

    @Test
    fun testOnReceive_noPackageData_doesNothing() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(Intent.ACTION_PACKAGE_ADDED)
        val initialProtectedApps = preferenceHelper.protectedApps
        receiver.onReceive(context, intent)
        assert(initialProtectedApps == preferenceHelper.protectedApps)
    }
}
