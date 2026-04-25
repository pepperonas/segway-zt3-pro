package com.celox.segway.core.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP client for the cfw.sh repos.
 *
 * Important: Some endpoints geo-block EU IPs (see the unlock plan). When this
 * client gets a 403 / connection-refused, surface it to the UI so the user can
 * turn on a non-EU VPN before retrying.
 *
 * See [SHU ANALYSIS](https://github.com/pepperonas/segway-zt3-pro/blob/main/reverse-engineering/apps/shu/ANALYSIS.md)
 * for the protocol details.
 */
@Singleton
class CfwRepoClient @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor { Timber.tag("cfw-repo").d(it) }
            .setLevel(HttpLoggingInterceptor.Level.BASIC))
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "SegwayMobilityReborn/0.1 (Android)")
                .build()
            chain.proceed(req)
        }
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://apps-data.cfw.sh/")
        .client(http)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api: Api = retrofit.create(Api::class.java)

    /** All currently published SHFW (and stock-replacement) releases. */
    suspend fun releases(target: FirmwareTarget? = null): Result<List<FirmwareRelease>> = run {
        runCatching {
            val resp = api.releases(target?.name?.lowercase())
            resp.releases
        }.onFailure { Timber.w(it, "releases() failed (geo-block?)") }
    }

    /** Stream the binary content of a release, with progress reporting. */
    suspend fun downloadBinary(
        release: FirmwareRelease,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val url = release.downloadUrl
            ?: return@withContext Result.failure(IllegalStateException("Release has no downloadUrl"))
        runCatching {
            val req = Request.Builder().url(url).get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("empty body")
                val total = body.contentLength().takeIf { it > 0 }
                val out = java.io.ByteArrayOutputStream()
                val source = body.byteStream()
                val buf = ByteArray(16 * 1024)
                var read = 0L
                while (true) {
                    val n = source.read(buf)
                    if (n == -1) break
                    out.write(buf, 0, n)
                    read += n
                    onProgress(read, total)
                }
                out.toByteArray()
            }
        }
    }

    /** Bootstrap zip: model DB + vehicle assets. */
    suspend fun fetchBootstrapZip(): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://apps-content.cfw.sh/repo/v4/bootstrap.zip")
                .get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                resp.body?.bytes() ?: throw IOException("empty body")
            }
        }
    }

    private interface Api {
        /** v8/releases — optional target=vcu|mcu|shfw etc. filter. */
        @GET("shfw/v8/releases")
        suspend fun releases(@Query("target") target: String? = null): ReleasesResponse

        @Streaming
        @GET
        suspend fun fetch(@Url url: String): okhttp3.ResponseBody
    }
}
