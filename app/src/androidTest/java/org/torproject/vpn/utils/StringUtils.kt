package org.torproject.vpn.utils

import android.content.Context
import androidx.annotation.StringRes
import androidx.test.core.app.ApplicationProvider

fun getString(@StringRes id: Int, vararg formatArgs: Any): String {
    return ApplicationProvider.getApplicationContext<Context>().getString(id, *formatArgs)
}
