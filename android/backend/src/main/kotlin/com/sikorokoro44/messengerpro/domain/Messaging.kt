package com.sikorokoro44.messengerpro.domain

enum class DeliveryState { PENDING, SENT, DELIVERED, FAILED }

enum class ReadState { UNREAD, READ }

/**
 * [clientNonce] is a stable idempotency key generated once per user action so a retry after a
 * network failure cannot create a duplicate message.
 */
data class Message(
    val id: String,
    val conversationId: String,
    val senderPhoneHash: String,
    val body: String,
    val createdAt: Long,
    val clientNonce: String,
    val delivery: DeliveryState = DeliveryState.PENDING,
    val read: ReadState = ReadState.UNREAD,
)

data class PageRequest(val cursor: String?, val limit: Int) {
    init {
        require(limit in 1..100) { "Page limit must be between 1 and 100" }
    }
}

data class Page<T>(val items: List<T>, val nextCursor: String?)

/** In-memory reference repository for CI. A deployed adapter must enforce the same invariants. */
class InMemoryMessageRepository {
    private val messages = mutableListOf<Message>()
    private val nonces = mutableSetOf<String>()

    @Synchronized
    fun append(message: Message): Boolean {
        if (!nonces.add(message.clientNonce)) return false
        messages += message
        return true
    }

    @Synchronized
    fun page(conversationId: String, request: PageRequest): Page<Message> {
        val ordered = messages
            .filter { it.conversationId == conversationId }
            .sortedByDescending { it.createdAt }
        val index = if (request.cursor == null) {
            0
        } else {
            val found = ordered.indexOfFirst { it.id == request.cursor }
            if (found < 0) 0 else found + 1
        }
        val items = ordered.drop(index).take(request.limit)
        val next = if (index + items.size < ordered.size) items.lastOrNull()?.id else null
        return Page(items, next)
    }

    @Synchronized
    fun transition(id: String, delivery: DeliveryState? = null, read: ReadState? = null) {
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = messages[index]
        messages[index] = current.copy(
            delivery = delivery ?: current.delivery,
            read = read ?: current.read,
        )
    }

    @Synchronized
    fun count(): Int = messages.size
}
