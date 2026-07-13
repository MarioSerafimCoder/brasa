package com.brasa.tv.designsystem

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TvFocusTest {
    @get:Rule val compose=createComposeRule()
    @Test fun requestedTvActionReceivesFocus(){
        compose.setContent{val requester=remember{FocusRequester()};BrasaTheme{BrasaButton("Reproduzir",{},Modifier.focusRequester(requester))};LaunchedEffect(Unit){requester.requestFocus()}}
        compose.onNodeWithText("Reproduzir").assertIsFocused()
    }
    @Test fun customTvActionRemainsClickable(){
        var clicked=false
        compose.setContent{BrasaTheme{BrasaButton("Assistir",{clicked=true},style=BrasaButtonStyle.Primary)}}
        compose.onNodeWithText("Assistir").performClick()
        compose.runOnIdle{assertTrue(clicked)}
    }
}
