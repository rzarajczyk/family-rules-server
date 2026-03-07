package pl.zarajczyk.familyrules.domain.webhook

import org.springframework.stereotype.Component
import pl.zarajczyk.familyrules.domain.User
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
 *
 * Carries full [User] objects so that the processor does not need to
 * re-fetch the user document from Firestore after dequeuing.
 */
@Component
class WebhookQueue {
    private val lock = ReentrantLock()
//    private val pending = HashSet<String>()
    private val queue = LinkedBlockingQueue<User>()

    fun enqueue(user: User) {
        lock.withLock {
//            if (pending.add(user.getDetails().username)) {
                queue.put(user)
//            }
        }
    }

    /**
     * Blocks until an element is available (or the timeout expires) and returns it.
     * Returns null if the timeout elapsed with no element available.
     */
    fun take(timeout: Long, unit: TimeUnit): User? {
        // Wait outside the lock so we don't block enqueuers
        val user = queue.poll(timeout, unit) ?: return null
//        lock.withLock {
//            pending.remove(user.getDetails().username)
//        }
        return user
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
