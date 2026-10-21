package org.torproject.vpn.ui.approuting.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.torproject.vpn.database.AppDatabase
import org.torproject.vpn.database.app.AppDao
import org.torproject.vpn.utils.PreferenceHelper

@RunWith(AndroidJUnit4::class)
class AppManagerTest {

    private lateinit var context: Context
    private lateinit var appDao: AppDao
    private lateinit var preferenceHelper: PreferenceHelper
    private lateinit var appManager: AppManager

    private var originalShouldMigrate = true
    private var originalProtectAllApps = true
    private var originalProtectedApps = mutableSetOf<String>()
    private var originalUnprotectedPackages = emptyList<String>()

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        appDao = AppDatabase.getDatabase(context).appDao()
        preferenceHelper = PreferenceHelper(context)
        appManager = AppManager(context)

        originalShouldMigrate = preferenceHelper.shouldMigrateProtectedApps
        originalProtectAllApps = preferenceHelper.protectAllApps
        originalProtectedApps = preferenceHelper.protectedApps
        originalUnprotectedPackages = appDao.packages() - protectedPackages()

        // Start every test from a synced database with all apps protected
        preferenceHelper.shouldMigrateProtectedApps = false
        appManager.updateAppDatabase()
        appDao.updateGlobalRouting(true)
    }

    @After
    fun tearDown() = runBlocking {
        preferenceHelper.shouldMigrateProtectedApps = originalShouldMigrate
        preferenceHelper.protectAllApps = originalProtectAllApps
        preferenceHelper.protectedApps = originalProtectedApps
        appDao.updateGlobalRouting(true)
        appDao.updateRouting(originalUnprotectedPackages, false)
    }

    @Test
    fun migrationKeepsAllAppsProtectedWhenProtectAllAppsIsSet() = runTest {
        preferenceHelper.shouldMigrateProtectedApps = true
        preferenceHelper.protectAllApps = true
        preferenceHelper.protectedApps = mutableSetOf(context.packageName)

        appManager.updateAppDatabase()

        assertEquals(appDao.packages().toSet(), protectedPackages())
        assertFalse(preferenceHelper.shouldMigrateProtectedApps)
    }

    @Test
    fun migrationDisablesRoutingForAllAppsWhenNoneAreProtected() = runTest {
        preferenceHelper.shouldMigrateProtectedApps = true
        preferenceHelper.protectAllApps = false
        preferenceHelper.protectedApps = mutableSetOf()

        appManager.updateAppDatabase()

        assertEquals(emptySet<String>(), protectedPackages())
        assertFalse(preferenceHelper.shouldMigrateProtectedApps)
    }

    @Test
    fun migrationKeepsOnlySpecificAppsProtected() = runTest {
        preferenceHelper.shouldMigrateProtectedApps = true
        preferenceHelper.protectAllApps = false
        // pkg.not.installed is not in the database and must be ignored
        preferenceHelper.protectedApps = mutableSetOf(context.packageName, "pkg.not.installed")

        appManager.updateAppDatabase()

        assertTrue(
            "The app itself must be in the database",
            appDao.packages().contains(context.packageName)
        )
        assertEquals(setOf(context.packageName), protectedPackages())
        assertFalse(preferenceHelper.shouldMigrateProtectedApps)
    }

    @Test
    fun migrationRunsOnlyOnce() = runTest {
        preferenceHelper.shouldMigrateProtectedApps = true
        preferenceHelper.protectAllApps = false
        preferenceHelper.protectedApps = mutableSetOf()
        appManager.updateAppDatabase()

        // User protects an app after the migration
        appManager.protectApp(true, context.packageName)
        appManager.updateAppDatabase()

        assertEquals(setOf(context.packageName), protectedPackages())
    }

    @Test
    fun migrationIsSkippedWhenNotNeeded() = runTest {
        preferenceHelper.shouldMigrateProtectedApps = false
        preferenceHelper.protectAllApps = false
        preferenceHelper.protectedApps = mutableSetOf()

        appManager.updateAppDatabase()

        assertEquals(appDao.packages().toSet(), protectedPackages())
    }

    private fun protectedPackages(): Set<String> =
        appDao.protectedApps().map { it.packageName }.toSet()
}
