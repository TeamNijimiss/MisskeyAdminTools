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

class EmojiMetaStore(connector: DatabaseConnector) : DatabaseTable(connector, "emojis_meta") {

    @Throws(SQLException::class)
    fun createTable() {
        super.createTable(
            "emoji_id VARCHAR(36) NOT NULL PRIMARY KEY, " +
                    "emoji_name VARCHAR(32) NOT NULL, " +
                    "image_file_id VARCHAR(36) NOT NULL, " +
                    "image_url VARCHAR(256) NOT NULL, " +
                    "license VARCHAR(32), " +
                    "is_sensitive BOOLEAN NOT NULL DEFAULT 0, " +
                    "local_only BOOLEAN NOT NULL DEFAULT 0, " +
                    "comment VARCHAR(256), " +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "approved BOOLEAN NOT NULL DEFAULT 0, " +
                    "approver_id BIGINT, " +
                    "approved_at TIMESTAMP"
        )
    }
}
