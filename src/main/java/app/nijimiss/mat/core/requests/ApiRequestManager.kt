/*
 * Copyright 2023 Nafu Satsuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.nijimiss.mat.core.requests

import app.nijimiss.mat.MisskeyAdminTools
import devcsrj.okhttp3.logging.HttpLoggingInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit


class ApiRequestManager(apiHostName: String) {
    private val apiHostName: String
    private val executor: ScheduledExecutorService
    private val logging: HttpLoggingInterceptor
    private val httpClient: OkHttpClient
    private val requestQueues: Queue<ApiRequestQueue>

    init {
        this.apiHostName = Objects.requireNonNull(apiHostName)
        executor = Executors.newSingleThreadScheduledExecutor()
        logging = HttpLoggingInterceptor(LoggerFactory.getLogger(this.javaClass))
        httpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
        requestQueues = ArrayDeque()
        executor.scheduleAtFixedRate({ execute() }, 0, 1500, TimeUnit.MILLISECONDS)
    }

    fun addRequest(request: ApiRequest, handler: ApiResponseHandler) {
        requestQueues.add(object : ApiRequestQueue {
            override val request: ApiRequest
                get() = request
            override val handler: ApiResponseHandler
                get() = handler
        })
    }

    fun shutdown() {
        executor.shutdownNow()
        try {
            executor.awaitTermination(1, TimeUnit.MINUTES)
        } catch (e: InterruptedException) {
            MisskeyAdminTools.getInstance().moduleLogger.error("An interruption occurred while waiting for the end.", e)
        }
    }

    private fun execute() {
        if (requestQueues.isEmpty()) return
        val requestQueue = requestQueues.poll()
        try {
            request(requestQueue)
        } catch (e: Exception) {
            if (e is IOException) {
                var retryCount = 0
                while (retryCount < 3) {
                    try {
                        Thread.sleep(1000 * retryCount.toLong())
                        request(requestQueue)
                        return
                    } catch (e1: Exception) {
                        retryCount++
                    }
                }
            }

            requestQueue.handler.onFailure(ApiResponse(requestQueue.request, 0, null))
            MisskeyAdminTools.getInstance().moduleLogger.warn(
                """
                Failed to execute request
                Executed requests: {}
                """.trimIndent(), requestQueue.request, e
            )
        }
    }

    @Throws(Exception::class)
    private fun request(requestQueue: ApiRequestQueue) {
        val request = requestQueue.request
        val handler = requestQueue.handler

        val httpRequest: Request = Request.Builder()
            .url("https://" + apiHostName + "/" + request.endpoint)
            .method(
                request.method.name,
                if (request.body == null) null else request.body!!
            )
            .build()

        httpClient.newCall(httpRequest).execute().use { httpResponse ->
            httpResponse.headers
            val response = ApiResponse(
                request,
                httpResponse.code,
                if (httpResponse.body != null) httpResponse.body!!.string() else null
            )
            if (response.statusCode == request.successCode) {
                handler.onSuccess(response)
            } else {
                handler.onFailure(response)
            }
        }
    }
}
