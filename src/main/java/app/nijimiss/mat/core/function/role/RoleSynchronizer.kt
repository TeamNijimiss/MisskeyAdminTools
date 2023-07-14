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

package app.nijimiss.mat.core.function.role

import app.nijimiss.mat.MisskeyAdminTools
import app.nijimiss.mat.api.misskey.FullUser
import app.nijimiss.mat.core.database.AccountsStore
import app.nijimiss.mat.core.function.link.LinkerHandler
import app.nijimiss.mat.core.requests.ApiRequestManager
import app.nijimiss.mat.core.requests.ApiResponse
import app.nijimiss.mat.core.requests.ApiResponseHandler
import app.nijimiss.mat.core.requests.misskey.endpoints.admin.roles.Assign
import app.nijimiss.mat.core.requests.misskey.endpoints.admin.roles.Unassign
import app.nijimiss.mat.core.requests.misskey.endpoints.users.Show
import com.fasterxml.jackson.databind.ObjectMapper
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import page.nafuchoco.neobot.api.ConfigLoader
import page.nafuchoco.neobot.api.command.CommandContext
import page.nafuchoco.neobot.api.command.CommandExecutor
import page.nafuchoco.neobot.api.module.NeoModuleLogger
import java.io.File
import java.io.IOException
import java.nio.file.Files

class RoleSynchronizer(
    private val accountsStore: AccountsStore,
    private val requestManager: ApiRequestManager,
) : ListenerAdapter(), LinkerHandler {
    private val logger: NeoModuleLogger = MisskeyAdminTools.getInstance().moduleLogger
    private val discordApi: JDA = MisskeyAdminTools.getInstance().jda
    private val synchronizerConfig: RoleSynchronizerConfig
    private val targetChannel: TextChannel
    private val targetGuild: Guild

    init {
        val configFile = File(MisskeyAdminTools.getInstance().dataFolder, "RoleSynchronizerConfig.yaml")
        if (!configFile.exists()) {
            try {
                MisskeyAdminTools.getInstance().getResources("RoleSynchronizerConfig.yaml").use { original ->
                    Files.copy(original, configFile.toPath())
                    logger.info("The configuration file was not found, so a new file was created.")
                }
            } catch (e: IOException) {
                logger.error(
                    """
                    The correct configuration file could not be retrieved from the executable.
                    If you have a series of problems, please contact the developer.
                    """.trimIndent(), e
                )
            }
        }

        synchronizerConfig = ConfigLoader.loadConfig(configFile, RoleSynchronizerConfig::class.java)
        targetChannel = discordApi.getTextChannelById(synchronizerConfig.discordTargetChannel)
            ?: throw IllegalStateException("The specified channel does not exist.")
        targetGuild = targetChannel.guild
        discordApi.addEventListener(this)

        MisskeyAdminTools.getInstance().registerCommand(object : CommandExecutor("resync") {
            override fun onInvoke(context: CommandContext) {
                if (context.invoker.roles.map { it.idLong }.contains(synchronizerConfig.adminRole)) {
                    context.responseSender.sendMessage("同期を開始します。 / Start synchronization.").queue()
                    accountsStore.getMisskeyAccounts().forEach { misskeyId ->
                        val discordId = accountsStore.getDiscordId(misskeyId)
                        synchronizeRole(discordId!!, misskeyId)
                    }
                } else {
                    context.responseSender.sendMessage("このコマンドを実行する権限がありません。 / You do not have permission to run this command.")
                        .queue()
                }
            }

            override fun getDescription(): String {
                return "DiscordアカウントとMisskeyアカウントのロールを再同期します。 / Resynchronize Discord and Misskey account roles."
            }
        })
    }

    override fun onLink(discordId: Long, misskeyId: String) {
        synchronizeRole(discordId, misskeyId)
    }

    override fun onUnlink(discordId: Long, misskeyId: String) {
        unassignAllRoles(misskeyId)
    }

    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) {
        val misskeyId = accountsStore.getMisskeyId(event.user.idLong)
        if (misskeyId != null) synchronizeRole(event.user.idLong, misskeyId)
    }

    override fun onGuildMemberRoleRemove(event: GuildMemberRoleRemoveEvent) {
        val misskeyId = accountsStore.getMisskeyId(event.user.idLong)
        if (misskeyId != null) synchronizeRole(event.user.idLong, misskeyId)
    }

    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        val misskeyId = accountsStore.getMisskeyId(event.user.idLong)
        if (misskeyId != null) unassignAllRoles(misskeyId)
    }

    private fun synchronizeRole(discordId: Long, misskeyId: String) {
        val show = Show(misskeyId, Show.SearchType.ID)
        requestManager.addRequest(show, object : ApiResponseHandler {
            override fun onSuccess(response: ApiResponse?) {
                val user = MAPPER.readValue(response!!.body, FullUser::class.java)
                val misskeyRoles = user.roles?.map { it.id }
                val discordRoles = targetGuild.getMemberById(discordId)?.roles?.map { it.idLong }

                // role synchronization
                synchronizerConfig.roleAssign.forEach { role ->
                    if (misskeyRoles?.contains(role.misskeyRole) == true && discordRoles?.contains(role.discordRole) == false) {
                        val unassign = Unassign(misskeyId, role.misskeyRole!!)
                        requestManager.addRequest(unassign, object : ApiResponseHandler {
                            override fun onSuccess(response: ApiResponse?) {
                                logger.debug("Unassigned role from user $misskeyId.")
                            }

                            override fun onFailure(response: ApiResponse?) {
                                logger.error("Failed to unassigned role from user.", response!!.body)
                            }
                        })
                    } else if (misskeyRoles?.contains(role.misskeyRole) == false && discordRoles?.contains(role.discordRole) == true) {
                        val assign = Assign(misskeyId, role.misskeyRole!!)
                        requestManager.addRequest(assign, object : ApiResponseHandler {
                            override fun onSuccess(response: ApiResponse?) {
                                logger.debug("Assigned role to user $misskeyId.")
                            }

                            override fun onFailure(response: ApiResponse?) {
                                logger.error("Failed to assign role to user.", response!!.body)
                            }
                        })
                    }
                }
            }

            override fun onFailure(response: ApiResponse?) {
                logger.error("Failed to get user information.", response!!.body)
            }
        })
    }

    private fun unassignAllRoles(misskeyId: String) {
        val show = Show(misskeyId, Show.SearchType.ID)
        requestManager.addRequest(show, object : ApiResponseHandler {
            override fun onSuccess(response: ApiResponse?) {
                val user = MAPPER.readValue(response!!.body, FullUser::class.java)
                val misskeyRoles = user.roles?.map { it.id }

                // role synchronization
                synchronizerConfig.roleAssign.forEach { role ->
                    if (misskeyRoles?.contains(role.misskeyRole) == true) {
                        val unassign = Unassign(misskeyId, role.misskeyRole!!)
                        requestManager.addRequest(unassign, object : ApiResponseHandler {
                            override fun onSuccess(response: ApiResponse?) {
                                logger.debug("Unassigned role from user $misskeyId.")
                            }

                            override fun onFailure(response: ApiResponse?) {
                                logger.error("Failed to unassigned role from user.", response!!.body)
                            }
                        })
                    }
                }
            }

            override fun onFailure(response: ApiResponse?) {
                logger.error("Failed to get user information.", response!!.body)
            }
        })
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
