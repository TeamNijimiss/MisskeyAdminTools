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

package app.nijimiss.mat.core.requests.misskey.endpoints.admin.emoji;

import app.nijimiss.mat.core.requests.misskey.RequireCredentialRequest;
import org.jetbrains.annotations.NotNull;

public class List extends RequireCredentialRequest {

    public List(String query, int limit, String sinceId, String untilId) {
        add("query", query);
        add("limit", limit);
        add("sinceId", sinceId);
        add("untilId", untilId);
    }

    @NotNull
    @Override
    public String getEndpoint() {
        return "api/admin/emoji/list";
    }

    @Override
    public int getSuccessCode() {
        return 200;
    }
}
