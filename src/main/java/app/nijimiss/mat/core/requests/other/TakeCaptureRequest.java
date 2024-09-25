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

package app.nijimiss.mat.core.requests.other;

import app.nijimiss.mat.core.requests.ApiRequest;
import app.nijimiss.mat.core.requests.HttpRequestMethod;
import okhttp3.RequestBody;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TakeCaptureRequest implements ApiRequest {
    private final String url;
    private final Boolean isForceTake;

    public TakeCaptureRequest(@NotNull String url, Boolean isForceTake) {
        this.url = url;
        this.isForceTake = isForceTake;
    }

    @Override
    public @NotNull String getEndpoint() {
        return "capture?url=" + url + "&isForceTake=" + isForceTake;
    }

    @Override
    public int getSuccessCode() {
        return 200;
    }

    @Override
    public @NotNull HttpRequestMethod getMethod() {
        return HttpRequestMethod.GET;
    }

    @Override
    public @Nullable RequestBody getBody() {
        return null;
    }
}
