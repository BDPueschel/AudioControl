package com.audiocontrol.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class AudioRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: AudioRepository

    private val stateJson = """
      {"master_gain":-45.0,"mute":false,
       "mains":{"gain":0.0,"hpf":{"freq":80,"bypass":true,"type":"lr4"},"lpf":{"freq":120,"bypass":true,"type":"lr4"}},
       "subs":{"gain":4.0,"hpf":{"freq":45,"bypass":false,"type":"lr4"},"lpf":{"freq":200,"bypass":false,"type":"lr4"}}}
    """.trimIndent()

    @Before fun setUp() {
        server = MockWebServer(); server.start()
        val api = buildApi(server.url("/").toString())
        repo = AudioRepository { api }
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun state_parsesDspState() = runTest {
        server.enqueue(MockResponse().setBody(stateJson))
        val s = repo.state().getOrThrow()
        assertThat(s.master_gain).isEqualTo(-45.0)
        assertThat(s.subs.hpf.freq).isEqualTo(45)
        assertThat(s.subs.hpf.filterType.wire).isEqualTo("lr4")
    }
    @Test fun hpf_sendsTypeInBody() = runTest {
        server.enqueue(MockResponse().setBody(stateJson))
        repo.hpf("subs", freq = 50, bypass = null, type = "butter12").getOrThrow()
        val req = server.takeRequest()
        assertThat(req.path).isEqualTo("/api/subs/hpf")
        assertThat(req.body.readUtf8()).contains("butter12")
    }
    @Test fun networkError_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertThat(repo.state().isFailure).isTrue()
    }
}
