package org.torproject.vpn.database.app

import androidx.paging.PagingSource
import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.torproject.vpn.database.AppDatabase

@RunWith(AndroidJUnit4::class)
class AppDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var appDao: AppDao

    @Before
    fun setUp() {
        // Using an in-memory database for testing to ensure a fresh state for each test
        database =
            Room.inMemoryDatabaseBuilder(
                InstrumentationRegistry.getInstrumentation().targetContext,
                AppDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        appDao = database.appDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndGetApp() = runTest {
        val app = App(
            packageName = "com.example.app",
            name = "Test App",
            uid = 1234,
            category = Category.OTHER,
            isRoutingEnabled = true
        )
        appDao.insert(listOf(app))

        val result = appDao.getApp("com.example.app").first()

        assertEquals(app.packageName, result.packageName)
        assertEquals(app.name, result.name)
        assertEquals(app.uid, result.uid)
        assertEquals(app.category, result.category)
        assertEquals(app.isRoutingEnabled, result.isRoutingEnabled)
    }

    @Test
    fun updateApp() = runTest {
        val uid = 5467587
        val app = App(
            packageName = "com.example.app",
            name = "Test App",
            uid = 1234,
            category = Category.OTHER,
            isRoutingEnabled = false
        )
        appDao.insert(listOf(app))

        appDao.update("com.example.app", "Updated Name", uid, Category.SYSTEM.name)

        val result = appDao.getApp("com.example.app").first()

        assertEquals("Updated Name", result.name)
        assertEquals(uid, result.uid)
        assertEquals(Category.SYSTEM, result.category)
        assertEquals(false, result.isRoutingEnabled)
    }

    @Test
    fun deleteApp() = runTest {
        val app = App(
            packageName = "com.example.app",
            name = "Test App",
            uid = 1234,
            category = Category.OTHER,
            isRoutingEnabled = false
        )
        appDao.insert(listOf(app))
        appDao.delete("com.example.app")

        val packages = appDao.packages()
        assertFalse("App should be removed from the database", packages.contains("com.example.app"))
    }

    @Test
    fun searchReturnsResults() = runTest {
        val app1 = App("pkg.one", "App One", 1, Category.BROWSER, false)
        val app2 = App("pkg.two", "Other App", 2, Category.OTHER, false)
        appDao.insert(listOf(app1, app2))

        val result = appDao.search("App")

        assert(result != null)
    }

    @Test
    fun pagedNonTorAppsSortsNamesCaseInsensitively() = runTest {
        appDao.insert(
            listOf(
                App("pkg.zeta", "zeta", 1, Category.OTHER, true),
                App("pkg.beta", "Beta", 2, Category.OTHER, true),
                App("pkg.alpha", "alpha", 3, Category.OTHER, true),
                App("pkg.gamma", "Gamma", 4, Category.OTHER, true)
            )
        )

        val names = appDao.pagedNonTorApps().loadAll().map { it.name }

        assertEquals(listOf("alpha", "Beta", "Gamma", "zeta"), names)
    }

    @Test
    fun pagedNonTorAppsSortsByCategoryThenName() = runTest {
        appDao.insert(
            listOf(
                App("pkg.system", "a system", 1, Category.SYSTEM, true),
                App("pkg.other", "B other", 2, Category.OTHER, true),
                App("pkg.browser", "c browser", 3, Category.BROWSER, true),
                App("pkg.other2", "a other", 4, Category.OTHER, true),
                App("pkg.tor", "A tor", 5, Category.TOR, true)
            )
        )

        val names = appDao.pagedNonTorApps().loadAll().map { it.name }

        assertEquals(listOf("c browser", "a other", "B other", "a system"), names)
    }

    @Test
    fun pagedTorAppsSortsNamesCaseInsensitively() = runTest {
        appDao.insert(
            listOf(
                App("pkg.orbot", "Orbot", 1, Category.TOR, true),
                App("pkg.briar", "briar", 2, Category.TOR, true),
                App("pkg.cwtch", "cwtch", 3, Category.TOR, true),
                App("pkg.other", "Another", 4, Category.OTHER, true)
            )
        )

        val names = appDao.pagedTorApps().loadAll().map { it.name }

        assertEquals(listOf("briar", "cwtch", "Orbot"), names)
    }

    @Test
    fun searchSortsPrefixMatchesFirst() = runTest {
        appDao.insert(
            listOf(
                App("pkg.one", "my app", 1, Category.OTHER, true),
                App("pkg.two", "app store", 2, Category.OTHER, true),
                App("pkg.three", "best app", 3, Category.OTHER, true),
                App("pkg.four", "apps", 4, Category.OTHER, true),
                App("pkg.five", "browser", 5, Category.BROWSER, true)
            )
        )

        val names = appDao.search("APP").loadAll().map { it.name }

        assertEquals(listOf("app store", "apps", "best app", "my app"), names)
    }

    @Test
    fun searchSortsNamesCaseInsensitively() = runTest {
        appDao.insert(
            listOf(
                App("pkg.one", "My App", 1, Category.OTHER, true),
                App("pkg.two", "Apps", 2, Category.OTHER, true),
                App("pkg.three", "app store", 3, Category.OTHER, true),
                App("pkg.four", "best app", 4, Category.OTHER, true)
            )
        )

        val names = appDao.search("app").loadAll().map { it.name }

        assertEquals(listOf("app store", "Apps", "best app", "My App"), names)
    }

    private suspend fun PagingSource<Int, App>.loadAll(): List<App> {
        val result = load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 100,
                placeholdersEnabled = false
            )
        )
        assertTrue("Expected a page but got $result", result is PagingSource.LoadResult.Page)
        return (result as PagingSource.LoadResult.Page).data
    }
}
