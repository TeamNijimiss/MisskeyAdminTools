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

package app.nijimiss.mat.core.database

import app.nijimiss.mat.core.function.emoji.ApprovedEmoji
import app.nijimiss.mat.core.function.emoji.EmojiRequest
import page.nafuchoco.neobot.api.DatabaseConnector
import java.sql.SQLException
import java.util.*

class EmojiStore(connector: DatabaseConnector) : DatabaseTable(connector, "emojis") {

    @Throws(SQLException::class)
    fun createTable() {
        super.createTable(
            "request_id VARCHAR(36) NOT NULL PRIMARY KEY, " +
                    "requester_id BIGINT NOT NULL, " +
                    "emoji_name VARCHAR(32) NOT NULL, " +
                    "image_file_id VARCHAR(36) NOT NULL, " +
                    "image_url VARCHAR(256) NOT NULL, " +
                    "aliases VARCHAR(128), " +
                    "license VARCHAR(128), " +
                    "is_sensitive BOOLEAN NOT NULL DEFAULT 0, " +
                    "local_only BOOLEAN NOT NULL DEFAULT 0, " +
                    "comment VARCHAR(256), " +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "approved BOOLEAN NOT NULL DEFAULT 0, " +
                    "approved_emoji_id VARCHAR(36)," +
                    "approver_id BIGINT, " +
                    "approved_at TIMESTAMP"
        )
    }

    @Throws(SQLException::class)
    fun existsEmoji(name: String): Boolean {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM $tableName WHERE emoji_name = ?"
            ).use { ps ->
                ps.setString(1, name)
                ps.executeQuery().use { rs ->
                    return rs.next()
                }
            }
        }
    }

    @Throws(SQLException::class)
    fun getEmoji(name: String): ApprovedEmoji? {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM $tableName WHERE emoji_name = ? AND approved = TRUE"
            ).use { ps ->
                ps.setString(1, name)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        return ApprovedEmoji(
                            UUID.fromString(rs.getString("request_id")),
                            rs.getLong("requester_id"),
                            rs.getString("emoji_name"),
                            rs.getString("image_file_id"),
                            rs.getString("image_url"),
                            rs.getString("aliases")?.splitWithoutEmpty(",") ?: emptyArray(),
                            rs.getString("license"),
                            rs.getBoolean("is_sensitive"),
                            rs.getBoolean("local_only"),
                            rs.getString("comment"),
                            rs.getTimestamp("created_at").time,
                            UUID.fromString(rs.getString("approved_emoji_id")),
                            rs.getLong("approver_id"),
                            rs.getTimestamp("approved_at").time
                        )
                    }
                }
            }
        }
        return null
    }

    @Throws(SQLException::class)
    fun addEmojiRequest(request: EmojiRequest) {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO $tableName (request_id, requester_id, emoji_name, image_file_id, image_url, aliases, license, is_sensitive, local_only, comment) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            ).use { ps ->
                ps.setString(1, request.requestId.toString())
                ps.setLong(2, request.requesterId)
                ps.setString(3, request.emojiName)
                ps.setString(4, request.imageFileId)
                ps.setString(5, request.imageUrl)
                ps.setString(6, request.aliases.joinToString(","))
                ps.setString(7, request.license)
                ps.setBoolean(8, request.sensitive)
                ps.setBoolean(9, request.localOnly)
                ps.setString(10, request.comment)
                ps.executeUpdate()
            }
        }
    }

    @Throws(SQLException::class)
    fun getEmojiRequest(requestId: String): EmojiRequest? {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM $tableName WHERE request_id = ?"
            ).use { ps ->
                ps.setString(1, requestId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        return EmojiRequest(
                            UUID.fromString(rs.getString("request_id")),
                            rs.getLong("requester_id"),
                            rs.getString("emoji_name"),
                            rs.getString("image_file_id"),
                            rs.getString("image_url"),
                            rs.getString("aliases")?.splitWithoutEmpty(",") ?: emptyArray(),
                            rs.getString("license"),
                            rs.getBoolean("is_sensitive"),
                            rs.getBoolean("local_only"),
                            rs.getString("comment"),
                            rs.getTimestamp("created_at").time
                        )
                    }
                }
            }
        }
        return null
    }

    fun searchEmojiRequest(requesterId: Long, emojiName: String): List<EmojiRequest> {
        val requests = mutableListOf<EmojiRequest>()
        connector.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM $tableName WHERE requester_id = ? AND approved = FALSE AND emoji_name LIKE ?"
            ).use { ps ->
                ps.setLong(1, requesterId)
                ps.setString(2, "%$emojiName%")
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        requests.add(
                            EmojiRequest(
                                UUID.fromString(rs.getString("request_id")),
                                rs.getLong("requester_id"),
                                rs.getString("emoji_name"),
                                rs.getString("image_file_id"),
                                rs.getString("image_url"),
                                rs.getString("aliases")?.splitWithoutEmpty(",") ?: emptyArray(),
                                rs.getString("license"),
                                rs.getBoolean("is_sensitive"),
                                rs.getBoolean("local_only"),
                                rs.getString("comment"),
                                rs.getTimestamp("created_at").time
                            )
                        )
                    }
                }
            }
        }
        return requests
    }

    @Throws(SQLException::class)
    fun approveEmojiRequest(requestId: String, approverId: Long, emojiId: String) {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE $tableName SET approved = TRUE, approver_id = ?, approved_at = CURRENT_TIMESTAMP, approved_emoji_id = ? WHERE request_id = ?"
            ).use { ps ->
                ps.setLong(1, approverId)
                ps.setString(2, emojiId)
                ps.setString(3, requestId)
                ps.execute()
            }
        }
    }

    @Throws(SQLException::class)
    fun rejectEmojiRequest(requestId: String) {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "DELETE FROM $tableName WHERE request_id = ?"
            ).use { ps ->
                ps.setString(1, requestId)
                ps.execute()
            }
        }
    }

    @Throws(SQLException::class)
    fun updateEmoji(request: EmojiRequest) {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE $tableName SET emoji_name = ?, image_file_id = ?, image_url = ?, aliases = ? , license = ?, is_sensitive = ?, local_only = ?, comment = ? WHERE request_id = ?"
            ).use { ps ->
                ps.setString(1, request.emojiName)
                ps.setString(2, request.imageFileId)
                ps.setString(3, request.imageUrl)
                ps.setString(4, request.aliases.joinToString(","))
                ps.setString(5, request.license)
                ps.setBoolean(6, request.sensitive)
                ps.setBoolean(7, request.localOnly)
                ps.setString(8, request.comment)
                ps.setString(9, request.requestId.toString())
                ps.executeUpdate()
            }
        }
    }

    @Throws(SQLException::class)
    fun countEmojiRequestLastMonth(requesterId: Long): Int {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "SELECT COUNT(*) FROM $tableName WHERE requester_id = ? AND YEAR(created_at) = YEAR(CURRENT_DATE) AND MONTH(created_at) = MONTH(CURRENT_DATE)"
            ).use { ps ->
                ps.setLong(1, requesterId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        return rs.getInt(1)
                    }
                }
            }
        }
        return 0
    }

    // TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP


    private fun String.splitWithoutEmpty(separator: String): Array<String> {
        return this.split(separator).toTypedArray().takeIf { it[0].isNotEmpty() } ?: emptyArray()
    }
}
