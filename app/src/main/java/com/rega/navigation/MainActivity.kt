import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.rega.navigation.data.OpenMapApi
import com.rega.navigation.data.PlaceResult
import com.rega.navigation.data.RouteResult
import com.rega.navigation.location.DeviceLocation
import com.rega.navigation.ui.RegaLanguage
import com.rega.navigation.ui.RegaStrings
import kotlinx.coroutines.launch
import java.util.Locale

private val Navy = Color(0xFF102A43)
private val Cyan = Color(0xFF20C7D9)
private val Orange = Color(0xFFFF9800)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Cyan, tertiary = Orange)) { RegaHome() } }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun RegaHome() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val location = remember { DeviceLocation(context) }
    val api = remember { OpenMapApi() }
    var ttsReady by remember { mutableStateOf(false) }
    val speech = remember {
        TextToSpeech(context) { result -> ttsReady = result == TextToSpeech.SUCCESS }
    }
    DisposableEffect(speech) {
        onDispose { speech.stop(); speech.shutdown() }
    }
    var language by remember { mutableStateOf(RegaLanguage.FA) }
    val copy = RegaStrings.forLanguage(language)
    var query by remember { mutableStateOf("") }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var place by remember { mutableStateOf<PlaceResult?>(null) }
    var searchResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var showSearchResults by remember { mutableStateOf(false) }
    var route by remember { mutableStateOf<RouteResult?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(location.hasPermission()) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionGranted = result.values.any { it }
        if (permissionGranted) currentLocation = location.lastKnown()
    }
    val requestLocation = { locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }

    LaunchedEffect(Unit) {
        if (location.hasPermission()) currentLocation = location.lastKnown()
        else requestLocation()
    }
    LaunchedEffect(mapReady, currentLocation, place, route, webView) {
        val map = webView
        if (mapReady && map != null) {
            currentLocation?.let { loc ->
                map.evaluateJavascript("window.waySetUser(${loc.latitude},${loc.longitude});", null)
            }
            place?.let { destination ->
                map.evaluateJavascript("window.waySetDestination(${destination.latitude},${destination.longitude});", null)
            }
            route?.let { activeRoute ->
                val coords = decodePolyline(activeRoute.encodedPolyline).joinToString(",") { "[${it.first},${it.second}]" }
                map.evaluateJavascript("window.wayDrawRoute([$coords]);", null)
            }
        }
    }
    DisposableEffect(permissionGranted) {
        val listener = if (permissionGranted) location.updates { fresh -> currentLocation = fresh } else null
        onDispose { location.stop(listener) }
    }

    val isRtl = language.rtl
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides if (isRtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
        Scaffold(containerColor = Color(0xFFF3F7FA), topBar = {
            Row(Modifier.fillMaxWidth().background(Navy).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                RegaMark(Modifier.size(42.dp))
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("REGA", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(copy.subtitle, color = Color(0xFFB8D9E8), style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = { showLanguages = true }) { Icon(Icons.Default.Language, null, tint = Color.White); Spacer(Modifier.width(5.dp)); Text(language.label, color = Color.White) }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text(copy.searchHint, style = MaterialTheme.typography.bodySmall) },
                        singleLine = true, shape = RoundedCornerShape(16.dp), leadingIcon = { Icon(Icons.Default.Search, null) }
                    )
                    Button(onClick = {
                        val origin = currentLocation
                        if (query.isBlank()) { status = copy.searchHint; return@Button }
                        
                        busy = true; status = copy.searching
                        place = null
                        route = null
                        showSearchResults = false
                        scope.launch {
                            try {
                                val lat = origin?.latitude ?: 35.6892
                                val lon = origin?.longitude ?: 51.3890
                                val results = api.search(query, lat, lon, language.tag)
                                searchResults = results
                                place = results.singleOrNull()
                                route = null
                                showSearchResults = results.size > 1
                                status = place?.let { "${it.title} – ${it.address}" } ?: if (results.isEmpty()) copy.routeFailed else copy.searching
                            } catch (e: Exception) { status = e.message ?: copy.routeFailed }
                            finally { busy = false }
                        }
                    }, enabled = !busy, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(16.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Default.Search, copy.search)
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx -> WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.userAgentString = settings.userAgentString + " REGA-Android/1.0.0"
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    mapReady = true
                                }
                            }
                            setBackgroundColor(android.graphics.Color.rgb(232, 240, 244))
                            loadDataWithBaseURL("https://localhost/", mapHtml(copy.mapAttribution), "text/html", "UTF-8", null)
                            webView = this
                        } },
                        update = { webView = it }
                    )
                    FloatingActionButton(
                        onClick = {
                            if (!location.hasPermission()) requestLocation()
                            else {
                                currentLocation = location.lastKnown()
                                status = if (currentLocation == null) copy.locating else copy.myLocation
                            }
                        }, modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp), containerColor = Color.White, contentColor = Navy
                    ) { Icon(Icons.Default.MyLocation, copy.myLocation) }
                }

                if (place != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(copy.destination, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            Text(place!!.title, fontWeight = FontWeight.Bold)
                            Text(place!!.address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Button(onClick = {
                                val from = currentLocation
                                val to = place
                                if (from == null) { status = copy.locating; requestLocation(); return@Button }
                                if (to == null) return@Button
                                
                                busy = true
                                scope.launch {
                                    try {
                                        val calculated = api.route(from.latitude, from.longitude, to.latitude, to.longitude, language.tag)
                                        route = calculated
                                        status = "${copy.routeReady} • ${formatDistance(calculated.distanceMeters)} • ${formatDuration(calculated.durationSeconds)}"
                                    } catch (e: Exception) { status = e.message ?: copy.routeFailed }
                                    finally { busy = false }
                                }
                            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Navigation, null); Spacer(Modifier.width(8.dp)); Text(copy.route) }
                        }
                    }
                }
                if (route != null && route!!.steps.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                when (language) {
                                    RegaLanguage.FA -> "راهنمای گام‌به‌گام مسیر"
                                    RegaLanguage.EN -> "Step-by-step directions"
                                    RegaLanguage.AR -> "إرشادات خطوة بخطوة"
                                    RegaLanguage.TR -> "Adım adım yol tarifi"
                                    RegaLanguage.CKB -> "ڕێنمایی هەنگاو بە هەنگاو"
                                    RegaLanguage.KMR -> "Rêberiya gav bi gav"
                                }, fontWeight = FontWeight.Bold, color = Navy
                            )
                            OutlinedButton(
                                enabled = ttsReady,
                                onClick = {
                                    val requestedLocale = when (language) {
                                        RegaLanguage.FA, RegaLanguage.CKB -> Locale("fa", "IR")
                                        RegaLanguage.EN -> Locale.UK
                                        RegaLanguage.AR -> Locale("ar")
                                        RegaLanguage.TR -> Locale("tr", "TR")
                                        RegaLanguage.KMR -> Locale("tr", "TR")
                                    }
                                    val fallbackLocale = when (language) {
                                        RegaLanguage.CKB, RegaLanguage.KMR -> Locale("fa", "IR")
                                        else -> Locale.UK
                                    }
                                    val chosenLocale = if (speech.isLanguageAvailable(requestedLocale) >= TextToSpeech.LANG_AVAILABLE) requestedLocale else fallbackLocale
                                    speech.language = chosenLocale
                                    speech.setSpeechRate(0.94f)
                                    speech.speak(
                                        route!!.steps.joinToString(separator = ". ") { it.instruction },
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        "way-route-directions"
                                    )
                                    status = if (chosenLocale == requestedLocale) "راهنمای صوتی فعال شد" else "صدای این زبان در موتور گفتار گوشی موجود نیست؛ از زبان جایگزین استفاده شد"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.VolumeUp, null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when (language) {
                                        RegaLanguage.FA -> "پخش راهنمای صوتی"
                                        RegaLanguage.EN -> "Play voice directions"
                                        RegaLanguage.AR -> "تشغيل الإرشادات الصوتية"
                                        RegaLanguage.TR -> "Sesli yönlendirmeyi oynat"
                                        RegaLanguage.CKB -> "ڕێنمایی دەنگی پەخش بکە"
                                        RegaLanguage.KMR -> "Rêberiya dengî bilîze"
                                    }
                                )
                            }
                            route!!.steps.take(5).forEachIndexed { index, step ->
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(color = Color(0xFFE5F7FA), shape = RoundedCornerShape(8.dp)) {
                                        Text("${index + 1}", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Navy, fontWeight = FontWeight.Bold)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(step.instruction, style = MaterialTheme.typography.bodySmall, color = Navy)
                                        if (step.distanceMeters > 0) Text(formatDistance(step.distanceMeters), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                }
                            }
                            if (route!!.steps.size > 5) Text("${route!!.steps.size - 5} مرحله دیگر در مسیر وجود دارد.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                }
                if (status.isNotBlank()) Text(status, color = Navy, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${copy.appName}  •  v1.0.0", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    Text("${copy.mapAttribution}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    if (showSearchResults) AlertDialog(
        onDismissRequest = { showSearchResults = false },
        title = { Text(copy.destination) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                searchResults.forEach { result ->
                    TextButton(onClick = {
                        place = result
                        route = null
                        showSearchResults = false
                        status = "${result.title} – ${result.address}"
                    }) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(result.title, color = Navy, fontWeight = FontWeight.SemiBold)
                            Text(result.address, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showSearchResults = false }) { Text("OK") } }
    )

    if (showLanguages) AlertDialog(
        onDismissRequest = { showLanguages = false },
        title = { Text(copy.chooseLanguage) },
        text = { Column { RegaLanguage.entries.forEach { item -> TextButton(onClick = { language = item; showLanguages = false }) { Text(item.label, color = Navy) } } } },
        confirmButton = { TextButton(onClick = { showLanguages = false }) { Text("OK") } }
    )
}

@Composable
private fun RegaMark(modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(13.dp), color = Color(0xFF173D59)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(7.dp)) {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * .08f, size.height * .18f); lineTo(size.width * .30f, size.height * .82f)
                lineTo(size.width * .50f, size.height * .30f); lineTo(size.width * .70f, size.height * .82f)
                lineTo(size.width * .92f, size.height * .18f)
            }
            drawPath(p, Cyan, style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width * .14f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawCircle(Orange, radius = size.width * .10f, center = androidx.compose.ui.geometry.Offset(size.width * .92f, size.height * .18f))
        }
    }
}

private fun mapHtml(attribution: String): String {
    return """
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>html,body,#map{height:100%;width:100%;margin:0;background:#e8f0f4} .leaflet-control-attribution{font-size:9px!important}</style></head>
<body><div id="map"></div><script>
const map=L.map('map',{zoomControl:true}).setView([32.2,53.7],5);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'}).addTo(map);
let userMarker=null,destMarker=null,routeLine=null;
window.waySetUser=(lat,lon,zoom)=>{if(userMarker)map.removeLayer(userMarker);userMarker=L.circleMarker([lat,lon],{radius:8,color:'#fff',weight:3,fillColor:'#1689ff',fillOpacity:1}).addTo(map).bindPopup('Your location');if(zoom)map.setView([lat,lon],15);};
window.waySetDestination=(lat,lon)=>{if(destMarker)map.removeLayer(destMarker);destMarker=L.marker([lat,lon]).addTo(map);map.setView([lat,lon],13);};
window.wayDrawRoute=(coords)=>{if(routeLine)map.removeLayer(routeLine);routeLine=L.polyline(coords,{color:'#13a9d0',weight:6,opacity:.9}).addTo(map);map.fitBounds(routeLine.getBounds(),{padding:[30,30]});};
</script></body></html>
""".trimIndent()
}

private fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
    val points = mutableListOf<Pair<Double, Double>>()
    var index = 0; var lat = 0; var lon = 0
    while (index < encoded.length) {
        fun readDelta(): Int {
            var result = 0
            var shift = 0
            var chunk: Int
            do {
                require(index < encoded.length) { "Polyline نامعتبر است" }
                chunk = encoded[index++].code - 63
                require(chunk in 0..63 && shift <= 30) { "Polyline نامعتبر است" }
                result = result or ((chunk and 0x1f) shl shift)
                shift += 5
            } while (chunk >= 0x20)
            return if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        }
        lat += readDelta()
        lon += readDelta()
        points.add(Pair(lat / 1E5, lon / 1E5))
    }
    return points
}
private fun formatDistance(meters: Int) = if (meters >= 1000) "%.1f km".format(Locale.US, meters / 1000.0) else "$meters m"
private fun formatDuration(seconds: Int): String { val mins = seconds / 60; return if (mins >= 60) "${mins / 60} h ${mins % 60} min" else "$mins min" }

