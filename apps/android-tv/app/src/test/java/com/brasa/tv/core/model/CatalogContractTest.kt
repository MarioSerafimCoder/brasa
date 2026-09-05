package com.brasa.tv.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogContractTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun catalogAcceptsEmptyAndTextualMetadataNumbers() {
        val response = json.decodeFromString(
            CatalogResponse.serializer(),
            """
            {
              "profile": { "id": "adult", "name": "Adulto", "maxContentRating": "18" },
              "movies": [
                { "id": "10", "mediaKey": "movie:10", "title": "Sem nota", "year": "2025", "rating": "" },
                { "id": "11", "mediaKey": "movie:11", "title": "Com nota", "year": 2024, "rating": "8,7" }
              ],
              "series": [
                {
                  "id": "avatar", "mediaKey": "series:avatar", "type": "series", "year": "2005",
                  "seasons": [{ "seasonNumber": "2", "episodes": [{ "id": "e1", "seasonNumber": "2", "episodeNumber": "11" }] }]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(18, response.profile.maxContentRating)
        assertEquals(2025, response.movies[0].year)
        assertNull(response.movies[0].rating)
        assertEquals(8.7, response.movies[1].rating!!, 0.001)
        assertEquals(2005, response.series[0].year)
        assertEquals(2, response.series[0].seasons[0].seasonNumber)
        assertEquals(11, response.series[0].seasons[0].episodes[0].episodeNumber)
    }

    @Test
    fun playbackAcceptsTextualProgressAndDimensions() {
        val playback = json.decodeFromString(
            PlaybackInfo.serializer(),
            """{"mediaKey":"movie:10","duration":"7200000","resumePosition":"1500","width":"1920","height":"1080","preparationProgress":"42,5"}""",
        )
        assertEquals(7_200_000L, playback.duration)
        assertEquals(1_500L, playback.resumePosition)
        assertEquals(1920, playback.width)
        assertEquals(1080, playback.height)
        assertEquals(42.5, playback.preparationProgress, 0.001)
    }

    @Test
    fun progressIsStillEncodedAsJsonNumbers() {
        val encoded = json.parseToJsonElement(json.encodeToString(WatchProgress(currentTime = 12.5, duration = 100.0, percentage = 12.5))).jsonObject
        assertEquals("12.5", encoded.getValue("currentTime").jsonPrimitive.content)
        assertEquals(false, encoded.getValue("currentTime").jsonPrimitive.isString)
    }
}
