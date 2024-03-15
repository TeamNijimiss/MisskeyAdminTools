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

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.apache.commons.lang3.StringUtils
import org.apache.tika.Tika
import java.io.File

abstract class FileUploadApiRequest : ValuedApiRequest() {
    private val files: MutableMap<String, File> = LinkedHashMap()

    protected fun addFile(key: String, value: File?) {
        require(!StringUtils.isBlank(key)) { "Key cannot be blank" }
        if (value != null) files[key] = value
    }

    protected fun removeFile(key: String) {
        files.remove(key)
    }

    override val body: RequestBody?
        get() = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                data.forEach { (key, value) ->
                    addFormDataPart(key, value.toString())
                }
                files.forEach { (key, value) ->
                    addFormDataPart(key, value.name, value.asRequestBody(TIKA.detect(value).toMediaType()))
                }
            }
            .build()


    companion object {
        private val TIKA: Tika = Tika()
    }
}
