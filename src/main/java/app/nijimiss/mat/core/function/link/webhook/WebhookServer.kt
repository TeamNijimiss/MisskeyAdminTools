/*
 * Copyright 2024 Nafu Satsuki
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

package app.nijimiss.mat.core.function.link.webhook

import app.nijimiss.mat.MisskeyAdminTools
import app.nijimiss.mat.core.function.link.DiscordMisskeyAccountLinker
import app.nijimiss.mat.core.requests.ApiRequestManager
import app.nijimiss.mat.core.requests.ApiResponse
import app.nijimiss.mat.core.requests.ApiResponseHandler
import app.nijimiss.mat.core.requests.misskey.endpoints.notes.Create
import app.nijimiss.mat.database.AccountsStore
import app.nijimiss.mat.entities.Note
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.security.SecureRandom

class WebhookServer(
    private val accountLinker: DiscordMisskeyAccountLinker,
    private val accountsStore: AccountsStore,
    private val requestManager: ApiRequestManager
) {
    data class WebhookRequest(
        val server: String,
        val hookId: String,
        val userId: String,
        val eventId: String,
        val createdAt: Long,
        val type: String,
        val body: Body
    )

    data class Body(
        val note: Note
    )

    init {
        Thread { start() }.start()
    }

    private fun start() {
        embeddedServer(Netty, port = 8080) {
            install(ContentNegotiation) {
                jackson {
                    // Customize Jackson ObjectMapper if needed
                    enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT)
                }
            }
            routing {
                post("/webhook") {
                    val request = call.receive<WebhookRequest>()
                    request.body.note.text

                    if (request.body.note.text?.contains("link") == true) {
                        val verifyCode = generateRandomString(8)
                        var responseMessage = ""
                        if (request.body.note.user == null) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@post
                        }

                        if (request.body.note.user.host != null) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@post
                        }

                        val updatedTime =
                            request.body.note.user.id?.let { it1 -> accountsStore.getUpdatedTime(it1) }
                        // check last update before 30 days
                        MisskeyAdminTools.getInstance().moduleLogger.info("Updated Time: $updatedTime, Current Time: ${System.currentTimeMillis()}, Diff: ${System.currentTimeMillis() - updatedTime!!}")
                        if (updatedTime != null && System.currentTimeMillis() - updatedTime < 2592000000) {
                            responseMessage =
                                "最後の紐付けから30日間は再度紐付けを行うことができません。 / If you link within 30 days, you cannot link again."
                        } else {
                            accountLinker.onWaitingLink(request.body.note.user, verifyCode)
                            responseMessage = "Please enter the following code to link your account: $verifyCode"
                        }

                        val createNote = Create(
                            Create.Visibility.SPECIFIED,
                            arrayOf(request.body.note.user.id),
                            responseMessage,
                            null,
                            request.body.note.id,
                            null,
                            null,
                            null,
                            false,
                            false,
                            false,
                            false
                        )
                        requestManager.addRequest(createNote, object : ApiResponseHandler {
                            override fun onSuccess(response: ApiResponse?) {

                            }

                            override fun onFailure(response: ApiResponse?) {
                            }
                        })
                    }

                    call.respond(HttpStatusCode.OK)
                }
            }
        }.start(wait = true)
    }

    fun generateRandomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val secureRandom = SecureRandom()
        return (1..length)
            .map { chars[secureRandom.nextInt(chars.length)] }
            .joinToString("")
    }
}
