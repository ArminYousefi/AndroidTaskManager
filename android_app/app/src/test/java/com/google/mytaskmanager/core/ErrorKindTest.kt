package com.google.mytaskmanager.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests for error classification and the user-facing messages. */
class ErrorKindTest {
    @Test fun httpStatusesMapToKinds() {
        assertEquals(ErrorKind.VALIDATION, ErrorKind.fromHttp(400))
        assertEquals(ErrorKind.UNAUTHORIZED, ErrorKind.fromHttp(401))
        assertEquals(ErrorKind.FORBIDDEN, ErrorKind.fromHttp(403))
        assertEquals(ErrorKind.NOT_FOUND, ErrorKind.fromHttp(404))
        assertEquals(ErrorKind.CONFLICT, ErrorKind.fromHttp(409))
        assertEquals(ErrorKind.RATE_LIMITED, ErrorKind.fromHttp(429))
        assertEquals(ErrorKind.SERVER, ErrorKind.fromHttp(503))
        assertEquals(ErrorKind.UNKNOWN, ErrorKind.fromHttp(418))
    }

    @Test fun onlyTransientFailuresAreQueuedForRetry() {
        assertTrue(ErrorKind.NETWORK.retryable && ErrorKind.TIMEOUT.retryable && ErrorKind.SERVER.retryable)
        assertFalse(ErrorKind.FORBIDDEN.retryable || ErrorKind.VALIDATION.retryable || ErrorKind.NOT_FOUND.retryable || ErrorKind.UNAUTHORIZED.retryable)
    }

    @Test fun onlyBugsAreReportedToDevelopers() {
        assertTrue(ErrorKind.SERVER.reportable && ErrorKind.UNKNOWN.reportable)
        assertFalse(ErrorKind.VALIDATION.reportable || ErrorKind.NETWORK.reportable || ErrorKind.FORBIDDEN.reportable)
    }

    @Test fun userMessagesPreferTheServerTextWhereTheUserCanFixIt() {
        assertEquals("Current password is incorrect", ErrorMessages.userMessage(ErrorKind.VALIDATION, "Current password is incorrect"))
        assertTrue(ErrorMessages.userMessage(ErrorKind.VALIDATION, null).isNotBlank())
        // never leak server internals for 5xx
        assertEquals(ErrorMessages.userMessage(ErrorKind.SERVER, null), ErrorMessages.userMessage(ErrorKind.SERVER, "NullPointerException at X"))
    }
}
