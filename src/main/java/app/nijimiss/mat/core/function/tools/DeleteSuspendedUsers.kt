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

package app.nijimiss.mat.core.function.tools

import app.nijimiss.mat.MisskeyAdminTools
import app.nijimiss.mat.api.misskey.FullUser
import app.nijimiss.mat.core.requests.ApiRequestManager
import app.nijimiss.mat.core.requests.ApiResponse
import app.nijimiss.mat.core.requests.ApiResponseHandler
import app.nijimiss.mat.core.requests.misskey.elements.Origin
import app.nijimiss.mat.core.requests.misskey.elements.State
import app.nijimiss.mat.core.requests.misskey.endpoints.admin.DeleteAccount
import app.nijimiss.mat.core.requests.misskey.endpoints.admin.ShowUsers
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import net.dv8tion.jda.api.entities.Role
import page.nafuchoco.neobot.api.command.CommandContext
import page.nafuchoco.neobot.api.command.CommandExecutor
import java.text.SimpleDateFormat


class DeleteSuspendedUsers(
    private val requestManager: ApiRequestManager,
) : CommandExecutor("deletesuspendedusers") {
    private val superUserRoleIds =
        MisskeyAdminTools.getInstance().config.authentication?.superUserRoleIds ?: emptyList()

    override fun onInvoke(context: CommandContext) {
        if (!superUserRoleIds.stream().anyMatch { o: Long ->
                context.invoker
                    .roles.map { role: Role -> role.idLong }
                    .contains(o)
            }) {
            context.responseSender.sendMessage(
                """
                        あなたはこのアクションを実行する権限を持っていません。
                        You do not have permission to perform this action.
                        """.trimIndent()
            ).setEphemeral(true).queue()
            return
        }

        context.responseSender.sendMessage("処理を開始します。").setEphemeral(true).queue()

        val suspendedUsers: MutableList<FullUser> = mutableListOf()
        do {
            var results = 0
            val showUsers = ShowUsers(null, 10, suspendedUsers.size, "-createdAt", Origin.LOCAL, State.SUSPENDED, null)
            requestManager.addRequest(showUsers, object : ApiResponseHandler {
                override fun onSuccess(response: ApiResponse?) {
                    val users: List<FullUser> =
                        MAPPER.readValue(response!!.body, object : TypeReference<List<FullUser>>() {})
                    suspendedUsers += users
                    results = users.size
                }

                override fun onFailure(response: ApiResponse?) {
                    context.responseSender.sendMessage("APIリクエストに失敗しました。").queue()
                }
            }).join()
        } while (results == 10)

        // filter last active date is 6 months ago
        val sixMonthsAgo = System.currentTimeMillis() - 15552000000
        val filteredUsers =
            suspendedUsers.filter { (if (it.updatedAt != null) dateFormat.parse(it.updatedAt).time else 0) < sixMonthsAgo }

        // delete users
        filteredUsers.forEach {
            requestManager.addRequest(DeleteAccount(it.id!!), object : ApiResponseHandler {
                override fun onSuccess(response: ApiResponse?) {
                    context.responseSender.sendMessage("アカウント ${it.id} を削除しました。").queue()
                }

                override fun onFailure(response: ApiResponse?) {
                    context.responseSender.sendMessage("APIリクエストに失敗しました。").queue()
                }
            }).join()
        }
    }

    override fun getDescription(): String {
        return "6ヶ月以上前にアカウントを凍結されたユーザーを削除します。"
    }


    companion object {
        private val MAPPER = ObjectMapper()
        var dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    }
}
