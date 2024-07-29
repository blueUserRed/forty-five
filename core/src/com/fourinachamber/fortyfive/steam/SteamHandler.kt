package com.fourinachamber.fortyfive.steam

import com.codedisaster.steamworks.*
import com.fourinachamber.fortyfive.utils.FortyFiveLogger


//        https://code-disaster.github.io/steamworks4j/getting-started.html
class SteamHandler {


    private val statsCallback: SteamUserStatsCallback = object : SteamUserStatsCallback {
        override fun onUserStatsReceived(gameId: Long, steamIDUser: SteamID, result: SteamResult) {
            println(
                (("User stats received: gameId=$gameId").toString() + ", userId=" + steamIDUser.accountID) +
                        ", result=" + result.toString()
            )
            val numAchievements: Int = userStats.numAchievements
            println("Num of achievements: $numAchievements")

            for (i in 0 until numAchievements) {
                val name: String = userStats.getAchievementName(i)
                val achieved: Boolean = userStats.isAchieved(name, false)
                println("# " + i + " : name=" + name + ", achieved=" + (if (achieved) "yes" else "no"))
            }
            val stats = arrayOf(
                "collection_progress",
                "nbr_of_runs"
            ).asList() //this is hardcoded, idk why there isn't a better possibility
            println("Num of stats: ${stats.size}")
            for (i in stats.indices) {
                val name: String = stats[i]
                val statF = userStats.getStatF(stats[i], -1F)
                if (statF == -1F)
                    println("# " + i + " : name=" + name + ", progress=" + userStats.getStatI(stats[i], -1))
                else println("# $i : name=$name, progress=$statF")
            }
        }

        override fun onUserStatsStored(gameId: Long, result: SteamResult) {}

        override fun onUserStatsUnloaded(steamIDUser: SteamID) {}

        override fun onUserAchievementStored(
            gameId: Long,
            isGroupAchievement: Boolean,
            achievementName: String?,
            curProgress: Int,
            maxProgress: Int
        ) {
//            println("User achievement stored: gameId=" + gameId + ", name=" + achievementName +
//                    ", progress=" + curProgress + "/" + maxProgress)
        }

        override fun onLeaderboardFindResult(leaderboard: SteamLeaderboardHandle, found: Boolean) {
//            println(
//                "Leaderboard find result: handle=$leaderboard, found=" + if (found) "yes" else "no"
//            )
//
//            if (found) {
//                println(
//                    ("Leaderboard: name=" + userStats.getLeaderboardName(leaderboard) +
//                            ", entries=" + userStats.getLeaderboardEntryCount(leaderboard))
//                )
////                currentLeaderboard = leaderboard
//            }
        }

        override fun onLeaderboardScoresDownloaded(
            leaderboard: SteamLeaderboardHandle?,
            entries: SteamLeaderboardEntriesHandle?,
            numEntries: Int
        ) {
        }

        override fun onLeaderboardScoreUploaded(
            success: Boolean,
            leaderboard: SteamLeaderboardHandle?,
            score: Int,
            scoreChanged: Boolean,
            globalRankNew: Int,
            globalRankPrevious: Int
        ) {
        }

        override fun onNumberOfCurrentPlayersReceived(success: Boolean, players: Int) {
//            println("Number of current players received: $players");
        }

        override fun onGlobalStatsReceived(gameId: Long, result: SteamResult) {
//            println("Global stats received: gameId=$gameId, result=$result");
        }
    }


    private lateinit var userStats: SteamUserStats

    init {
        //        https://code-disaster.github.io/steamworks4j/getting-started.html
        try {
            SteamAPI.loadLibraries()
            if (!SteamAPI.init()) {
                FortyFiveLogger.severe(LOGTAG, "Couldn't load Steam API")
            }else{
                userStats = SteamUserStats(statsCallback)
//                loadUserStats()
            }
        } catch (e: SteamException) {
            FortyFiveLogger.severe(LOGTAG, "Couldn't load Steam Librarys")
        }
    }

    private fun loadUserStats() {
        userStats.resetAllStats(true) //TODO remove after done testing
        userStats.requestGlobalStats(10)
        userStats.requestCurrentStats()

//        Thread() {
//            Thread.sleep(5000)
//            println("now start")
//            userStats.setStatI("nbr_of_runs", 3)
//            userStats.setStatI("collection_progress", 1)
////            userStats.setStatF("complete_collection",1F)
//            Thread.sleep(5000)
//            println("Hallo welt")
//            userStats.requestCurrentStats()
//        }.start()
    }

    private var countFrames = 0
    fun update() {
        if (SteamAPI.isSteamRunning() && (countFrames++ and 4) == 0) SteamAPI.runCallbacks()
    }

    fun updateStats(stat: UserStat, value: Number = 1) {
        if (!SteamAPI.isSteamRunning()) return
        when (stat.progressType) {
            ProgressType.INSTANT -> {
                if (value == 1) userStats.setAchievement(stat.name)
                else userStats.clearAchievement(stat.name)
            }

            ProgressType.NUMBERBASED -> {
                if (stat.isInt) userStats.setStatI(stat.name, value.toInt())
                else userStats.setStatF(stat.name, value.toFloat())
            }

            ProgressType.INCREMENTAL -> {
                if (stat.isInt) userStats.setStatI(stat.name, userStats.getStatI(stat.name, 0) + value.toInt())
                else userStats.setStatF(stat.name, userStats.getStatF(stat.name, 0F) + value.toFloat())
            }
        }
        userStats.storeStats()
//        Thread() {
//            Thread.sleep(5000)
//            println("Hallo welt")
//            userStats.requestCurrentStats()
//        }.start()
    }

    fun end() {
        SteamAPI.shutdown()
    }
//    private var user: SteamUser? = null
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

    companion object {
        private val LOGTAG = "AchievementHandler"
    }
}

sealed class UserStat {

    object CollectionCardAmount : UserStat() {
        override val progressType: ProgressType = ProgressType.NUMBERBASED
        override val name: String = "collection_progress"
        override val isInt: Boolean = true
    }

    object Cash1000InHand : UserStat() {
        override val progressType: ProgressType = ProgressType.INSTANT
        override val name: String = "CASH_1000"
        override val isInt: Boolean = false //it is a boolean :)
    }

    object NumberOfRuns : UserStat() {
        override val progressType: ProgressType = ProgressType.INCREMENTAL
        override val name: String = "nbr_of_runs"
        override val isInt: Boolean = true
    }

    abstract val progressType: ProgressType
    abstract val name: String
    abstract val isInt: Boolean
}

enum class ProgressType {
    INSTANT, //are direct achievements, the rest change "stats" which then automatically represent achievements
    NUMBERBASED,
    INCREMENTAL
}