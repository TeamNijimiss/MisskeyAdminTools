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

abstract class DatabaseTable protected constructor(protected val connector: DatabaseConnector, tablename: String) {
    val tableName: String

    init {
        tableName = connector.prefix + tablename
    }

    /**
     * Creates a table with the specified structure.
     * If a table with the same name already exists, it exits without executing the process.
     *
     * @param construction Structure of the table to be created
     * @throws SQLException Thrown when creating a table fails.
     */
    @Throws(SQLException::class)
    fun createTable(construction: String) {
        connector.connection.use { connection ->
            connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS $tableName ($construction)"
            ).use { ps -> ps.execute() }
        }
    }
}
