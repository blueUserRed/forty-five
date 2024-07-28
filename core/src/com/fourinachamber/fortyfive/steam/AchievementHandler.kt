package com.fourinachamber.fortyfive.steam

import com.codedisaster.steamworks.*
import com.fourinachamber.fortyfive.utils.FortyFiveLogger


object AchievementHandler {
    fun init() {
        try {
            SteamAPI.loadLibraries()
            if (!SteamAPI.init()) {
                FortyFiveLogger.severe(LOGTAG, "Couln't load Steam API")
            }
        } catch (e: SteamException) {
            FortyFiveLogger.severe(LOGTAG, "Couln't load Steam Librarys")
        }
        printUserStats()
//        SteamAPI.shutdown()
    }

    private fun printUserStats() {
        userStats = SteamUserStats(callback)
    }

    private lateinit var userStats: SteamUserStats


    private val callback: SteamUserStatsCallback = object : SteamUserStatsCallback {
        override fun onUserStatsReceived(gameId: Long, steamIDUser: SteamID?, result: SteamResult?) {
            System.out.println(
                (("User stats received: gameId=$gameId").toString() + ", userId=" + steamIDUser!!.accountID).toString() +
                        ", result=" + result.toString()
            )

            val numAchievements: Int = userStats.getNumAchievements()
            println("Num of achievements: $numAchievements")

            for (i in 0 until numAchievements) {
                val name: String = userStats.getAchievementName(i)
                val achieved: Boolean = userStats.isAchieved(name, false)
                println("# " + i + " : name=" + name + ", achieved=" + (if (achieved) "yes" else "no"))
            }
        }

        override fun onUserStatsStored(gameId: Long, result: SteamResult) {
            System.out.println("User stats stored: gameId=" + gameId +
                    ", result=" + result.toString());
        }

        override fun onUserStatsUnloaded(steamIDUser: SteamID) {
            System.out.println("User stats unloaded: userId=" + steamIDUser.accountID);
        }

        override fun onUserAchievementStored(
            gameId: Long,
            isGroupAchievement: Boolean,
            achievementName: String?,
            curProgress: Int,
            maxProgress: Int
        ) {
            println("User achievement stored: gameId=" + gameId + ", name=" + achievementName +
                    ", progress=" + curProgress + "/" + maxProgress)
        }

        override fun onLeaderboardFindResult(leaderboard: SteamLeaderboardHandle, found: Boolean) {
            println(
                "Leaderboard find result: handle=$leaderboard, found=" + if (found) "yes" else "no"
            )

            if (found) {
                println(
                    ("Leaderboard: name=" + userStats.getLeaderboardName(leaderboard) +
                            ", entries=" + userStats.getLeaderboardEntryCount(leaderboard))
                )
//                currentLeaderboard = leaderboard
            }
        }

        override fun onLeaderboardScoresDownloaded(
            leaderboard: SteamLeaderboardHandle?,
            entries: SteamLeaderboardEntriesHandle?,
            numEntries: Int
        ) {
            TODO("Not yet implemented")
        }

        override fun onLeaderboardScoreUploaded(
            success: Boolean,
            leaderboard: SteamLeaderboardHandle?,
            score: Int,
            scoreChanged: Boolean,
            globalRankNew: Int,
            globalRankPrevious: Int
        ) {
            TODO("Not yet implemented")
        }

        override fun onNumberOfCurrentPlayersReceived(success: Boolean, players: Int) {
            println("Number of current players received: $players");
        }

        override fun onGlobalStatsReceived(gameId: Long, result: SteamResult) {
            println("Global stats received: gameId=$gameId, result=$result");
        }

    }

    private val LOGTAG = "AchievementHandler"
}

enum class ACHIEVEMENT {
    COMPLETE_COLLECTION,
}