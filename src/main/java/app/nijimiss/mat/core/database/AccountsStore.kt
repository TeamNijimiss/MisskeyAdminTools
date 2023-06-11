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

import page.nafuchoco.neobot.api.DatabaseConnector
import java.sql.SQLException

class AccountsStore(connector: DatabaseConnector) : DatabaseTable(connector, "accounts") {

    @Throws(SQLException::class)
    fun createTable() {
        super.createTable("discord_id BIGINT NOT NULL, misskey_id VARCHAR(26) NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    }

    @Throws(SQLException::class)
    fun addAccount(discordId: Long, misskeyId: String) {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO $tableName (discord_id, misskey_id) VALUES (?, ?)"
            ).use { ps ->
                ps.setLong(1, discordId)
                ps.setString(2, misskeyId)
                ps.execute()
            }
        }
    }

    @Throws(SQLException::class)
    fun updateAccount(discordId: Long, misskeyId: String) {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE $tableName SET misskey_id = ? WHERE discord_id = ?"
            ).use { ps ->
                ps.setString(1, misskeyId)
                ps.setLong(2, discordId)
                ps.execute()
            }
        }
    }

    @Throws(SQLException::class)
    fun getMisskeyId(discordId: Long): String? {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM $tableName WHERE discord_id = ?"
            ).use { ps ->
                ps.setLong(1, discordId)
                ps.execute()
                val rs = ps.resultSet
                if (rs.next()) {
                    return rs.getString("misskey_id")
                }
            }
        }
        return null
    }

    @Throws(SQLException::class)
    fun getDiscordId(misskeyId: String): Long? {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM $tableName WHERE misskey_id = ?"
            ).use { ps ->
                ps.setString(1, misskeyId)
                ps.execute()
                val rs = ps.resultSet
                if (rs.next()) {
                    return rs.getLong("discord_id")
                }
            }
        }
        return null
    }

    @Throws(SQLException::class)
    fun getUpdatedTime(discordId: Long): Long? {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM $tableName WHERE discord_id = ?"
            ).use { ps ->
                ps.setLong(1, discordId)
                ps.execute()
                val rs = ps.resultSet
                if (rs.next()) {
                    return rs.getTimestamp("updated_at").time
                }
            }
        }
        return null
    }

    @Throws(SQLException::class)
    fun getMisskeyAccounts(): List<String> {
        val accounts = mutableListOf<String>()
        connector.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM $tableName"
            ).use { ps ->
                ps.execute()
                val rs = ps.resultSet
                while (rs.next()) {
                    accounts.add(rs.getString("misskey_id"))
                }
            }
        }
        return accounts
    }
}
