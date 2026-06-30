package com.audiocontrol.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface AudioApi {
    @GET("api/health") suspend fun getHealth(): Health
    @GET("api/state") suspend fun getState(): DspState
    @POST("api/master-gain") suspend fun setMasterGain(@Body body: GainBody): DspState
    @POST("api/mute") suspend fun setMute(@Body body: MuteBody): DspState
    @POST("api/{group}/gain") suspend fun setGain(@Path("group") group: String, @Body body: GainBody): DspState
    @POST("api/{group}/hpf") suspend fun setHpf(@Path("group") group: String, @Body body: FilterBody): DspState
    @POST("api/{group}/lpf") suspend fun setLpf(@Path("group") group: String, @Body body: FilterBody): DspState
    @POST("api/reset") suspend fun reset(): DspState
}

fun buildApi(baseUrl: String): AudioApi {
    val json = Json { ignoreUnknownKeys = true }
    val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()
    return Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AudioApi::class.java)
}
