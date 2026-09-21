package com.brasa.tv.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GenreIdentityTest {
    @Test fun accentVariantsShareOneIdentityAndLabel() {
        assertEquals(genreIdentity("Acao"), genreIdentity("Ação"))
        assertEquals("Ação", genreLabel("Acao"))
        assertEquals(listOf("Ação"), listOf("Acao", "Ação").normalizedGenres())
    }

    @Test fun compoundCategoriesRemainDistinct() {
        assertNotEquals(genreIdentity("Ação"), genreIdentity("Ação e aventura"))
        assertEquals("Ação e aventura", genreLabel("Acao e aventura"))
    }
}
