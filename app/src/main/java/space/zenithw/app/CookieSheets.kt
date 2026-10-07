@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package space.zenithw.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable fun ProfileSheet(profiles: List<CookieProfile>,selected: String?,onDismiss: ()->Unit,
    onSelect: (String?)->Unit,onRemove: (String)->Unit,onNew: ()->Unit,onImport: ()->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=Panel,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        LazyColumn(Modifier.fillMaxWidth(),contentPadding=PaddingValues(start=24.dp,end=24.dp,bottom=24.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {
                Text("Oturumların.",style=MaterialTheme.typography.headlineMedium)
                Text("Giriş isteyen içerikler için bir oturum seç.",color=Muted,modifier=Modifier.padding(top=8.dp,bottom=8.dp))
            }
            item {
                OutlinedButton(onClick={ onSelect(null) },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)) {
                    Text(if(selected==null) "✓ Oturumsuz devam et" else "Oturumsuz devam et")
                }
            }
            profiles.forEach { profile -> item(key=profile.id) {
                Surface(onClick={ onSelect(profile.id) },color=if(selected==profile.id) PanelRaised else Panel,
                    shape=RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start=14.dp,top=8.dp,bottom=8.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Icon(if(selected==profile.id) Icons.Outlined.CheckCircle else Icons.Outlined.Lock,null,tint=Muted)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile.host,style=MaterialTheme.typography.titleMedium)
                            Text("${profile.count} cookie · şifreli",color=Muted,style=MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick={ onRemove(profile.id) }) { Icon(Icons.Outlined.DeleteOutline,"Oturumu sil") }
                    }
                }
            } }
            item {
                Button(onClick=onNew,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(18.dp)) {
                    Icon(Icons.Outlined.Add,null); Spacer(Modifier.width(8.dp)); Text("Yeni oturum bağla")
                }
                TextButton(onClick=onImport,modifier=Modifier.fillMaxWidth()) { Text("Cookie dosyası içe aktar") }
                Text("Oturum bilgileri bu cihazda şifreli saklanır ve yalnızca seçtiğin indirmede kullanılır. Uygulama içi girişe izin vermeyen sitelerde Netscape cookie dosyası kullanabilirsin.",
                    color=Muted,style=MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable fun CookieUrlDialog(onDismiss: ()->Unit,onOpen: (String)->Unit) {
    var value by rememberSaveable(stateSaver=TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue("https://",TextRange(8)))
    }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest=onDismiss,containerColor=Panel,title={ Text("Oturum bağla") },
        text={
            Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Text("Siteyi aç, hesabına giriş yap ve oturumu kaydet.",color=Muted)
                OutlinedTextField(value,onValueChange={
                    val cleaned=it.text.replace(Regex("^https://(?=https?://)"),"")
                    value=if(cleaned==it.text) it else TextFieldValue(cleaned,TextRange(cleaned.length)); error=null
                },label={ Text("Site adresi") },singleLine=true,modifier=Modifier.fillMaxWidth(),
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),shape=RoundedCornerShape(16.dp),
                    isError=error!=null,supportingText={ error?.let { Text(it) } })
                Choices(listOf("https://www.youtube.com" to "YouTube","https://www.instagram.com" to "Instagram",
                    "https://www.tiktok.com" to "TikTok"),value.text) {
                    value=TextFieldValue(it,TextRange(it.length))
                }
            }
        },confirmButton={ TextButton(onClick={
            runCatching {
                normalizeUrl(value.text).also { require(it.startsWith("https://")) { "Güvenli bir https:// adresi gir." } }
            }.onSuccess(onOpen).onFailure { error=it.message ?: "Adresi kontrol et." }
        }) { Text("Siteyi aç") } },dismissButton={ TextButton(onClick=onDismiss) { Text("Vazgeç") } })
}

