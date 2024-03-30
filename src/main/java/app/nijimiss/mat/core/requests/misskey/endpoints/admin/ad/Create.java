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

package app.nijimiss.mat.core.requests.misskey.endpoints.admin.ad;

import app.nijimiss.mat.core.requests.misskey.RequireCredentialRequest;
import org.jetbrains.annotations.NotNull;

public class Create extends RequireCredentialRequest {

    public Create(@NotNull String url,
                  @NotNull String imageUrl,
                  long startsAt,
                  long expiresAt) {
        add("url", url);
        add("imageUrl", imageUrl);
        add("startsAt", startsAt);
        add("expiresAt", expiresAt);

        add("memo", "");
        add("place", "horizontal");
        add("priority", "middle");
        add("ratio", 1);
        add("dayOfWeek", 0);
    }

    @NotNull
    @Override
    public String getEndpoint() {
        return "api/admin/ad/create";
    }

    @Override
    public int getSuccessCode() {
        return 200;
    }
}
