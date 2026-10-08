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
    val texts=LocalAppContext.current
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=Panel,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        LazyColumn(Modifier.fillMaxWidth(),contentPadding=PaddingValues(start=24.dp,end=24.dp,bottom=24.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {
                Text(texts.getString(R.string.your_sessions),style=MaterialTheme.typography.headlineMedium)
                Text(texts.getString(R.string.session_hint),color=Muted,modifier=Modifier.padding(top=8.dp,bottom=8.dp))
            }
            item {
                OutlinedButton(onClick={ onSelect(null) },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)) {
                    Text(if(selected==null) texts.getString(R.string.no_session_selected) else texts.getString(R.string.no_session))
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
                            Text(texts.getString(R.string.cookie_count,profile.count),color=Muted,style=MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick={ onRemove(profile.id) }) { Icon(Icons.Outlined.DeleteOutline,texts.getString(R.string.delete_session)) }
                    }
                }
            } }
            item {
                Button(onClick=onNew,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(18.dp)) {
                    Icon(Icons.Outlined.Add,null); Spacer(Modifier.width(8.dp)); Text(texts.getString(R.string.new_session))
                }
                TextButton(onClick=onImport,modifier=Modifier.fillMaxWidth()) { Text(texts.getString(R.string.import_cookies)) }
                Text(texts.getString(R.string.session_privacy),
                    color=Muted,style=MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable fun CookieUrlDialog(onDismiss: ()->Unit,onOpen: (String)->Unit) {
    val texts=LocalAppContext.current
    var value by rememberSaveable(stateSaver=TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue("https://",TextRange(8)))
    }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest=onDismiss,containerColor=Panel,title={ Text(texts.getString(R.string.connect_session)) },
        text={
            Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Text(texts.getString(R.string.open_login_hint),color=Muted)
                OutlinedTextField(value,onValueChange={
                    val cleaned=it.text.replace(Regex("^https://(?=https?://)"),"")
                    value=if(cleaned==it.text) it else TextFieldValue(cleaned,TextRange(cleaned.length)); error=null
                },label={ Text(texts.getString(R.string.site_address)) },singleLine=true,modifier=Modifier.fillMaxWidth(),
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),shape=RoundedCornerShape(16.dp),
                    isError=error!=null,supportingText={ error?.let { Text(AppLanguage.message(texts,it)) } })

            }
        },confirmButton={ TextButton(onClick={
            runCatching {
                normalizeUrl(value.text).also { require(it.startsWith("https://")) { texts.getString(R.string.secure_url) } }
            }.onSuccess(onOpen).onFailure { error=it.message ?: texts.getString(R.string.check_address) }
        }) { Text(texts.getString(R.string.open_site)) } },dismissButton={ TextButton(onClick=onDismiss) { Text(texts.getString(R.string.cancel)) } })
}

