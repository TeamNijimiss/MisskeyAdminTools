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

package app.nijimiss.mat.core.function.ad

import app.nijimiss.mat.MisskeyAdminTools
import app.nijimiss.mat.database.AccountsStore
import app.nijimiss.mat.database.AdStore
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.buttons.Button
import page.nafuchoco.neobot.api.module.NeoModuleLogger
import java.awt.Color
import java.util.*

class AdsRequestReportSender(
    private val accountsStore: AccountsStore,
    private val adStore: AdStore,
    targetReportChannel: Long
) : ListenerAdapter(), AdsRequesterHandler {
    private val logger: NeoModuleLogger = MisskeyAdminTools.getInstance().moduleLogger
    private val discordApi: JDA = MisskeyAdminTools.getInstance().jda
    private val targetChannel: TextChannel = discordApi.getTextChannelById(targetReportChannel)
        ?: throw IllegalStateException("The specified channel does not exist.")


    override fun requestAds(
        requestId: String,
        requesterId: Long,
        imageFileId: String,
        imageUrl: String,
        linkUrl: String,
        comment: String?,
        endAt: Long?,
    ) {
        adStore.insertAd(requestId, requesterId, imageFileId, imageUrl, linkUrl, comment ?: "", endAt)

        val requestInfo: EmbedBuilder = EmbedBuilder()
            .setTitle("絵文字の追加リクエスト / Emoji add request")
            .addField("リクエストID / Request ID", requestId.toString(), false)
            .addField(
                "リクエストユーザー / Request User",
                "$requesterId (${accountsStore.getMisskeyId(requesterId)})", false
            )
            .addField("終了日時 / End date", if (endAt != null) Date(endAt).toString() else "未定義 / Undefined", false)
            .addField("コメント / Comment", comment ?: "None", false)
            .setFooter("Request date")
            .setTimestamp(Date().toInstant())
            .setImage(imageUrl)
            .setColor(Color.RED)
        targetChannel.sendMessageEmbeds(requestInfo.build()).queue {
            val buttons = listOf(
                Button.primary("ads_accept_$requestId", "承認 / Accept"),
                Button.danger("ads_deny_$requestId", "拒否 / Deny")
            )
            it.editMessageComponents().setActionRow(buttons).queue()
        }
    }
}
