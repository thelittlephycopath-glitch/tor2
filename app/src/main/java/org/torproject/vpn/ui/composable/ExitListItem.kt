package org.torproject.vpn.ui.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.torproject.vpn.R
import org.torproject.vpn.ui.preview.ThemePreviewProvider
import org.torproject.vpn.ui.theme.iconSizeBig
import org.torproject.vpn.ui.theme.listItemSize
import org.torproject.vpn.utils.Country

/**
 * Composable to display available exits for tor vpn in a list
 */
@Composable
fun ExitListItem(
    modifier: Modifier = Modifier,
    country: Country,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    ListItem(
        modifier = modifier
            .height(listItemSize)
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton
            ),
        leadingContent = {
            Image(
                modifier = Modifier.requiredSize(iconSizeBig),
                painter = painterResource(country.flag),
                contentDescription = null
            )
        },
        overlineContent = {
            if (country == Country.RANDOM) {
                Text(
                    text = stringResource(R.string.exit_location_safest),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        headlineContent = {
            Text(
                text = when (country) {
                    Country.RANDOM -> stringResource(R.string.exit_location_automatic)
                    else -> country.locale()!!.displayCountry
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            RadioButton(
                selected = isSelected,
                onClick = null
            )
        }
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun ExitListItemRandomPreview() {
    ExitListItem(country = Country.RANDOM, isSelected = true)
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun ExitListItemOtherPreview() {
    ExitListItem(country = Country.ANTARCTICA)
}
