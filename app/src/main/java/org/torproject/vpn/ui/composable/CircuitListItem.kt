package org.torproject.vpn.ui.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.util.fastForEach
import org.torproject.onionmasq.circuit.CircuitCountryCodes
import org.torproject.vpn.R
import org.torproject.vpn.ui.preview.ThemePreviewProvider
import org.torproject.vpn.ui.theme.heightCountryItem
import org.torproject.vpn.ui.theme.iconSizeDefault
import org.torproject.vpn.ui.theme.spacingSizeSmall
import org.torproject.vpn.ui.theme.spacingSizeVerySmall
import org.torproject.vpn.ui.theme.widthDivider
import org.torproject.vpn.utils.Country

/**
 * Composable to display circuits for an app
 */
@Composable
fun CircuitListItem(
    modifier: Modifier = Modifier,
    appName: String,
    circuitCountryCodes: CircuitCountryCodes
) {
    @Composable
    fun HeaderItem(icon: Painter, label: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacingSizeSmall)
        ) {
            Image(
                painter = icon,
                contentDescription = null
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    fun CountryItem(code: String?) {
        val country = Country.fromCode(code)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacingSizeSmall)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_hops_section),
                contentDescription = null
            )
            Row(
                modifier = Modifier.widthIn(min = heightCountryItem),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacingSizeVerySmall)
            ) {
                country?.let { country ->
                    Image(
                        modifier = Modifier.requiredSize(iconSizeDefault),
                        painter = painterResource(country.flag),
                        contentDescription = null
                    )
                }
                Text(
                    text = country?.locale()?.displayCountry ?: stringResource(R.string.unknown),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    OutlinedCard(
        border = BorderStroke(
            width = widthDivider,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = modifier.padding(dimensionResource(R.dimen.super_toggle_padding))) {
            HeaderItem(
                icon = painterResource(R.drawable.ic_hops_top),
                label = appName
            )

            circuitCountryCodes.countryCodes.fastForEach { code ->
                CountryItem(code = code)
            }

            HeaderItem(
                icon = painterResource(R.drawable.ic_hops_bottom),
                label = stringResource(R.string.the_internet)
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun CircuitListItemPreview() {
    CircuitListItem(
        appName = "Tor VPN",
        circuitCountryCodes = CircuitCountryCodes(arrayListOf("US", "JP", "BR", "ZA", "DE"))
    )
}
