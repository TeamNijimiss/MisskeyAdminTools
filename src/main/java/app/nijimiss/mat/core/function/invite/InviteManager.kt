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

package app.nijimiss.mat.core.function.invite

import app.nijimiss.mat.MisskeyAdminTools
import app.nijimiss.mat.core.requests.ApiRequestManager
import app.nijimiss.mat.core.requests.ApiResponse
import app.nijimiss.mat.core.requests.ApiResponseHandler
import app.nijimiss.mat.core.requests.misskey.endpoints.admin.Invite
import com.google.gson.Gson
import com.google.gson.JsonArray
import net.dv8tion.jda.api.interactions.commands.OptionType
import page.nafuchoco.neobot.api.ConfigLoader
import page.nafuchoco.neobot.api.command.CommandContext
import page.nafuchoco.neobot.api.command.CommandExecutor
import page.nafuchoco.neobot.api.command.CommandValueOption
import page.nafuchoco.neobot.api.module.NeoModuleLogger
import java.io.File
import java.io.IOException
import java.nio.file.Files

class InviteManager : CommandExecutor("invite") {
    private val logger: NeoModuleLogger = MisskeyAdminTools.getInstance().moduleLogger
    private val inviteManagerConfig: InviteManagerConfig

    private val requestManagers: MutableMap<String, ApiRequestManager> = mutableMapOf()

    init {
        val configFile = File(MisskeyAdminTools.getInstance().dataFolder, "InviteManagerConfig.yaml")
        if (!configFile.exists()) {
            try {
                MisskeyAdminTools.getInstance().getResources("InviteManagerConfig.yaml").use { original ->
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
        inviteManagerConfig = ConfigLoader.loadConfig(configFile, InviteManagerConfig::class.java)

        inviteManagerConfig.instances.forEach { instance ->
            requestManagers += instance.hostname to ApiRequestManager(instance.hostname, instance.token)
        }

        val inviteOption =
            CommandValueOption(OptionType.STRING, "instance", "The instance to invite the user to.", true, false)
        inviteManagerConfig.instances.forEach { instance ->
            inviteOption.addChoiceAsString(instance.instanceName, instance.hostname)
        }
        options.add(inviteOption)
    }

    override fun onInvoke(context: CommandContext) {
        val instance = context.options["instance"]?.value as String
        val requestManager = requestManagers[instance]
        if (requestManager == null) {
            context.responseSender.sendMessage("The specified instance does not exist.").setEphemeral(true).queue()
            return
        }

        requestManager.addRequest(Invite(), object : ApiResponseHandler {
            override fun onSuccess(response: ApiResponse?) {
                GSON.fromJson(response!!.body, JsonArray::class.java)[0].asJsonObject.get("code").asString.let {
                    context.responseSender.sendMessage("Invite code: $it").setEphemeral(true).queue()
                }
            }

            override fun onFailure(response: ApiResponse?) {
                context.responseSender.sendMessage("Failed to invite user to instance.").setEphemeral(true).queue()
            }
        })
    }

    override fun getDescription(): String {
        return "Invite the user to the instance."
    }

    companion object {
        private val GSON = Gson()
    }
}
