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
package app.nijimiss.mat.core.requests

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

abstract class JsonApiRequest : ValuedApiRequest() {
    var serializeNulls = false

    override val body: RequestBody?
        get() = if (serializeNulls) GsonBuilder().serializeNulls().create().toJson(data)
            .toRequestBody(MEDIA_TYPE_JSON) else Gson().toJson(data).toRequestBody(MEDIA_TYPE_JSON)


    companion object {
        private val MEDIA_TYPE_JSON: MediaType = "application/json; charset=utf-8".toMediaType()
    }
}
