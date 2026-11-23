package de.muttis.tinderfotos

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val Pink = Color(0xFFFF4458)
private val Green = Color(0xFF21D07A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Pink)) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFFF4F4F4)) { App() }
            }
        }
    }
}

/** Lädt alle Fotos der Galerie (neueste zuerst). */
private fun loadPhotos(context: Context): List<Uri> {
    val result = mutableListOf<Uri>()
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    context.contentResolver.query(
        collection,
        arrayOf(MediaStore.Images.Media._ID),
        null, null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC"
    )?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (c.moveToNext()) result += ContentUris.withAppendedId(collection, c.getLong(idCol))
    }
    return result
}

@Composable
private fun App() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("muttis", Context.MODE_PRIVATE) }

    val permission = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember {
        mutableStateOf(
            context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Muttis tinder für Fotos", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Pink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text("Die App braucht Zugriff auf deine Fotos, damit du sie durchwischen kannst.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { permLauncher.launch(permission) }) { Text("Zugriff erlauben") }
        }
        return
    }

    // Bereits behaltene Fotos werden nicht noch einmal gezeigt.
    var keptIds by remember { mutableStateOf(prefs.getStringSet("kept", emptySet())!!.toSet()) }
    var photos by remember {
        mutableStateOf(loadPhotos(context).filter { ContentUris.parseId(it).toString() !in keptIds })
    }
    var index by remember { mutableIntStateOf(0) }
    val toDelete = remember { mutableStateListOf<Uri>() }
    // Verlauf für "Rückgängig": true = gelöscht-markiert, false = behalten
    val history = remember { mutableStateListOf<Boolean>() }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == android.app.Activity.RESULT_OK) {
            val deleted = toDelete.toSet()
            photos = photos.filter { it !in deleted }
            toDelete.clear()
            history.clear()
            index = 0
        }
    }

    fun deleteMarked() {
        if (toDelete.isEmpty()) return
        val sender = MediaStore.createDeleteRequest(context.contentResolver, toDelete.toList()).intentSender
        deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
    }

    fun decide(delete: Boolean) {
        val uri = photos[index]
        if (delete) toDelete += uri else {
            keptIds = keptIds + ContentUris.parseId(uri).toString()
            prefs.edit().putStringSet("kept", keptIds).apply()
        }
        history += delete
        index++
    }

    fun undo() {
        if (history.isEmpty() || index == 0) return
        index--
        val uri = photos[index]
        if (history.removeAt(history.lastIndex)) toDelete.remove(uri) else {
            keptIds = keptIds - ContentUris.parseId(uri).toString()
            prefs.edit().putStringSet("kept", keptIds).apply()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Muttis tinder für Fotos", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Pink, modifier = Modifier.weight(1f))
            Text("${minOf(index, photos.size)}/${photos.size}", color = Color.Gray)
        }
        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (index >= photos.size) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (photos.isEmpty()) "Keine Fotos mehr da 🎉" else "Alle Fotos durchgewischt 🎉", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("${toDelete.size} Foto(s) zum Löschen markiert")
                    Spacer(Modifier.height(16.dp))
                    if (toDelete.isNotEmpty()) {
                        Button(
                            onClick = { deleteMarked() },
                            colors = ButtonDefaults.buttonColors(containerColor = Pink)
                        ) { Text("${toDelete.size} Foto(s) endgültig löschen") }
                    }
                }
            } else {
                // Nächste Karte liegt dahinter
                if (index + 1 < photos.size) PhotoCard(photos[index + 1], Modifier.padding(8.dp))
                SwipeCard(
                    key = photos[index],
                    onSwiped = { decide(delete = it) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            val active = index < photos.size
            RoundButton(Icons.Default.Close, Pink, 64.dp, enabled = active) { decide(true) }
            RoundButton(Icons.Default.Undo, Color.Gray, 48.dp, enabled = history.isNotEmpty()) { undo() }
            RoundButton(Icons.Default.Favorite, Green, 64.dp, enabled = active) { decide(false) }
        }
        if (toDelete.isNotEmpty() && index < photos.size) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { deleteMarked() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("${toDelete.size} markierte Foto(s) jetzt löschen", color = Pink)
            }
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, size: androidx.compose.ui.unit.Dp, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = tint, disabledContainerColor = Color(0xFFE8E8E8))
    ) { Icon(icon, contentDescription = null, modifier = Modifier.size(size / 2)) }
}

@Composable
private fun PhotoCard(uri: Uri, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Color.White)) {
        AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun SwipeCard(key: Uri, onSwiped: (delete: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val offsetX = remember(key) { Animatable(0f) }
    val offsetY = remember(key) { Animatable(0f) }
    val screenWidth = with(LocalDensity.current) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val threshold = screenWidth * 0.3f

    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .rotate(offsetX.value / 40f)
            .pointerInput(key) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(offsetX.value) > threshold) {
                                val left = offsetX.value < 0
                                offsetX.animateTo(if (left) -screenWidth * 1.5f else screenWidth * 1.5f)
                                onSwiped(left)
                            } else {
                                launch { offsetX.animateTo(0f) }
                                offsetY.animateTo(0f)
                            }
                        }
                    }
                ) { change, drag ->
                    change.consume()
                    scope.launch {
                        offsetX.snapTo(offsetX.value + drag.x)
                        offsetY.snapTo(offsetY.value + drag.y)
                    }
                }
            }
    ) {
        PhotoCard(key)
        val p = (offsetX.value / threshold).coerceIn(-1f, 1f)
        if (p < -0.1f) Label("LÖSCHEN", Pink, -p, Alignment.TopEnd)
        if (p > 0.1f) Label("BEHALTEN", Green, p, Alignment.TopStart)
    }
}

@Composable
private fun BoxScope.Label(text: String, color: Color, alpha: Float, align: Alignment) {
    Text(
        text,
        color = color,
        fontSize = 32.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .align(align)
            .padding(24.dp)
            .background(Color.White.copy(alpha = 0.8f * alpha), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .rotate(if (align == Alignment.TopEnd) 12f else -12f)
    )
}
