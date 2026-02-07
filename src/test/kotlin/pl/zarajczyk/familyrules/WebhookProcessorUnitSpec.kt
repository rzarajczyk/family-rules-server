package pl.zarajczyk.familyrules

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import pl.zarajczyk.familyrules.domain.webhook.WebhookQueue

class WebhookQueueUnitSpec : FunSpec({

    test("WebhookQueue should enqueue and dequeue usernames in order") {
        // Given
        val queue = WebhookQueue()
        
        // When
        queue.enqueue("user1")
        queue.enqueue("user2")
        queue.enqueue("user3")
        
        // Then
        queue.size() shouldBe 3
        queue.dequeue() shouldBe "user1"
        queue.dequeue() shouldBe "user2"
        queue.dequeue() shouldBe "user3"
        queue.dequeue() shouldBe null
        queue.isEmpty() shouldBe true
    }

    test("WebhookQueue should deduplicate usernames") {
        // Given
        val queue = WebhookQueue()
        
        // When
        queue.enqueue("user1")
        queue.enqueue("user1")
        queue.enqueue("user2")
        queue.enqueue("user1")
        queue.enqueue("user3")
        
        // Then
        queue.size() shouldBe 3
        val dequeued = mutableListOf<String>()
        while (!queue.isEmpty()) {
            queue.dequeue()?.let { dequeued.add(it) }
        }
        dequeued shouldHaveSize 3
        dequeued shouldContain "user1"
        dequeued shouldContain "user2"
        dequeued shouldContain "user3"
    }

    test("WebhookQueue should be thread-safe") {
        // Given
        val queue = WebhookQueue()
        val threads = 10
        val itemsPerThread = 100
        
        // When
        val enqueueThreads = (1..threads).map { threadNum ->
            Thread {
                repeat(itemsPerThread) { item ->
                    queue.enqueue("user-$threadNum-$item")
                }
            }
        }
        
        enqueueThreads.forEach { it.start() }
        enqueueThreads.forEach { it.join() }
        
        // Then
        queue.size() shouldBe (threads * itemsPerThread)
        
        // Dequeue all items
        var count = 0
        while (!queue.isEmpty()) {
            queue.dequeue()
            count++
        }
        count shouldBe (threads * itemsPerThread)
    }

    test("WebhookQueue dequeue returns null when empty") {
        // Given
        val queue = WebhookQueue()
        
        // When & Then
        queue.isEmpty() shouldBe true
        queue.dequeue() shouldBe null
        queue.size() shouldBe 0
    }
})
