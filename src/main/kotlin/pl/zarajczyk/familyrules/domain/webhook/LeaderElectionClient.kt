package pl.zarajczyk.familyrules.domain.webhook

import org.springframework.stereotype.Service

interface LeaderElectionClient {
    fun isLeader(): Boolean
}

@Service
class OneInstanceLeaderElectionClient : LeaderElectionClient {
    override fun isLeader(): Boolean {
        return true // we assume only one instance is running
    }
}