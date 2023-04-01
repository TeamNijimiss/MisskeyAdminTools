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

package app.nijimiss.mat.core.requests.misskey.endpoints.admin;

import app.nijimiss.mat.core.requests.misskey.RequireCredentialRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AbuseUserReports extends RequireCredentialRequest {
    public AbuseUserReports(@NotNull String credential,
                            int limit,
                            @Nullable String sinceId,
                            @Nullable String untilId,
                            @Nullable String state,
                            @Nullable String reporterOrigin,
                            @Nullable String targetUserOrigin,
                            boolean forwarded) {
        super(credential);

        if (limit < 1 || limit > 100)
            throw new IllegalArgumentException("limit must be between 1 and 100");

        add("limit", limit);
        add("sinceId", sinceId);
        add("untilId", untilId);
        add("state", state);
        add("reporterOrigin", reporterOrigin);
        add("targetUserOrigin", targetUserOrigin);
        add("forwarded", forwarded);
    }

    public AbuseUserReports(@NotNull String credential) {
        this(credential, 10, null, null, null, null, null, false);
    }

    public AbuseUserReports(@NotNull String credential,
                            int limit,
                            @Nullable String sinceId,
                            @Nullable String untilId) {
        this(credential, limit, sinceId, untilId, null, null, null, false);
    }


    @Override
    public String getEndpoint() {
        return "api/admin/abuse-user-reports";
    }

    @Override
    public int getSuccessCode() {
        return 200;
    }
}
