package com.brasa.tv.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.designsystem.*

@Composable fun HomeScreen(state:BrasaUiState,onItem:(CatalogItem)->Unit,onSearch:()->Unit,onSettings:()->Unit,onRefresh:()->Unit){
    val home=state.home
    if(home==null){MessagePanel("Carregando sua biblioteca",state.message.ifBlank{"O computador precisa estar ligado e conectado à mesma rede."},"Tentar novamente",onRefresh);return}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=48.dp)){
        item{Row(Modifier.fillMaxWidth().padding(36.dp,24.dp),horizontalArrangement=Arrangement.SpaceBetween){BrasaLogo();Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){BrasaButton("Buscar",onSearch);BrasaButton("Configurações",onSettings)}}}
        items(home.rows,key={it.id}){row->if(row.items.isNotEmpty()){
            Text(row.title,Modifier.padding(horizontal=40.dp,vertical=10.dp),fontSize=26.sp)
            LazyRow(modifier=Modifier.focusRestorer(),contentPadding=PaddingValues(horizontal=40.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)){items(row.items,key={it.mediaKey.ifBlank{it.id}}){item->MediaCard(item,{onItem(item)})}}
            Spacer(Modifier.height(18.dp))
        }}
    }
}
