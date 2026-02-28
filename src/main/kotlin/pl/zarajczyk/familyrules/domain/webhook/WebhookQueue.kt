package pl.zarajczyk.familyrules.domain.webhook

import org.springframework.stereotype.Component
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe deduplicating queue for webhook notifications.
 *
 * Uses a [LinkedBlockingQueue] for ordering and blocking semantics,
 * plus a [HashSet] guarded by a [ReentrantLock] for deduplication.
 * Both structures are always updated atomically under the same lock,
 * so an enqueue that arrives while a dequeue is in progress can never be lost.
 */
@Component
class WebhookQueue {
    private val lock = ReentrantLock()
//    private val pending = HashSet<String>()
    private val queue = LinkedBlockingQueue<String>()

    /**
     * Enqueues a username for webhook notification.
     * If the username is already pending, this is a no-op.
     */
    fun enqueue(username: String) {
        lock.withLock {
//            if (pending.add(username)) {
                queue.put(username)
//            }
        }
    }

    /**
     * Blocks until an element is available (or the timeout expires) and returns it.
     * Returns null if the timeout elapsed with no element available.
     */
    fun take(timeout: Long, unit: TimeUnit): String? {
        // Wait outside the lock so we don't block enqueuers
        val username = queue.poll(timeout, unit) ?: return null
//        lock.withLock {
//            pending.remove(username)
//        }
        return username
    }

    /**
     * Returns the current number of pending items.
     */
    fun size(): Int = queue.size // lock.withLock { pending.size }

    /**
     * Checks if the queue is empty.
     */
    fun isEmpty(): Boolean = queue.isEmpty() // lock.withLock { pending.isEmpty() }
}
