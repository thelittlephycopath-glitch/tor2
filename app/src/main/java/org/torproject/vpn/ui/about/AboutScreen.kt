package org.torproject.vpn.ui.about

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.core.net.toUri
import java.text.SimpleDateFormat
import java.util.Date
import org.torproject.vpn.BuildConfig
import org.torproject.vpn.Constants
import org.torproject.vpn.R
import org.torproject.vpn.ui.composable.TopAppBar
import org.torproject.vpn.ui.preview.ThemePreviewProvider
import org.torproject.vpn.ui.theme.appIconSizeLDPIPlus
import org.torproject.vpn.ui.theme.heightDataCard
import org.torproject.vpn.ui.theme.spacingSizeBig
import org.torproject.vpn.ui.theme.spacingSizeNormal
import org.torproject.vpn.ui.theme.spacingSizeSmall
import org.torproject.vpn.ui.theme.spacingSizeVerySmall
import org.torproject.vpn.ui.theme.widthDivider
import org.torproject.vpn.utils.getIconForPackage

@Composable
fun AboutScreen(onNavigateUp: () -> Unit) {
    val context = LocalContext.current
    val versionNameRegex = Regex("""\d+\.\d+\.\d+""")
    val simpleDateFormat = SimpleDateFormat("yyyy/MM/dd", LocalLocale.current.platformLocale)
    val packageInfo = context.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, 0)

    ScreenContent(
        onNavigateUp = onNavigateUp,
        versionName = versionNameRegex.find(BuildConfig.VERSION_NAME)!!.value,
        lastUpdated = simpleDateFormat.format(Date(packageInfo.lastUpdateTime)),
        onOpenUrl = { url ->
            Intent(Intent.ACTION_VIEW).also { intent ->
                intent.data = url.toUri()
                context.startActivity(intent)
            }
        }
    )
}

@Composable
fun ScreenContent(
    onNavigateUp: () -> Unit = {},
    versionName: String = BuildConfig.VERSION_NAME,
    lastUpdated: String = "",
    onOpenUrl: (url: String) -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    @Composable
    fun Title() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacingSizeVerySmall),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacingSizeVerySmall)
        ) {
            Image(
                modifier = Modifier
                    .requiredSize(appIconSizeLDPIPlus)
                    .clip(CircleShape),
                bitmap = getIconForPackage(packageName = BuildConfig.APPLICATION_ID),
                contentDescription = null
            )

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    fun InfoCard() {
        Card(
            modifier = Modifier.padding(vertical = spacingSizeVerySmall),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = heightDataCard)
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(
                    modifier = Modifier
                        .padding(spacingSizeBig)
                        .weight(1F)
                ) {
                    Text(
                        text = stringResource(R.string.version),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = versionName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                VerticalDivider(thickness = widthDivider)

                Column(
                    modifier = Modifier
                        .padding(spacingSizeBig)
                        .weight(1F)
                ) {
                    Text(
                        text = stringResource(R.string.last_updated),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = lastUpdated,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    @Composable
    fun Description() {
        val torProjectString = stringResource(R.string.tor_project)
        val fullDescription = stringResource(R.string.about_description_long, torProjectString)

        val startIndex = fullDescription.indexOf(torProjectString)
        val endIndex = startIndex + torProjectString.length

        val annotatedString = buildAnnotatedString {
            append(fullDescription)

            if (startIndex != -1) {
                addLink(
                    url = LinkAnnotation.Url(
                        url = Constants.URL_WEBSITE,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        ),
                        linkInteractionListener = { onOpenUrl(Constants.URL_WEBSITE) }
                    ),
                    start = startIndex,
                    end = endIndex
                )
            }
        }

        Text(
            modifier = Modifier.padding(vertical = spacingSizeNormal),
            text = annotatedString,
            style = MaterialTheme.typography.bodyLarge
        )
    }

    @Composable
    fun DonationCard() {
        Card(
            modifier = Modifier.padding(vertical = spacingSizeVerySmall),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Image(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(R.dimen.support_image_height))
                    .clip(RoundedCornerShape(spacingSizeSmall)),
                painter = painterResource(R.drawable.about_support_image),
                contentDescription = null,
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacingSizeNormal, vertical = spacingSizeBig),
                verticalArrangement = Arrangement.spacedBy(spacingSizeVerySmall)
            ) {
                Text(
                    text = stringResource(R.string.about_support_title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stringResource(R.string.about_support_description),
                    style = MaterialTheme.typography.bodyMedium
                )

                Button(
                    modifier = Modifier
                        .padding(top = spacingSizeNormal)
                        .fillMaxWidth(),
                    onClick = { onOpenUrl(Constants.URL_DONATE) }
                ) {
                    Text(
                        text = stringResource(R.string.about_support_action),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(onNavigateUp = onNavigateUp, scrollBehavior = scrollBehavior)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    start = spacingSizeNormal,
                    end = spacingSizeNormal
                )
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacingSizeVerySmall)
        ) {
            Title()

            InfoCard()

            Description()

            DonationCard()
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun AboutScreenPreview() {
    ScreenContent(
        versionName = "1.9.0",
        lastUpdated = "2026/08/18"
    )
}
