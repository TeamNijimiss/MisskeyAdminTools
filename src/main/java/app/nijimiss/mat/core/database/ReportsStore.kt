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

import app.nijimiss.mat.core.function.report.ReportContext
import page.nafuchoco.neobot.api.DatabaseConnector
import java.sql.SQLException

class ReportsStore(connector: DatabaseConnector) : DatabaseTable(connector, "reports") {

    @Throws(SQLException::class)
    fun createTable() {
        super.createTable("report_id VARCHAR(26) NOT NULL, note_id VARCHAR(26) NOT NULL, target_id VARCHAR(26), message_id BIGINT NOT NULL, PRIMARY KEY (note_id, message_id) ")
    }

    @Throws(SQLException::class)
    fun addReport(context: ReportContext) {
        for (noteId in context.reportTargetNoteIds) {
            connector.connection.use { connection ->
                connection.prepareStatement(
                    "INSERT INTO $tableName (report_id, note_id, target_id, message_id) VALUES (?, ?, ?, ?)"
                ).use { ps ->
                    ps.setString(1, context.reportId)
                    ps.setString(2, noteId)
                    ps.setString(3, context.reportTargetUserId)
                    ps.setLong(4, context.messageId.toLong())
                    ps.execute()
                }
            }
        }
    }

    @Throws(SQLException::class)
    fun getReport(messageId: Long): ReportContext? {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM $tableName WHERE message_id = ?"
            ).use { ps ->
                ps.setLong(1, messageId)
                ps.execute()
                val rs = ps.resultSet
                if (rs.next()) {
                    val reportId = rs.getString("report_id")
                    return ReportContext(
                        reportId,
                        messageId.toString(),
                        rs.getString("target_id"),
                        getNotes(reportId)
                    )
                }
                return null
            }
        }
    }

    @Throws(SQLException::class)
    fun getNotes(reportId: String): List<String> {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "SELECT note_id FROM $tableName WHERE report_id = ?"
            ).use { ps ->
                ps.setString(1, reportId)
                ps.execute()
                val rs = ps.resultSet
                val notes = mutableListOf<String>()
                while (rs.next()) {
                    notes.add(rs.getString("note_id"))
                }
                return notes
            }
        }
    }

    @Throws(SQLException::class)
    fun getMessages(noteId: String): List<Long> {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "SELECT message_id FROM $tableName WHERE note_id = ?"
            ).use { ps ->
                ps.setString(1, noteId)
                ps.execute()
                val rs = ps.resultSet
                val messages = mutableListOf<Long>()
                while (rs.next()) {
                    messages.add(rs.getLong("message_id"))
                }
                return messages
            }
        }
    }

    @Throws(SQLException::class)
    fun removeReport(messageId: Long) {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "DELETE FROM $tableName WHERE message_id = ?"
            ).use { ps ->
                ps.setLong(1, messageId)
                ps.execute()
            }
        }
    }
}
