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

import org.apache.commons.lang3.StringUtils

abstract class ValuedApiRequest : ApiRequest {
    protected val data: MutableMap<String, Any> = LinkedHashMap()

    protected fun add(key: String, value: Any?) {
        require(!StringUtils.isBlank(key)) { "Key cannot be blank" }
        if (value != null) data[key] = value
    }

    protected fun remove(key: String) {
        data.remove(key)
    }
}
