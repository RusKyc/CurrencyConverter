package com.currencyconverter.app.data

import com.currencyconverter.app.data.remote.ApiKeyInterceptor
import com.currencyconverter.app.data.remote.ExchangeRateApi
import com.currencyconverter.app.data.remote.ExchangeRateApiRemoteDataSource
import com.currencyconverter.app.domain.model.RatesError
import com.currencyconverter.app.domain.model.RatesException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class ExchangeRateApiRemoteDataSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: ExchangeRateApiRemoteDataSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dataSource = ExchangeRateApiRemoteDataSource(buildApi(apiKey = ""))
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun buildApi(apiKey: String, readTimeoutMs: Long = 2_000): ExchangeRateApi {
        val client = OkHttpClient.Builder()
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .connectTimeout(2, TimeUnit.SECONDS)
            .addInterceptor(ApiKeyInterceptor("X-Api-Key", apiKey))
            .build()
        return Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ExchangeRateApi::class.java)
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private suspend fun assertFails(expected: RatesError) {
        try {
            dataSource.fetchLatest("EUR")
            fail("Expected $expected")
        } catch (e: RatesException) {
            assertEquals(expected, e.error)
        }
    }

    @Test
    fun `parses a real response keeping exact decimals`() = runTest {
        server.enqueue(
            json(
                """{"result":"success","base_code":"EUR","time_last_update_utc":"Mon, 28 Sep 2026 00:02:31 +0000",""" +
                    """"rates":{"USD":1.138492,"RUB":96.061131,"JPY":168.4}}""",
            ),
        )

        val rates = dataSource.fetchLatest("EUR")

        assertEquals("EUR", rates.base)
        assertEquals(LocalDate.of(2026, 9, 28), rates.date)
        assertEquals(BigDecimal("1.138492"), rates.quotes["USD"])
        assertEquals("96.061131", rates.quotes["RUB"]!!.toPlainString())
        assertEquals("168.4", rates.quotes["JPY"]!!.toPlainString())
    }

    @Test
    fun `sends the requested base in the path`() = runTest {
        server.enqueue(json("""{"result":"success","base_code":"EUR","rates":{"USD":1.1}}"""))

        dataSource.fetchLatest("EUR")

        val request = server.takeRequest()
        assertEquals("/v6/latest/EUR", request.url.encodedPath)
    }

    @Test
    fun `missing update time is tolerated`() = runTest {
        server.enqueue(json("""{"result":"success","base_code":"EUR","rates":{"USD":1.1}}"""))
        assertNull(dataSource.fetchLatest("EUR").date)
    }

    @Test
    fun `an unparseable update time is tolerated`() = runTest {
        server.enqueue(json("""{"result":"success","base_code":"EUR","time_last_update_utc":"not a date","rates":{"USD":1.1}}"""))
        assertNull(dataSource.fetchLatest("EUR").date)
    }

    @Test
    fun `falls back to the requested base if base_code is missing`() = runTest {
        server.enqueue(json("""{"result":"success","rates":{"USD":1.1}}"""))
        assertEquals("EUR", dataSource.fetchLatest("eur").base)
    }

    @Test
    fun `unknown fields are ignored`() = runTest {
        server.enqueue(json("""{"result":"success","base_code":"EUR","documentation":"x","rates":{"USD":1.1}}"""))
        assertEquals(1, dataSource.fetchLatest("EUR").quotes.size)
    }

    @Test
    fun `a provider level error result is an api error`() = runTest {
        server.enqueue(json("""{"result":"error","error-type":"unsupported-code"}"""))
        assertFails(RatesError.Api(null))
    }

    @Test
    fun `server errors are api errors with the status code`() = runTest {
        server.enqueue(json("""{"message":"oops"}""", code = 500))
        assertFails(RatesError.Api(500))
    }

    @Test
    fun `client errors are api errors too`() = runTest {
        server.enqueue(json("""{"message":"not found"}""", code = 404))
        assertFails(RatesError.Api(404))
    }

    @Test
    fun `rate limiting is an api error`() = runTest {
        server.enqueue(json("{}", code = 429))
        assertFails(RatesError.Api(429))
    }

    @Test
    fun `broken json is a parsing error`() = runTest {
        server.enqueue(json("""{"result":"success","rates":{"USD":"""))
        assertFails(RatesError.Parsing)
    }

    @Test
    fun `html error page with 200 is a parsing error`() = runTest {
        server.enqueue(json("<html>maintenance</html>"))
        assertFails(RatesError.Parsing)
    }

    @Test
    fun `missing rates object is a parsing error`() = runTest {
        server.enqueue(json("""{"result":"success","base_code":"EUR"}"""))
        assertFails(RatesError.Parsing)
    }

    @Test
    fun `empty rates are a parsing error`() = runTest {
        server.enqueue(json("""{"result":"success","base_code":"EUR","rates":{}}"""))
        assertFails(RatesError.Parsing)
    }

    @Test
    fun `non numeric rate is a parsing error`() = runTest {
        server.enqueue(json("""{"result":"success","base_code":"EUR","rates":{"USD":"abc"}}"""))
        assertFails(RatesError.Parsing)
    }

    @Test
    fun `null rate is a parsing error`() = runTest {
        server.enqueue(json("""{"result":"success","base_code":"EUR","rates":{"USD":null}}"""))
        assertFails(RatesError.Parsing)
    }

    @Test
    fun `zero or negative rate is a parsing error`() = runTest {
        server.enqueue(json("""{"result":"success","base_code":"EUR","rates":{"USD":0}}"""))
        assertFails(RatesError.Parsing)
        server.enqueue(json("""{"result":"success","base_code":"EUR","rates":{"USD":-1.2}}"""))
        assertFails(RatesError.Parsing)
    }

    @Test
    fun `empty body is a parsing error`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("").build())
        assertFails(RatesError.Parsing)
    }

    @Test
    fun `dropped connection is a network error`() = runTest {
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        assertFails(RatesError.Network)
    }

    @Test
    fun `unreachable server is a network error`() = runTest {
        val api = buildApi(apiKey = "")
        server.close()
        val result = runCatching { ExchangeRateApiRemoteDataSource(api).fetchLatest("EUR") }
        assertTrue((result.exceptionOrNull() as RatesException).error == RatesError.Network)
    }

    @Test
    fun `timeout is a network error`() = runTest {
        dataSource = ExchangeRateApiRemoteDataSource(buildApi(apiKey = "", readTimeoutMs = 300))
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"result":"success","base_code":"EUR","rates":{"USD":1.1}}""")
                .onResponseStart(SocketEffect.Stall)
                .build(),
        )
        assertFails(RatesError.Network)
    }

    @Test
    fun `api key header is sent only when configured`() = runTest {
        server.enqueue(json("""{"result":"success","base_code":"EUR","rates":{"USD":1.1}}"""))
        dataSource.fetchLatest("EUR")
        assertNull(server.takeRequest().headers["X-Api-Key"])

        server.enqueue(json("""{"result":"success","base_code":"EUR","rates":{"USD":1.1}}"""))
        ExchangeRateApiRemoteDataSource(buildApi(apiKey = "secret")).fetchLatest("EUR")
        assertEquals("secret", server.takeRequest().headers["X-Api-Key"])
    }
}
