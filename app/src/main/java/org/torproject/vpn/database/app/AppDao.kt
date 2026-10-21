package org.torproject.vpn.database.app

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Insert
import androidx.room3.MapColumn
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import kotlinx.coroutines.flow.Flow

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface AppDao {

    @Query(
        """
        SELECT * FROM apps 
        WHERE category != 'TOR'
        ORDER BY
            CASE category
                WHEN 'BROWSER' THEN 1
                WHEN 'OTHER' THEN 2
                WHEN 'SYSTEM' THEN 3
                ELSE 4
            END ASC,
            name COLLATE NOCASE ASC
    """
    )
    fun pagedNonTorApps(): PagingSource<Int, App>

    @Query(
        """
        SELECT * FROM apps
        WHERE category = 'TOR'
        ORDER BY name COLLATE NOCASE ASC
    """
    )
    fun pagedTorApps(): PagingSource<Int, App>

    @Query("SELECT * FROM apps WHERE isRoutingEnabled = 1")
    fun protectedApps(): List<App>

    @Query("SELECT packageName FROM apps")
    fun packages(): List<String>

    @Query("SELECT * FROM apps WHERE packageName = :packageName")
    fun getApp(packageName: String): Flow<App>

    @Query(
        """
        SELECT * FROM apps
        WHERE LENGTH(TRIM(:query)) > 0 AND INSTR(LOWER(name), LOWER(:query)) > 0
        ORDER BY
            CASE
                WHEN INSTR(LOWER(name), LOWER(:query)) = 1 THEN 1
                ELSE 2
            END ASC,
            name COLLATE NOCASE ASC
    """
    )
    fun search(query: String): PagingSource<Int, App>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: App): Long

    @Query(
        """
        UPDATE apps 
        SET name = :name, uid = :uid, category = :category 
        WHERE packageName = :packageName
    """
    )
    suspend fun update(packageName: String, name: String, uid: Int, category: String)

    @Transaction
    suspend fun insert(apps: List<App>) {
        apps.forEach { app ->
            val id = insert(app)

            // App already exists, update the data instead
            if (id == -1L) {
                update(
                    packageName = app.packageName,
                    name = app.name,
                    uid = app.uid,
                    category = app.category.name
                )
            }
        }
    }

    @Query("DELETE FROM apps WHERE packageName = :packageName")
    fun delete(packageName: String)

    @Query("DELETE FROM apps WHERE packageName IN (:packages)")
    suspend fun delete(packages: Set<String>)

    @Transaction
    suspend fun chunkedDelete(packages: Set<String>) {
        packages.chunked(500).forEach { chunk ->
            delete(packages)
        }
    }

    @Query("UPDATE apps SET isRoutingEnabled = :enabled WHERE packageName = :packageName")
    suspend fun updateRouting(packageName: String, enabled: Boolean)

    @Transaction
    suspend fun updateRouting(packages: List<String>, enabled: Boolean) {
        packages.forEach { pkg -> updateRouting(pkg, enabled) }
    }

    @Query("UPDATE apps SET isRoutingEnabled = :enabled")
    suspend fun updateGlobalRouting(enabled: Boolean)

    @Query(
        """
        SELECT 
            CASE 
                WHEN COUNT(*) = 0 OR SUM(isRoutingEnabled) = 0 THEN 'NONE'
                WHEN SUM(isRoutingEnabled) = COUNT(*) THEN 'ALL'
                ELSE 'SOME'
            END
        FROM apps
    """
    )
    fun globalRouting(): Flow<Routing>

    @Query("SELECT category, COUNT(*) AS count FROM apps GROUP BY category")
    fun categoryCount(): Flow<
        Map<
            @MapColumn(columnName = "category")
            Category,
            @MapColumn(columnName = "count")
            Int
            >
        >
}
