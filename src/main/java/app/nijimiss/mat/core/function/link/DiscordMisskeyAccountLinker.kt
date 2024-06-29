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

package app.nijimiss.mat.core.function.link

import app.nijimiss.mat.MisskeyAdminTools
import app.nijimiss.mat.core.function.link.webhook.WebhookServer
import app.nijimiss.mat.core.requests.ApiRequestManager
import app.nijimiss.mat.database.AccountsStore
import app.nijimiss.mat.entities.User
import app.nijimiss.mat.utils.ExpiringMap
import net.dv8tion.jda.api.interactions.commands.OptionType
import page.nafuchoco.neobot.api.command.CommandContext
import page.nafuchoco.neobot.api.command.CommandExecutor
import page.nafuchoco.neobot.api.command.CommandValueOption
import page.nafuchoco.neobot.api.module.NeoModuleLogger
import java.util.concurrent.TimeUnit


class DiscordMisskeyAccountLinker(
    private val accountsStore: AccountsStore,
    private val requestManager: ApiRequestManager,
) : CommandExecutor("verify") {
    private val logger: NeoModuleLogger = MisskeyAdminTools.getInstance().moduleLogger
    private val handlers: MutableList<LinkerHandler> = mutableListOf()
    private val waitingAccounts = ExpiringMap<User, String>(15, TimeUnit.MINUTES)

    init {
        options.add(
            CommandValueOption(
                OptionType.STRING,
                "verify_code",
                "管理用BOTから送信された認証コードを入力してください。 / Please enter the authentication code sent by the management BOT.",
                true,
                false
            )
        )
        options.add(
            CommandValueOption(
                OptionType.BOOLEAN,
                "force",
                "既に紐付けられているアカウントを強制的に解除して再度紐付けます。 / Force unlink the already linked account and link again.",
                false,
                false
            )
        )

        WebhookServer(this, accountsStore, requestManager)
    }

    fun onWaitingLink(misskeyUser: User, verify_code: String) {
        waitingAccounts[misskeyUser] = verify_code
    }

    fun registerHandler(handler: LinkerHandler) {
        handlers.add(handler)
    }

    override fun onInvoke(context: CommandContext) {
        var update = false

        val verify_code = context.options["verify_code"]?.value as String?
        val misskeyUser = waitingAccounts.entries.find { it.value == verify_code }?.key
        val force = context.options["force"]?.value as Boolean? ?: false

        // Check verify code
        if (misskeyUser == null) {
            context.responseSender.sendMessage("認証コードが正しくありません。 / The authentication code is incorrect.")
                .queue()
            return
        }

        // Check if the Misskey account is already linked
        val misskeyId = misskeyUser.id!!
        if (accountsStore.getMisskeyId(context.invoker.idLong) != null && accountsStore.getMisskeyId(context.invoker.idLong) != misskeyId) {
            if (!force) {
                context.responseSender.sendMessage("このDiscordアカウントは既に他のMisskeyアカウントに紐付けられています。再紐付けを行う場合は`force`オプションを付けてください。 / This Discord account is already linked to another Misskey account. If you want to relink, please add the `force` option.")
                    .queue()
                return
            } else {
                handlers.forEach { it.onUnlink(context.invoker.idLong, misskeyId) } // Unlink old discord account
                accountsStore.removeAccount(context.invoker.idLong)
            }
        } else if (accountsStore.getDiscordId(misskeyId) != null) {
            update = true
        }

        if (update) {
            handlers.forEach { it.onUnlink(context.invoker.idLong, misskeyId) } // Unlink old misskey account
            accountsStore.updateAccount(
                misskeyId,
                context.invoker.idLong
            )
        } else accountsStore.addAccount(context.invoker.idLong, misskeyId)

        handlers.forEach { it.onLink(context.invoker.idLong, misskeyId) }
        context.responseSender.sendMessage("Misskey ID `${misskeyUser.username}` とDiscordアカウントを紐付けました。 / Linked Misskey ID `${misskeyUser.username}` and Discord account.")
            .queue()

        waitingAccounts.remove(misskeyUser)
    }

    override fun getDescription(): String {
        return "DiscordアカウントとMisskeyアカウントを紐付けます。 / Link Discord account and Misskey account."
    }
}
