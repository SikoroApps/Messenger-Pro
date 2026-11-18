package com.sikorokoro44.messengerpro.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainFoundationsTest {
    @Test
    fun contactMatchingUsesOnlyKeyedHashesAndHonoursConsent() {
        val policy = ContactMatchPolicy("lookup-pepper".toByteArray())
        val hashA = policy.hashPhone("+26876123456")
        val hashB = policy.hashPhone("+26876123457")
        assertFalse(hashA.contains("76123456"))
        assertFalse(hashA == hashB)

        val server = mapOf(hashA to "Ama", hashB to "Unauthorised")
        assertTrue(policy.match(listOf(hashA), server).map { it.displayName } == listOf("Ama"))
        assertTrue(policy.match(emptyList(), server).isEmpty())
        assertTrue(PrivacySettings().discoverByPhone.not())
    }

    @Test
    fun messageRetryIsIdempotent() {
        val repo = InMemoryMessageRepository()
        val message = Message("m1", "c1", "hash-a", "hello", 1L, "nonce-1")
        assertTrue(repo.append(message))
        assertFalse(repo.append(message.copy(id = "m2")))
        assertEquals(1, repo.count())
    }

    @Test
    fun messagePaginationIsStableAndBounded() {
        val repo = InMemoryMessageRepository()
        repeat(5) { i ->
            repo.append(Message("m$i", "c1", "hash-a", "body $i", i.toLong(), "nonce-$i"))
        }
        assertFalse(runCatching { PageRequest(null, 101) }.isSuccess)

        val first = repo.page("c1", PageRequest(null, 2))
        assertEquals(listOf("m4", "m3"), first.items.map { it.id })
        assertEquals("m3", first.nextCursor)

        val second = repo.page("c1", PageRequest(first.nextCursor, 2))
        assertEquals(listOf("m2", "m1"), second.items.map { it.id })

        val last = repo.page("c1", PageRequest(second.nextCursor, 2))
        assertEquals(listOf("m0"), last.items.map { it.id })
        assertNull(last.nextCursor)
    }

    @Test
    fun deliveryAndReadTransitionsAreIndependent() {
        val repo = InMemoryMessageRepository()
        repo.append(Message("m1", "c1", "hash-a", "hi", 1L, "n1"))
        repo.transition("m1", delivery = DeliveryState.DELIVERED)
        repo.transition("m1", read = ReadState.READ)
        val message = repo.page("c1", PageRequest(null, 1)).items.single()
        assertEquals(DeliveryState.DELIVERED, message.delivery)
        assertEquals(ReadState.READ, message.read)
    }

    @Test
    fun mediaPolicyRejectsOversizeTypeAndExtensionMismatch() {
        assertEquals(MediaValidation.ACCEPTED, MediaPolicy.validate("image/jpeg", 1024, "jpg"))
        assertEquals(MediaValidation.UNSUPPORTED_TYPE, MediaPolicy.validate("application/x-msdownload", 1024, "exe"))
        assertEquals(MediaValidation.INVALID_SIZE, MediaPolicy.validate("image/png", MediaPolicy.MAX_BYTES + 1, "png"))
        assertEquals(MediaValidation.INVALID_SIZE, MediaPolicy.validate("image/png", 0, "png"))
        assertEquals(MediaValidation.EXTENSION_MISMATCH, MediaPolicy.validate("image/png", 1024, "exe"))
        assertFalse(MediaPolicy.allowedMimeTypes.contains("text/html"))
    }

    @Test
    fun accountControlsKeepDeviceLockAsPolicyOnly() {
        val policy = AccountControlPolicy()
        assertEquals(AppLockMode.OFF, policy.lockMode)
        assertEquals(7, policy.exportRetentionDays)
        assertFalse(runCatching { AccountControlPolicy(exportRetentionDays = 0) }.isSuccess)
        assertEquals(DataExportStatus.QUEUED, DataExport("e1", DataExportStatus.QUEUED, 1L).status)
    }
}
