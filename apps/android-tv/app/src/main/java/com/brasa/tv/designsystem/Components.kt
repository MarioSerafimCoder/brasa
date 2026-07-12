package com.brasa.tv.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.brasa.tv.core.model.CatalogItem

@Composable fun BrasaLogo(modifier:Modifier=Modifier){Text("BRasa",modifier=modifier,color=BrasaOrange,fontSize=38.sp)}
@Composable fun BrasaButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true){Button(onClick=onClick,modifier=modifier.heightIn(min=56.dp),enabled=enabled){Text(text,fontSize=18.sp)}}
@Composable fun MediaCard(item:CatalogItem,onClick:()->Unit,modifier:Modifier=Modifier){Button(onClick=onClick,modifier=modifier.width(if(item.type=="movie")180.dp else 260.dp),contentPadding=PaddingValues(0.dp)){Column(Modifier.fillMaxWidth().background(BrasaSurface,RoundedCornerShape(14.dp))){AsyncImage(model=item.backdrop.ifBlank{item.poster},contentDescription=item.title,modifier=Modifier.fillMaxWidth().aspectRatio(if(item.type=="movie")2f/3f else 16f/9f),contentScale=ContentScale.Crop);Text(item.title,Modifier.padding(12.dp),fontSize=17.sp,maxLines=2);item.progress?.takeIf{it.percentage>0}?.let{Box(Modifier.fillMaxWidth().height(5.dp).background(Color.DarkGray)){Box(Modifier.fillMaxWidth((it.percentage/100).toFloat().coerceIn(0f,1f)).fillMaxHeight().background(BrasaOrange))}}}}}
@Composable fun MessagePanel(title:String,message:String,action:String?=null,onAction:()->Unit={}){Column(Modifier.fillMaxSize().padding(64.dp),verticalArrangement=Arrangement.Center){BrasaLogo();Spacer(Modifier.height(24.dp));Text(title,fontSize=38.sp);Spacer(Modifier.height(12.dp));Text(message,fontSize=20.sp,color=Color.LightGray);if(action!=null){Spacer(Modifier.height(24.dp));BrasaButton(action,onAction)}}}
