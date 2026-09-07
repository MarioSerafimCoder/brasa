package com.brasa.tv.core.model

import org.junit.Assert.*
import org.junit.Test

class ContinuityTest {
    private fun episode(id:String, season:Int, number:Int, percentage:Double=0.0, completed:Boolean=false, updatedAt:String="") = CatalogItem(id=id,mediaKey="episode:$id",type="episode",seasonNumber=season,episodeNumber=number,progress=WatchProgress(percentage=percentage,completed=completed,updatedAt=updatedAt))

    @Test fun resumesPartialThenAdvancesAcrossMissingEpisodes() {
        val show = CatalogItem(type="series",seasons=listOf(
            Season(0,listOf(episode("special",0,1))),
            Season(1,listOf(episode("e1",1,1,100.0,true,"2026-01-01"),episode("e3",1,3,50.0,false,"2026-01-02"))),
            Season(2,listOf(episode("e4",2,1))),
        ))
        assertEquals("episode:e3", show.seriesContinuation().episode?.mediaKey)
        val completedPartial = show.copy(seasons=show.seasons.map { season -> if(season.seasonNumber==1) season.copy(episodes=season.episodes.map { if(it.id=="e3") it.copy(progress=WatchProgress(percentage=100.0,completed=true,updatedAt="2026-01-03")) else it }) else season })
        assertEquals("episode:e4", completedPartial.seriesContinuation().episode?.mediaKey)
    }

    @Test fun completedSeriesIsExcludedByUnwatchedRule() {
        val show=CatalogItem(type="series",seasons=listOf(Season(1,listOf(episode("e1",1,1,100.0,true)))))
        assertTrue(show.isWatched())
    }
}
