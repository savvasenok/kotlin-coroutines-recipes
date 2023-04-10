import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime

import kotlinx.coroutines.test.runTest
import recipes.retryBackoff
import kotlin.random.Random

import kotlin.test.Test

import kotlin.test.assertEquals
import kotlin.time.Duration

import kotlin.time.Duration.Companion.seconds

class RetryBackoffFlowTest {
    
    private fun failingNTimesFlow(failingTimes: Int) : Flow<String> {
        var attempt = 0
        return flow {
            when (attempt++) {
                in 0 until failingTimes -> throw TestException("Error$attempt")
                else -> emit("ABC")
            }
        }
    }
    
    @Test
    fun `should retry single failing call`() = runTest {
        val error = object : Throwable() {}
        var first = true
        flow {
            if (first) {
                first = false
                throw error
            } else {
                emit("ABC")
            }
        }.testBackoffRetry(
            testScope = this,
            minDelay = 1.seconds,
            maxDelay = 5.seconds,
            maxAttempts = 11,
            expectResult = Result.success(listOf("ABC")),
            expectRetriesExhaustedCalls = listOf(),
            expectBeforeRetryCalls = listOf(
                BeforeRetryCall(error, 0, 0, 0),
            )
        )
    }
    
    @Test
    fun `should retry multiple times with growing backoff`() = runTest {
        failingNTimesFlow(10).testBackoffRetry(
            testScope = this,
            minDelay = 1.seconds,
            maxAttempts = 11,
            jitterFactor = 0.0,
            expectResult = Result.success(listOf("ABC")),
            expectRetriesExhaustedCalls = listOf(), // Should not call retriesExhausted when maxAttempts is not reached
            expectBeforeRetryCalls = listOf(
                BeforeRetryCall(TestException("Error1"), 0, 0, 0),
                BeforeRetryCall(TestException("Error2"), 1, 1, 1000),
                BeforeRetryCall(TestException("Error3"), 2, 2, 2000 + 1000),
                BeforeRetryCall(TestException("Error4"), 3, 3, 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error5"), 4, 4, 8000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error6"), 5, 5, 16000 + 8000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error7"), 6, 6, 32000 + 16000 + 8000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error8"), 7, 7,  64000 + 32000 + 16000 + 8000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error9"), 8, 8, 128000 + 64000 + 32000 + 16000 + 8000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error10"), 9, 9, 256000 + 128000 + 64000 + 32000 + 16000 + 8000 + 4000 + 2000 + 1000),
            )
        )
    }
    
    @Test
    fun `should calculate only following calls for max attempts`() = runTest {
        var attempt = 0
        flow {
            while (true) {
                when (attempt++ % 4) {
                    3 -> emit("Result$attempt")
                    else -> throw TestException("Call$attempt")
                }
            }
        }.testBackoffRetry(
            testScope = this,
            elementsToExpect = 3,
            minDelay = 1.seconds,
            maxAttempts = 4,
            jitterFactor = 0.0,
            expectResult = Result.success(listOf("Result4", "Result8", "Result12")),
            expectRetriesExhaustedCalls = listOf(),
            expectBeforeRetryCalls = listOf(
                BeforeRetryCall(TestException("Call1"), 0, 0, 0),
                BeforeRetryCall(TestException("Call2"), 1, 1, 1000),
                BeforeRetryCall(TestException("Call3"), 2, 2, 2000 + 1000),
                BeforeRetryCall(TestException("Call5"), 0, 3, 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call6"), 1, 4, 1000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call7"), 2, 5, 2000 + 1000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call9"), 0, 6, 4000 + 2000 + 1000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call10"), 1, 7, 1000 + 4000 + 2000 + 1000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call11"), 2, 8, 2000 + 1000 + 4000 + 2000 + 1000 + 4000 + 2000 + 1000),
            )
        )
    }
    
    @Test
    fun `should calculate all calls for non-transient`() = runTest {
        var attempt = 0
        flow {
            while (true) {
                when (attempt++ % 4) {
                    3 -> emit("Result$attempt")
                    else -> throw TestException("Call$attempt")
                }
            }
        }.testBackoffRetry(
            testScope = this,
            elementsToExpect = 3,
            minDelay = 1.seconds,
            transient = false,
            jitterFactor = 0.0,
            expectResult = Result.success(listOf("Result4", "Result8", "Result12")),
            expectRetriesExhaustedCalls = listOf(),
            expectBeforeRetryCalls = listOf(
                BeforeRetryCall(TestException("Call1"), 0, 0, 0),
                BeforeRetryCall(TestException("Call2"), 1, 1, 1000),
                BeforeRetryCall(TestException("Call3"), 2, 2, 2000 + 1000),
                BeforeRetryCall(TestException("Call5"), 3, 3, 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call6"), 4, 4, 8000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call7"), 5, 5, 16000 + 8000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call9"), 6, 6, 32000 + 16000 + 8000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call10"), 7, 7, 64000 + 32000 + 16000 + 8000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call11"), 8, 8,128000 + 64000 + 32000 + 16000 + 8000 + 4000 + 2000 + 1000),
            )
        )
    }
    
    @Test
    fun `should use backoff factor`() = runTest {
        failingNTimesFlow(10).testBackoffRetry(
            testScope = this,
            minDelay = 1.seconds,
            backoffFactor = 3.0,
            maxAttempts = 5,
            jitterFactor = 0.0,
            expectResult = Result.failure(TestException("Error6")),
            expectRetriesExhaustedCalls = listOf(
                RetryExhaustedCall(TestException("Error6"), 121000)
            ),
            expectBeforeRetryCalls = listOf(
                BeforeRetryCall(TestException("Error1"), 0, 0, 0),
                BeforeRetryCall(TestException("Error2"), 1, 1, 1000),
                BeforeRetryCall(TestException("Error3"), 2, 2, 3000 + 1000),
                BeforeRetryCall(TestException("Error4"), 3, 3, 9000 + 3000 + 1000),
                BeforeRetryCall(TestException("Error5"), 4, 4, 27000 + 9000 + 3000 + 1000),
            )
        )
    }
    
    @Test
    fun `should stop backoff growing once max reached`() = runTest {
        failingNTimesFlow(10).testBackoffRetry(
            testScope = this,
            minDelay = 1.seconds,
            maxDelay = 5.seconds,
            maxAttempts = 11,
            jitterFactor = 0.0,
            expectResult = Result.success(listOf("ABC")),
            expectRetriesExhaustedCalls = listOf(), // Should not call retriesExhausted when maxAttempts is not reached
            expectBeforeRetryCalls = listOf(
                BeforeRetryCall(TestException("Error1"), 0, 0, 0),
                BeforeRetryCall(TestException("Error2"), 1, 1, 1000),
                BeforeRetryCall(TestException("Error3"), 2, 2, 2000 + 1000),
                BeforeRetryCall(TestException("Error4"), 3, 3, 4000 + 2000 + 1000),
                // Delay is random due to jitter, but once it reached maxDelay it should be around this value
                BeforeRetryCall(TestException("Error5"), 4, 4, 5000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error6"), 5, 5, 2 * 5000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error7"), 6, 6, 3 * 5000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error8"), 7, 7, 4 * 5000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error9"), 8, 8, 5 * 5000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error10"), 9, 9, 6 * 5000 + 4000 + 2000 + 1000),
            )
        )
    }
    
    @Test
    fun `should retry until max attempts reached`() = runTest {
        failingNTimesFlow(10).testBackoffRetry(
            testScope = this,
            minDelay = 2.seconds,
            maxDelay = 10.seconds,
            maxAttempts = 4,
            jitterFactor = 0.0,
            expectResult = Result.failure(TestException("Error5")),
            expectRetriesExhaustedCalls = listOf(
                RetryExhaustedCall(TestException("Error5"), 24000),
            ),
            expectBeforeRetryCalls = listOf(
                BeforeRetryCall(TestException("Error1"), 0, 0, 0),
                BeforeRetryCall(TestException("Error2"), 1, 1, 2000),
                BeforeRetryCall(TestException("Error3"), 2, 2, 6000),
                BeforeRetryCall(TestException("Error4"), 3, 3, 14000),
            )
        )
    }
    
    @Test
    fun `should add random jitter`() = runTest {
        failingNTimesFlow(10).testBackoffRetry(
            testScope = this,
            minDelay = 1.seconds,
            maxDelay = 5.seconds,
            maxAttempts = 10,
            jitterFactor = 0.5,
            random = Random(12345),
            expectResult = Result.success(listOf("ABC")),
            expectRetriesExhaustedCalls = listOf(),
            expectBeforeRetryCalls = listOf(
                BeforeRetryCall(TestException("Error1"), 0, 0, 0),
                BeforeRetryCall(TestException("Error2"), 1, 1, 1270),
                BeforeRetryCall(TestException("Error3"), 2, 2, 3941),
                BeforeRetryCall(TestException("Error4"), 3, 3, 8560),
                BeforeRetryCall(TestException("Error5"), 4, 4, 15151),
                BeforeRetryCall(TestException("Error6"), 5, 5, 20908),
                BeforeRetryCall(TestException("Error7"), 6, 6, 25383),
                BeforeRetryCall(TestException("Error8"), 7, 7, 31701),
                BeforeRetryCall(TestException("Error9"), 8, 8, 36902),
                BeforeRetryCall(TestException("Error10"), 9, 9, 43791),
            )
        )
    }
    
    suspend fun <T> Flow<T>.testBackoffRetry(
        testScope: TestScope,
        minDelay: Duration,
        maxDelay: Duration = Duration.INFINITE,
        backoffFactor: Double = 2.0,
        maxAttempts: Int = Int.MAX_VALUE,
        transient: Boolean = true,
        jitterFactor: Double = 0.1,
        random: Random = Random,
        expectResult: Result<List<T>>,
        expectRetriesExhaustedCalls: List<RetryExhaustedCall>,
        expectBeforeRetryCalls: List<BeforeRetryCall>,
        elementsToExpect: Int = 1,
    ) {
        var beforeRetryCalls = listOf<BeforeRetryCall>()
        var retriesExhaustedCalls = listOf<RetryExhaustedCall>()
        val result = runCatching {
            this.retryBackoff(
                minDelay = minDelay,
                maxDelay = maxDelay,
                maxAttempts = maxAttempts,
                backoffFactor = backoffFactor,
                transient = transient,
                jitterFactor = jitterFactor,
                random = random,
                beforeRetry = { cause, currentAttempts, totalAttempts ->
                    beforeRetryCalls += BeforeRetryCall(cause, currentAttempts, totalAttempts, testScope.currentTime)
                },
                retriesExhausted = {
                    retriesExhaustedCalls += RetryExhaustedCall(it, testScope.currentTime)
                }
            ).take(elementsToExpect)
                .toList()
        }
        assertEquals(expectResult, result)
        assertEquals(expectRetriesExhaustedCalls, retriesExhaustedCalls)
        assertEquals(expectBeforeRetryCalls, beforeRetryCalls)
    }
    
    data class BeforeRetryCall(val cause: Throwable, val currentAttempts: Int, val totalAttempts: Int, val time: Long)
    data class RetryExhaustedCall(val cause: Throwable, val time: Long)
    
    data class TestException(override val message: String) : Exception(message)
}