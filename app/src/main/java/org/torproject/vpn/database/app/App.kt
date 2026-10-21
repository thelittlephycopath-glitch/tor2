package org.torproject.vpn.database.app

import androidx.compose.runtime.Immutable
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import org.torproject.vpn.paging.PagingItem

/**
 * Class to hold data about an app
 */
@Immutable
@Entity(tableName = "apps")
@Serializable
data class App(
    @PrimaryKey val packageName: String,
    val name: String,
    val uid: Int,
    val category: Category,
    val isRoutingEnabled: Boolean
) : PagingItem {

    override val itemKey: String
        get() = packageName

    override val contentType: String
        get() = "App"
}
