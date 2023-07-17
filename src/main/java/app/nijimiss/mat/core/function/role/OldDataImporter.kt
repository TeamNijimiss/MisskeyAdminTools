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

import app.nijimiss.mat.core.database.AccountsStore
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.File

class OldDataImporter(
    private val accountsStore: AccountsStore,
) {

    fun load(file: File) {
        val hikariConfig = HikariConfig()
        hikariConfig.driverClassName = "org.sqlite.JDBC"
        hikariConfig.jdbcUrl = "jdbc:sqlite:" + file.absolutePath
        val dataSource = HikariDataSource(hikariConfig)

        dataSource.connection.use {
            it.prepareStatement("SELECT discordId, misskeyId FROM users").use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val discordId = rs.getLong("discordId")
                        val misskeyId = rs.getString("misskeyId")

                        if (discordId == 0L || misskeyId == null) continue
                        accountsStore.addAccount(discordId, misskeyId)
                    }
                }
            }
        }

        dataSource.close()
    }
}
