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

package app.nijimiss.mat.core.requests.misskey;

import app.nijimiss.mat.core.requests.FileUploadApiRequest;
import app.nijimiss.mat.core.requests.HttpRequestMethod;
import app.nijimiss.mat.core.requests.RequireCredentialApiRequest;
import org.jetbrains.annotations.NotNull;

public abstract class RequireCredentialFileUploadRequest extends FileUploadApiRequest implements RequireCredentialApiRequest {

    public RequireCredentialFileUploadRequest() {
    }

    @Override
    public void setToken(@NotNull String token) {
        super.add("i", token);
    }

    @Override
    public HttpRequestMethod getMethod() {
        return HttpRequestMethod.POST;
    }
}
