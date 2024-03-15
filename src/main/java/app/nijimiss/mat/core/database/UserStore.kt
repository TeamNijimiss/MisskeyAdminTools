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

package app.nijimiss.mat.core.database

import page.nafuchoco.neobot.api.DatabaseConnector
import java.sql.SQLException

class UserStore(connector: DatabaseConnector) : DatabaseTable(connector, "users") {

    @Throws(SQLException::class)
    fun createTable() {
        super.createTable("user_id VARCHAR(26) NOT NULL, account_status VARCHAR(128) NOT NULL DEFAULT 'normal', warning_count INT NOT NULL DEFAULT 0, PRIMARY KEY (user_id)")
    }

    @Throws(SQLException::class)
    fun getUserStatus(userId: String): String? {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM $tableName WHERE user_id = ?"
            ).use { ps ->
                ps.setString(1, userId)
                ps.executeQuery().use { rs ->
                    return if (rs.next()) {
                        rs.getString("account_status")
                    } else {
                        null
                    }
                }
            }
        }
    }

    @Throws(SQLException::class)
    fun getWarningCount(userId: String): Int {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM $tableName WHERE user_id = ?"
            ).use { ps ->
                ps.setString(1, userId)
                ps.executeQuery().use { rs ->
                    return if (rs.next()) {
                        rs.getInt("warning_count")
                    } else {
                        0
                    }
                }
            }
        }
    }

    @Throws(SQLException::class)
    fun registerUser(userId: String) {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO $tableName (user_id) SELECT ? WHERE NOT EXISTS (SELECT * FROM $tableName WHERE user_id = ?)"
            ).use { ps ->
                ps.setString(1, userId)
                ps.setString(2, userId)
                ps.execute()
            }
        }
    }

    @Throws(SQLException::class)
    fun updateAccountStatus(userId: String, status: String) {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE $tableName SET account_status = ? WHERE user_id = ?"
            ).use { ps ->
                ps.setString(1, status)
                ps.setString(2, userId)
                ps.execute()
            }
        }
    }

    @Throws(SQLException::class)
    fun updateWarningCount(userId: String, count: Int) {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE $tableName SET warning_count = ? WHERE user_id = ?"
            ).use { ps ->
                ps.setInt(1, count)
                ps.setString(2, userId)
                ps.execute()
            }
        }
    }
}
