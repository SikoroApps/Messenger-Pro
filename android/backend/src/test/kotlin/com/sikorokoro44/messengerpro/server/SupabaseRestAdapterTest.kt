package com.sikorokoro44.messengerpro.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseRestAdapterTest {
    private companion object {
        const val SERVICE_KEY = "sb_secret_9f8c7d6b5a"
        const val LEGACY_KEY = "eyJhbGciOiJIUzI1NiJ9.legacy.service.role"
        const val REFRESH_TOKEN = "r.do-not-persist"
    }

    private class FakeTransport(
        responses: List<ProviderHttpResponse>,
    ) : ProviderHttpTransport {
        private val queue = responses.toMutableList()
        val requests = mutableListOf<ProviderHttpRequest>()

        override fun send(request: ProviderHttpRequest): ProviderHttpResponse {
            requests += request
            return if (queue.isEmpty()) {
                ProviderHttpResponse(200, "[]")
            } else {
                queue.removeAt(0)
            }
        }
    }

    private fun ok(body: String) = ProviderHttpResponse(200, body)

    @Test
    fun accountUpsertUsesRpcWithServiceKeyOnlyInApikeyHeader() {
        val transport = FakeTransport(
            listOf(
                ok("""[{"id":"acct-1","phone_e164":"+26876123456","state":"active","created_at":1000}]"""),
            ),
        )
        val repo = SupabaseAccountRepository(
            SupabaseRestClient("https://project.supabase.co", SERVICE_KEY, transport),
        )

        val account = repo.upsertByPhone("+26876123456", 1000L)

        assertEquals("acct-1", account.id)
        val request = transport.requests.single()
        assertTrue(request.url.endsWith("/rest/v1/rpc/mp_upsert_account"))
        assertEquals("POST", request.method)
        assertEquals(SERVICE_KEY, request.headers["apikey"])
        assertFalse(request.headers.containsKey("Authorization"))
        assertTrue(request.body.contains("+26876123456"))
        assertFalse(request.body.contains(SERVICE_KEY))
    }

    @Test
    fun legacyJwtServiceRoleKeyAlsoSendsBearerAuthorization() {
        val transport = FakeTransport(listOf(ok("[{\"id\":\"acct-1\"}]")))
        SupabaseAccountRepository(
            SupabaseRestClient("https://project.supabase.co", LEGACY_KEY, transport),
        ).count()

        val request = transport.requests.single()
        assertEquals(LEGACY_KEY, request.headers["apikey"])
        assertEquals("Bearer $LEGACY_KEY", request.headers["Authorization"])
    }

    @Test
    fun providerOtpConsumeIsConditionalAndRequiresAnAffectedRow() {
        val transport = FakeTransport(listOf(ok("[{\"id\":\"req-1\"}]"), ok("[]")))
        val repo = SupabaseProviderOtpRepository(
            SupabaseRestClient("https://project.supabase.co", SERVICE_KEY, transport),
        )

        assertTrue(repo.consume("req-1", 2000L))
        assertFalse(repo.consume("req-1", 2001L))

        val request = transport.requests.first()
        assertEquals("PATCH", request.method)
        assertTrue(request.url.contains("consumed_at=is.null"))
        assertTrue(request.url.contains("failed_at=is.null"))
        assertTrue(request.body.contains("2000"))
        assertEquals(SERVICE_KEY, request.headers["apikey"])
    }

    @Test
    fun sessionRepositoryPersistsOnlyRefreshHash() {
        val transport = FakeTransport(listOf(ok("[{\"id\":\"session-1\"}]"), ok("[]")))
        val client = SupabaseRestClient("https://project.supabase.co", SERVICE_KEY, transport)
        val repo = SupabaseSessionRepository(client)
        val session = Session(
            id = "session-1",
            accountId = "acct-1",
            refreshHash = client.encode(REFRESH_TOKEN),
            expiresAt = 5000L,
            createdAt = 1000L,
        )

        repo.save(session)
        repo.markConsumed(session.refreshHash, "acct-1")

        val save = transport.requests[0]
        val consume = transport.requests[1]
        assertFalse(save.body.contains(REFRESH_TOKEN))
        assertTrue(save.body.contains("refresh_hash"))
        assertFalse(consume.body.contains(REFRESH_TOKEN))
        assertTrue(consume.url.contains("on_conflict=refresh_hash"))
    }

    @Test
    fun errorsAndConfigurationFailuresDoNotExposeTheServiceKey() {
        val transport = FakeTransport(
            listOf(ProviderHttpResponse(401, """{"message":"bad key $SERVICE_KEY"}""")),
        )
        val client = SupabaseRestClient("https://project.supabase.co", SERVICE_KEY, transport)
        val thrown = try {
            client.execute("/rest/v1/accounts")
            throw AssertionError("Expected SupabaseException")
        } catch (e: SupabaseException) {
            e
        }
        assertEquals(SupabaseFailure.UNAUTHORIZED, thrown.failure)
        assertEquals("UNAUTHORIZED", thrown.message)
        assertFalse(thrown.message!!.contains(SERVICE_KEY))

        val configFailure = try {
            SupabaseRestClient("http://project.supabase.co", SERVICE_KEY, transport)
            throw AssertionError("Expected configuration rejection")
        } catch (e: IllegalArgumentException) {
            e
        }
        assertFalse(configFailure.message!!.contains(SERVICE_KEY))
    }

    @Test
    fun httpsTransportRefusesOtherSupabaseHosts() {
        val transport = SupabaseHttpsTransport("https://project.supabase.co")
        val failure = try {
            transport.send(
                ProviderHttpRequest(
                    method = "GET",
                    url = "https://other.supabase.co/rest/v1/accounts",
                    headers = mapOf("apikey" to SERVICE_KEY),
                    body = "",
                ),
            )
            throw AssertionError("Expected host rejection")
        } catch (e: IllegalArgumentException) {
            e
        }
        assertNotNull(failure.message)
        assertFalse(failure.message!!.contains(SERVICE_KEY))
    }
}
