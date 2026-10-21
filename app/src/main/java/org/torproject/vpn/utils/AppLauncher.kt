package org.torproject.vpn.utils

import android.content.Context
import android.util.Log
import androidx.compose.material3.SnackbarHostState
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext
import org.torproject.vpn.R
import org.torproject.vpn.database.app.App

class AppLauncher(
    private val context: Context,
    private val snackBarHostState: SnackbarHostState,
    private val backgroundContext: CoroutineContext
) {

    fun canLaunch(app: App): Boolean {
        return getLaunchIntent(context, app.packageName) != null
    }

    suspend operator fun invoke(app: App) {
        try {
            context.startActivity(getLaunchIntent(context, app.packageName))
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to launch ${app.packageName}", exception)
            withContext(backgroundContext) {
                snackBarHostState.showSnackbar(
                    message = context.getString(R.string.error_app_open)
                )
            }
        }
    }

    companion object {
        const val TAG: String = "AppLauncher"
    }
}
