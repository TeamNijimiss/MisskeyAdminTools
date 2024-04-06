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

package app.nijimiss.mat.database

import app.nijimiss.mat.core.function.ad.AdRequest
import page.nafuchoco.neobot.api.DatabaseConnector
import java.sql.SQLException
import java.util.*

class AdStore(connector: DatabaseConnector) : DatabaseTable(connector, "ads") {

    @Throws(SQLException::class)
    fun createTable() {
        super.createTable(
            "request_id VARCHAR(36) NOT NULL PRIMARY KEY, " +
                    "requester_id BIGINT NOT NULL, " +
                    "image_file_id VARCHAR(36) NOT NULL, " +
                    "image_url VARCHAR(256) NOT NULL, " +
                    "link_url VARCHAR(256) NOT NULL, " +
                    "comment VARCHAR(256), " +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "approved BOOLEAN NOT NULL DEFAULT 0, " +
                    "approver_id BIGINT, " +
                    "start_at TIMESTAMP, " +
                    "end_at TIMESTAMP"
        )
    }

    @Throws(SQLException::class)
    fun getAdRequest(requestId: UUID): AdRequest? {
        connector.connection.use { connection ->
            connection.prepareStatement("SELECT * FROM $tableName WHERE request_id = ? AND approved = FALSE")
                .use { statement ->
                    statement.setString(1, requestId.toString())
                    statement.executeQuery().use { result ->
                        if (result.next()) {
                            return AdRequest(
                                UUID.fromString(result.getString("request_id")),
                                result.getLong("requester_id"),
                                result.getString("image_file_id"),
                                result.getString("image_url"),
                                result.getString("link_url"),
                                result.getString("comment"),
                                result.getTimestamp("created_at").time,
                                result.getTimestamp("end_at")?.time
                            )
                        }
                    }
                }
        }
        return null
    }

    // approved = TRUE かつ end_at が現在時刻より後の広告 もしくは approved = FALSE の広告が存在するか確認
    @Throws(SQLException::class)
    fun checkActiveAd(requesterId: Long): Boolean {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM $tableName WHERE requester_id = ? AND (approved = TRUE AND end_at > CURRENT_TIMESTAMP OR approved = FALSE)"
            ).use { statement ->
                statement.setLong(1, requesterId)
                statement.executeQuery().use { result ->
                    return result.next()
                }
            }
        }
    }

    @Throws(SQLException::class)
    fun insertAd(
        requestId: UUID,
        requesterId: Long,
        imageFileId: String,
        imageUrl: String,
        linkUrl: String,
        comment: String?,
        endAt: Long?
    ) {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO $tableName (request_id, requester_id, image_file_id, image_url, link_url, comment) VALUES (?, ?, ?, ?, ?, ?)"
            ).use { statement ->
                statement.setString(1, requestId.toString())
                statement.setLong(2, requesterId)
                statement.setString(3, imageFileId)
                statement.setString(4, imageUrl)
                statement.setString(5, linkUrl)
                statement.setString(6, comment)
                if (endAt != null) {
                    statement.setTimestamp(7, java.sql.Timestamp(endAt))
                }
                statement.executeUpdate()
            }
        }
    }

    @Throws(SQLException::class)
    fun approveAd(requestId: UUID, approverId: Long, startAt: Long, endAt: Long) {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE $tableName SET approved = 1, approver_id = ?, start_at = ?, end_at = ? WHERE request_id = ?"
            ).use { statement ->
                statement.setLong(1, approverId)
                statement.setTimestamp(2, java.sql.Timestamp(startAt))
                statement.setTimestamp(3, java.sql.Timestamp(endAt))
                statement.setString(4, requestId.toString())
                statement.executeUpdate()
            }
        }
    }

    @Throws(SQLException::class)
    fun deleteAd(requestId: UUID) {
        connector.connection.use { connection ->
            connection.prepareStatement("DELETE FROM $tableName WHERE request_id = ?").use { statement ->
                statement.setString(1, requestId.toString())
                statement.executeUpdate()
            }
        }
    }
}
