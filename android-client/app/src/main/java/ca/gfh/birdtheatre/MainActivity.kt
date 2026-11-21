package ca.gfh.birdtheatre

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Native Android navigation shell; browser portal supplies the shared community workflows. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val origin = BuildConfig.PORTAL_URL.trimEnd('/')
        val trustedHost = Uri.parse(origin).host
        setContent {
            val colors = lightColorScheme(primary=Color(0xFF205A40),secondary=Color(0xFF7DB891))
            MaterialTheme(colorScheme=colors) {
                var page by remember { mutableStateOf("explore") }
                var browser by remember { mutableStateOf<WebView?>(null) }
                BackHandler(enabled = browser?.canGoBack() == true) { browser?.goBack() }
                Scaffold(
                    topBar = {
                        Surface(color=Color(0xFF123227)) {
                            Row(modifier=Modifier.fillMaxWidth().padding(17.dp)) {
                                Text("✦  bird theatre.",color=Color.White,style=MaterialTheme.typography.titleLarge)
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar(containerColor=Color.White) {
                            listOf("explore" to "Explore", "cameras" to "Stations", "sightings" to "Visits", "alerts" to "Alerts", "rfid" to "RFID")
                                .forEach { (route,label) ->
                                    NavigationBarItem(selected=page==route,onClick={
                                        page=route
                                        browser?.loadUrl("$origin/#$route")
                                    },icon={Text(when(route){"explore"->"⌂";"cameras"->"▣";"sightings"->"◉";"alerts"->"♧";else->"⌁"})},label={Text(label)})
                                }
                        }
                    }
                ) { inset ->
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                                        val url=request.url
                                        if (url.scheme=="https" && url.host==trustedHost) return false
                                        if (url.scheme=="https" || url.scheme=="mailto") {
                                            try { context.startActivity(Intent(Intent.ACTION_VIEW,url)) } catch (_:Exception) { }
                                        }
                                        return true
                                    }
                                }
                                loadUrl("$origin/#explore")
                                browser=this
                            }
                        },
                        modifier=Modifier.fillMaxSize().padding(inset)
                    )
                }
                DisposableEffect(Unit) { onDispose { browser?.destroy(); browser=null } }
            }
        }
    }
}
