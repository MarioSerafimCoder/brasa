package com.brasa.tv.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.designsystem.*

@Composable fun SearchScreen(state:BrasaUiState,onSearch:(String)->Unit,onItem:(CatalogItem)->Unit,onBack:()->Unit){BackHandler(onBack=onBack);var query by remember{mutableStateOf("")};Column(Modifier.fillMaxSize().padding(38.dp)){Row(horizontalArrangement=Arrangement.spacedBy(18.dp)){BrasaButton("Voltar",onBack);BasicTextField(query,{query=it;onSearch(it)},singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(Color.White,fontSize=24.sp),modifier=Modifier.width(620.dp).background(BrasaSurface).padding(18.dp));if(query.isNotBlank())BrasaButton("Limpar",{query="";onSearch("")})};Spacer(Modifier.height(24.dp));if(query.isNotBlank()&&state.searchResults.isEmpty())Text("Nenhum conteúdo encontrado.",fontSize=22.sp);LazyVerticalGrid(columns=GridCells.Adaptive(190.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){items(state.searchResults,key={it.mediaKey}){MediaCard(it,{onItem(it)})}}}}
