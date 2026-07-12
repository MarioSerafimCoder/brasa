package com.brasa.tv.feature.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.designsystem.*

@Composable fun DetailsScreen(state:BrasaUiState,onPlay:(CatalogItem)->Unit,onFavorite:()->Unit,onBack:()->Unit){BackHandler(onBack=onBack);val item=state.selected?:return MessagePanel("Conteúdo indisponível","Volte e escolha outro item.","Voltar",onBack);Box(Modifier.fillMaxSize()){AsyncImage(item.backdrop.ifBlank{item.poster},item.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop);Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(BrasaBackground,BrasaBackground.copy(.78f),Color.Transparent))));Column(Modifier.fillMaxHeight().widthIn(max=850.dp).padding(64.dp),verticalArrangement=Arrangement.Center){Text(item.title,fontSize=52.sp);Text(listOfNotNull(item.year?.toString(),item.duration.takeIf(String::isNotBlank),item.contentRating.takeIf(String::isNotBlank)).joinToString(" · "),fontSize=20.sp,color=Color.LightGray);Spacer(Modifier.height(16.dp));Text(item.overview.ifBlank{"Sem sinopse disponível."},fontSize=21.sp,maxLines=6);Spacer(Modifier.height(24.dp));Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){val first=item.seasons.flatMap{it.episodes}.firstOrNull();BrasaButton(if((item.progress?.percentage?:0.0)>0)"Continuar" else "Reproduzir",{onPlay(first?:item)},enabled=item.streamUrl.isNotBlank()||first!=null);if(item.type=="movie")BrasaButton(if(item.favorite)"Remover da lista" else "Favoritar",onFavorite);BrasaButton("Voltar",onBack)};if(item.seasons.isNotEmpty()){Spacer(Modifier.height(24.dp));Text("Episódios",fontSize=26.sp);LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){items(item.seasons.flatMap{it.episodes},key={it.mediaKey}){episode->BrasaButton("T${episode.seasonNumber}E${episode.episodeNumber} · ${episode.title}",{onPlay(episode)},Modifier.width(300.dp))}}}}}}
