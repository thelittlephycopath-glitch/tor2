package org.torproject.vpn.database.app

import androidx.annotation.StringRes
import org.torproject.vpn.R

/**
 * Status to reflect routing status of all installed apps
 */
enum class Routing(@StringRes val localizedTitle: Int) {
    ALL(R.string.apps_description_all_protected),
    SOME(R.string.label_some_protected),
    NONE(R.string.label_not_protected);

    val isProtectingAllApps: Boolean
        get() = this == ALL
}
