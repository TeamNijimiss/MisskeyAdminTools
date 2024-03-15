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

package app.nijimiss.mat.core.requests.misskey.endpoints.admin;

import app.nijimiss.mat.core.requests.misskey.RequireCredentialRequest;
import app.nijimiss.mat.core.requests.misskey.elements.Origin;
import app.nijimiss.mat.core.requests.misskey.elements.State;
import org.jetbrains.annotations.NotNull;

public class ShowUsers extends RequireCredentialRequest {

    public ShowUsers(String hostname, int limit, int offset, String sort, Origin origin, State state, String username) {
        add("hostname", hostname);
        add("limit", limit);
        add("offset", offset);
        add("sort", sort);
        add("origin", origin.name().toLowerCase());
        add("state", state.name().toLowerCase());
        add("username", username);
    }

    @NotNull
    @Override
    public String getEndpoint() {
        return "api/admin/show-users";
    }

    @Override
    public int getSuccessCode() {
        return 200;
    }
}
