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

package app.nijimiss.mat.core.function.link

import app.nijimiss.mat.MisskeyAdminTools
import app.nijimiss.mat.api.misskey.FullUser
import app.nijimiss.mat.core.database.AccountsStore
import app.nijimiss.mat.core.database.MATSystemDataStore
import app.nijimiss.mat.core.requests.ApiRequestManager
import app.nijimiss.mat.core.requests.ApiResponse
import app.nijimiss.mat.core.requests.ApiResponseHandler
import app.nijimiss.mat.core.requests.misskey.endpoints.users.Show
import com.fasterxml.jackson.databind.ObjectMapper
import net.dv8tion.jda.api.interactions.commands.OptionType
import page.nafuchoco.neobot.api.command.CommandContext
import page.nafuchoco.neobot.api.command.CommandExecutor
import page.nafuchoco.neobot.api.command.CommandValueOption
import page.nafuchoco.neobot.api.module.NeoModuleLogger


class DiscordMisskeyAccountLinker(
    private val systemStore: MATSystemDataStore,
    private val accountsStore: AccountsStore,
    private val requestManager: ApiRequestManager,
    private val token: String,
) : CommandExecutor("link") {
    private val logger: NeoModuleLogger = MisskeyAdminTools.getInstance().moduleLogger
    private val handlers: MutableList<LinkerHandler> = mutableListOf()

    init {
        options.add(
            CommandValueOption(
                OptionType.STRING,
                "username",
                "Misskeyのユーザー名 / Username of Misskey (ex: nafu_at)",
                true,
                false
            )
        )
    }

    fun registerHandler(handler: LinkerHandler) {
        handlers.add(handler)
    }

    override fun onInvoke(context: CommandContext) {
        var username = context.options["username"]!!.value as String

        // username start with @ -> remove @
        if (username.startsWith("@")) username = username.substring(1)

        val userShow = Show(token, username, Show.SearchType.USERNAME)
        requestManager.addRequest(userShow, object : ApiResponseHandler {
            override fun onSuccess(response: ApiResponse?) {
                val user = MAPPER.readValue(response!!.body, FullUser::class.java)

                if (user.id != null) {
                    accountsStore.addAccount(context.invoker.idLong, user.id)
                    handlers.forEach { it.onLink(context.invoker.idLong, user.id) }
                } else {
                    handlers.forEach { it.onLinkFailed(context.invoker.idLong) }
                    context.responseSender.sendMessage("ユーザーが見つかりませんでした。 / User not found.").queue()
                    logger.error("Failed to get user.")
                }
            }

            override fun onFailure(response: ApiResponse?) {
                handlers.forEach { it.onLinkFailed(context.invoker.idLong) }
                context.responseSender.sendMessage("ユーザー情報の取得に失敗しました。 / Failed to get user information.").queue()
                logger.error("Failed to get user")
            }
        })
    }

    override fun getDescription(): String {
        return "DiscordアカウントとMisskeyアカウントを紐付けます。 / Link Discord account and Misskey account."
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}