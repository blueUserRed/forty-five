package com.fourinachamber.fortyfive.steam

import com.codedisaster.steamworks.*
import com.codedisaster.steamworks.SteamAuth.AuthSessionResponse
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
//        https://code-disaster.github.io/steamworks4j/getting-started.html
//        user = SteamUser(userCallback);
        userStats = SteamUserStats(statsCallback)
        userStats.requestGlobalStats(0)
//        userStats.setStatF() //check this and so on
//        userStats.setStatI() //check this and so on
        SteamAPI.runCallbacks() //This method needs to be called every few frames

//        userStats.getGlobalStat()
    }

    private lateinit var userStats: SteamUserStats

    private var user: SteamUser? = null

/*
    private val userCallback: SteamUserCallback = object : SteamUserCallback {
        override fun onAuthSessionTicket(authTicket: SteamAuthTicket, result: SteamResult) {}
        override fun onValidateAuthTicket(
            steamID: SteamID,
            authSessionResponse: AuthSessionResponse,
            ownerSteamID: SteamID
        ) {
        }

        override fun onMicroTxnAuthorization(appID: Int, orderID: Long, authorized: Boolean) {}
        override fun onEncryptedAppTicket(result: SteamResult) {}
        fun onGetTicketForWebApi(authTicket: SteamAuthTicket?, result: SteamResult, ticketData: ByteArray) {
            println(
                "auth ticket for web API: " + ticketData.size + " bytes" +
                        ", result=" + result.toString()
            )
        }
    }*/

    private val statsCallback: SteamUserStatsCallback = object : SteamUserStatsCallback {
        override fun onUserStatsReceived(gameId: Long, steamIDUser: SteamID?, result: SteamResult?) {
            println(
                (("User stats received: gameId=$gameId").toString() + ", userId=" + steamIDUser!!.accountID) +
                        ", result=" + result.toString()
            )

            val numAchievements: Int = userStats.numAchievements
            println("Num of achievements: $numAchievements")

            for (i in 0 until numAchievements) {
                val name: String = userStats.getAchievementName(i)
                val achieved: Boolean = userStats.isAchieved(name, false)
                println("# " + i + " : name=" + name + ", achieved=" + (if (achieved) "yes" else "no"))
            }
        }

        override fun onUserStatsStored(gameId: Long, result: SteamResult) {
            println("User stats stored: gameId=" + gameId +
                    ", result=" + result.toString());
        }

        override fun onUserStatsUnloaded(steamIDUser: SteamID) {
            println("User stats unloaded: userId=" + steamIDUser.accountID);
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