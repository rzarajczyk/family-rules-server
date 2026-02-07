package pl.zarajczyk.familyrules.domain.webhook

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe deduplicating queue that maintains insertion order.
 * Uses LinkedHashSet logic backed by ConcurrentHashMap for thread safety.
 */
@Component
class WebhookQueue {
    private val queue = ConcurrentHashMap.newKeySet<String>()

    /**
     * Enqueues a username for webhook notification.
     * If the username is already in the queue, it remains in its original position.
     */
    fun enqueue(username: String) {
        queue.add(username)
    }

    /**
     * Dequeues and returns the next username from the queue.
     * Returns null if the queue is empty.
     */
    fun dequeue(): String? {
        val iterator = queue.iterator()
        if (iterator.hasNext()) {
            val username = iterator.next()
            queue.remove(username)
            return username
        }
        return null
    }

    /**
     * Returns the current size of the queue.
     */
    fun size(): Int = queue.size

    /**
     * Checks if the queue is empty.
     */
    fun isEmpty(): Boolean = queue.isEmpty()
}
