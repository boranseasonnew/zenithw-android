package space.zenithw.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalAppContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AppReleaseResult?>(null) }
    var checkFailed by remember { mutableStateOf(false) }
    var openFailed by remember { mutableStateOf(false) }
    val storeManaged = BuildConfig.F_DROID
    val stable = BuildConfig.APPLICATION_ID == "space.zenithw.app.stable"
    fun open(url: String) {
        // All call sites use constants or the checker's strictly parsed tag.
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); openFailed = false }
        catch (_: ActivityNotFoundException) { openFailed = true }
        catch (_: SecurityException) { openFailed = true }
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, context.getString(R.string.about_back)) }
            Text(context.getString(R.string.about_title), style = MaterialTheme.typography.headlineMedium)
        }
        Surface(color = Panel, shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Image(painterResource(R.drawable.zenithw), null, Modifier.size(66.dp))
                    Column {
                        Text("ZenithW", style = MaterialTheme.typography.headlineMedium)
                        Text(context.getString(R.string.about_version, BuildConfig.VERSION_NAME), color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(context.getString(R.string.about_intro), style = MaterialTheme.typography.bodyLarge)
                Text(context.getString(R.string.about_creator), color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        AboutCard(Icons.Outlined.SystemUpdate, context.getString(R.string.about_updates_title)) {
            Text(context.getString(if (storeManaged) R.string.about_fdroid_updates else if (!stable) R.string.about_preview_updates else R.string.about_updates_detail), color = Muted, style = MaterialTheme.typography.bodyMedium)
            if (storeManaged) {
                OutlinedButton(onClick = { open(AppReleaseChecker.FDROID_PAGE) }, shape = RoundedCornerShape(14.dp)) {
                    Text(context.getString(R.string.about_fdroid_open))
                    Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp))
                }
            } else if (stable) {
                Button(onClick = {
                    checking = true; checkFailed = false; result = null
                    scope.launch {
                        try { result = AppReleaseChecker.check() }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) { checkFailed = true }
                        finally { checking = false }
                    }
                }, enabled = !checking, shape = RoundedCornerShape(14.dp)) {
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(context.getString(if (checking) R.string.about_checking else R.string.about_check_updates))
                }
            }
            AnimatedVisibility(result != null || checkFailed, enter = fadeIn(tween(140)), exit = fadeOut(tween(100))) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val release = result
                    val message = when {
                        checkFailed -> context.getString(R.string.about_update_error)
                        release?.status == AppReleaseStatus.AVAILABLE -> context.getString(R.string.about_update_available, release.latestVersion.orEmpty())
                        release?.status == AppReleaseStatus.AHEAD -> context.getString(R.string.about_update_ahead)
                        release?.status == AppReleaseStatus.CURRENT -> context.getString(R.string.about_update_current)
                        else -> ""
                    }
                    Text(message, color = if (checkFailed) MaterialTheme.colorScheme.error else Silver, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    if (release?.status == AppReleaseStatus.AVAILABLE) {
                        OutlinedButton(onClick = { open(release.url) }, shape = RoundedCornerShape(14.dp)) {
                            Text(context.getString(R.string.about_view_release))
                            Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        AboutCard(Icons.Outlined.PrivacyTip, context.getString(R.string.about_privacy_title)) {
            Text(context.getString(R.string.about_privacy), color = Muted, style = MaterialTheme.typography.bodyMedium)
            Text(context.getString(R.string.about_connections), color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        AboutCard(Icons.Outlined.Code, context.getString(R.string.about_engine_title)) {
            Text(context.getString(R.string.about_engine), color = Muted, style = MaterialTheme.typography.bodyMedium)
            Text(context.getString(R.string.about_license), color = Muted, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { open("https://github.com/boranseasonnew/zenithw-android") }, shape = RoundedCornerShape(14.dp)) {
                Text(context.getString(R.string.about_source)); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp))
            }
        }
        AboutCard(Icons.Outlined.FavoriteBorder, context.getString(R.string.about_support_title)) {
            Text(context.getString(R.string.about_support), color = Muted, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { open("https://github.com/sponsors/boranseasonnew") }, shape = RoundedCornerShape(14.dp)) {
                Text(context.getString(R.string.about_support_button))
            }
            TextButton(onClick = { open("https://zenithw.space") }) { Text(context.getString(R.string.about_website)) }
        }
        if (openFailed) Text(context.getString(R.string.about_open_error), color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AboutCard(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(21.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, tint = Silver, modifier = Modifier.size(21.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}
