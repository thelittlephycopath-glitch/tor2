package org.torproject.vpn.ui.appdetail

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.torproject.vpn.R

@Composable
fun RefreshDialog(
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    AlertDialog(
        modifier = modifier,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_refresh_circuit),
                contentDescription = null
            )
        },
        title = {
            Text(text = stringResource(R.string.reload_app_circuits))
        },
        text = {
            Text(text = stringResource(R.string.reload_app_circuits_description))
        },
        onDismissRequest = onDismiss,
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.action_refresh))
            }
        }
    )
}
