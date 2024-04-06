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

import app.nijimiss.mat.core.function.decoration.DecorationRequest
import page.nafuchoco.neobot.api.DatabaseConnector
import java.sql.SQLException
import java.util.*

class DecorationStore(connector: DatabaseConnector) : DatabaseTable(connector, "decorations") {

    @Throws(SQLException::class)
    fun createTable() {
        super.createTable(
            "request_id VARCHAR(36) NOT NULL PRIMARY KEY, " +
                    "requester_id BIGINT NOT NULL, " +
                    "decoration_name VARCHAR(32) NOT NULL, " +
                    "image_file_id VARCHAR(36) NOT NULL, " +
                    "image_url VARCHAR(256) NOT NULL, " +
                    "license VARCHAR(128), " +
                    "comment VARCHAR(256), " +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "approved BOOLEAN NOT NULL DEFAULT 0, " +
                    "approver_id BIGINT, " +
                    "approved_at TIMESTAMP"
        )
    }

    @Throws(SQLException::class)
    fun getDecorationRequest(requestId: UUID): DecorationRequest? {
        connector.connection.use { connection ->
            connection.prepareStatement("SELECT * FROM $tableName WHERE request_id = ? AND approved = FALSE")
                .use { statement ->
                    statement.setString(1, requestId.toString())
                    statement.executeQuery().use { result ->
                        if (result.next()) {
                            return DecorationRequest(
                                UUID.fromString(result.getString("request_id")),
                                result.getLong("requester_id"),
                                result.getString("decoration_name"),
                                result.getString("image_file_id"),
                                result.getString("image_url"),
                                result.getString("license"),
                                result.getString("comment"),
                                result.getTimestamp("created_at").time
                            )
                        }
                    }
                }
        }
        return null
    }

    @Throws(SQLException::class)
    fun countDecorationRequestsThisMonth(requesterId: Long): Int {
        connector.connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM $tableName WHERE requester_id = ? AND MONTH(created_at) = MONTH(CURRENT_TIMESTAMP) AND YEAR(created_at) = YEAR(CURRENT_TIMESTAMP)")
                .use { statement ->
                    statement.setLong(1, requesterId)
                    statement.executeQuery().use { result ->
                        if (result.next()) {
                            return result.getInt(1)
                        }
                    }
                }
        }
        return 0
    }

    @Throws(SQLException::class)
    fun insertDecorationRequest(request: DecorationRequest) {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO $tableName (request_id, requester_id, decoration_name, image_file_id, image_url, license, comment) VALUES (?, ?, ?, ?, ?, ?, ?)"
            ).use { statement ->
                statement.setString(1, request.requestId.toString())
                statement.setLong(2, request.requesterId)
                statement.setString(3, request.decorationName)
                statement.setString(4, request.imageFileId)
                statement.setString(5, request.imageUrl)
                statement.setString(6, request.license)
                statement.setString(7, request.comment)
                statement.executeUpdate()
            }
        }
    }

    @Throws(SQLException::class)
    fun approveDecorationRequest(requestId: UUID, approverId: Long) {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE $tableName SET approved = TRUE, approver_id = ?, approved_at = CURRENT_TIMESTAMP WHERE request_id = ?"
            ).use { statement ->
                statement.setLong(1, approverId)
                statement.setString(2, requestId.toString())
                statement.executeUpdate()
            }
        }
    }

    @Throws(SQLException::class)
    fun deleteDecorationRequest(requestId: UUID) {
        connector.connection.use { connection ->
            connection.prepareStatement("DELETE FROM $tableName WHERE request_id = ?")
                .use { statement ->
                    statement.setString(1, requestId.toString())
                    statement.executeUpdate()
                }
        }
    }
}
