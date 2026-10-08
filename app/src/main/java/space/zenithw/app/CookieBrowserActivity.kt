package space.zenithw.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CookieBrowserActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val initial=runCatching { normalizeUrl(intent.getStringExtra("url").orEmpty()) }
            .getOrNull()?.takeIf { it.startsWith("https://") } ?: run { finish(); return }
        setContent {
            val base=androidx.compose.ui.platform.LocalContext.current
            val localized=remember(base) { AppLanguage.context(base) }
            CompositionLocalProvider(LocalAppContext provides localized,
                androidx.compose.ui.platform.LocalConfiguration provides localized.resources.configuration) {
                ZenithTheme { CookieBrowser(initial) }
            }
        }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun CookieBrowser(initial: String) {
        val texts=LocalAppContext.current
        var current by remember { mutableStateOf(initial) }
        var loading by remember { mutableStateOf(true) }
        var message by remember { mutableStateOf<String?>(null) }
        var saving by remember { mutableStateOf(false) }
        val vault=(application as ZenithApplication).vault
        val scope=rememberCoroutineScope()
        val view=remember {
            WebView(this@CookieBrowserActivity).apply {
                settings.javaScriptEnabled=true
                settings.domStorageEnabled=true
                settings.allowFileAccess=false
                settings.allowContentAccess=false
                settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this,false)
                webViewClient=object: WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView,request: WebResourceRequest): Boolean =
                        request.url.scheme!="https"
                    override fun onPageStarted(view: WebView,url: String,favicon: Bitmap?) { current=url;loading=true }
                    override fun onPageFinished(view: WebView,url: String) { current=url;loading=false }
                    override fun onReceivedSslError(view: WebView,handler: SslErrorHandler,error: SslError) {
                        handler.cancel()
                        message=texts.getString(R.string.ssl_error)
                    }
                }
                loadUrl(initial)
            }
        }
        DisposableEffect(view) { onDispose { view.stopLoading();view.destroy() } }
        BackHandler {
            if(view.canGoBack()) view.goBack() else finish()
        }
        Scaffold(containerColor=Ink,topBar={
            TopAppBar(title={
                Text(Uri.parse(current).host ?: texts.getString(R.string.connect_session),style=MaterialTheme.typography.titleMedium)
            },navigationIcon={ IconButton(onClick={ finish() }) { Icon(Icons.Outlined.ArrowBack,texts.getString(R.string.back)) } },
                actions={ IconButton(onClick={ startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(current))) }) {
                    Icon(Icons.Outlined.OpenInNew,texts.getString(R.string.open_browser))
                } },colors=TopAppBarDefaults.topAppBarColors(containerColor=Panel))
        },bottomBar={
            Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                Text(message?.let { AppLanguage.message(texts,it) } ?: texts.getString(R.string.save_login_hint),
                    color=Muted,style=MaterialTheme.typography.bodyMedium)
                Button(onClick={
                    if(!saving) {
                        saving=true
                        val uri=Uri.parse(view.url ?: current)
                        val header=CookieManager.getInstance().getCookie(uri.toString()).orEmpty()
                        scope.launch {
                            try {
                                require(uri.scheme=="https")
                                val profile=withContext(Dispatchers.IO) {
                                    val saved=vault.capture(uri.host ?: error(texts.getString(R.string.missing_site)),header)
                                    CookieManager.getInstance().flush()
                                    saved
                                }
                                setResult(RESULT_OK,Intent().putExtra("profileId",profile.id));finish()
                            } catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch(e: Exception) { message=e.message ?: texts.getString(R.string.session_save_error);saving=false }
                        }
                    }
                },enabled=!saving,modifier=Modifier.fillMaxWidth().padding(top=12.dp).heightIn(min=56.dp)) {
                    Text(if(saving) texts.getString(R.string.saving) else texts.getString(R.string.save_session))
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if(loading) LinearProgressIndicator(Modifier.fillMaxWidth(),color=Silver)
                AndroidView(factory={ view },modifier=Modifier.fillMaxSize())
            }
        }
    }
}
