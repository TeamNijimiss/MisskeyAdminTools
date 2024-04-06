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

package app.nijimiss.mat.core.function.decoration

import app.nijimiss.mat.core.entities.RequestBase
import java.util.*

data class DecorationRequest(
    override val requestId: UUID,
    override val requesterId: Long,
    val decorationName: String,
    override val imageFileId: String,
    override val imageUrl: String,
    val license: String?,
    override val comment: String?,
    override val createAt: Long
) : RequestBase
