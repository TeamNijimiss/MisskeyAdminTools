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

class MATSystemDataStore(connector: DatabaseConnector) : DatabaseTable(connector, "system") {
    @Throws(SQLException::class)
    fun createTable() {
        super.createTable("options_key VARCHAR(128) NOT NULL UNIQUE, option_value VARCHAR(1024) NOT NULL")
    }

    @Throws(SQLException::class)
    fun setOption(key: String?, value: String?) {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO $tableName (options_key, option_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE option_value = ?"
            ).use { ps ->
                ps.setString(1, key)
                ps.setString(2, value)
                ps.setString(3, value)
                ps.execute()
            }
        }
    }

    @Throws(SQLException::class)
    fun getOption(key: String?): String? {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "SELECT option_value FROM $tableName WHERE options_key = ?"
            ).use { ps ->
                ps.setString(1, key)
                ps.execute()
                val rs = ps.resultSet
                return if (rs.next()) rs.getString("option_value") else null
            }
        }
    }
}
