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

package app.nijimiss.mat.core.requests.misskey.endpoints.drive.files;

import app.nijimiss.mat.core.requests.misskey.RequireCredentialFileUploadRequest;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class Create extends RequireCredentialFileUploadRequest {

    public Create(String credential, String folderId, String name, String comment, boolean isSensitive, boolean force, File file) {
        super(credential);
        add("folderId", folderId);
        add("name", name);
        add("comment", comment);
        add("isSensitive", isSensitive);
        add("force", force);
        addFile("file", file);
    }

    public Create(String credential, String folderId, String name, String comment, boolean isSensitive, File file) {
        this(credential, folderId, name, comment, isSensitive, false, file);
    }

    public Create(String credential, String name, String comment, boolean isSensitive, File file) {
        this(credential, null, name, comment, isSensitive, file);
    }

    public Create(String credential, String name, boolean isSensitive, File file) {
        this(credential, name, null, isSensitive, file);
    }

    public Create(String credential, String name, File file) {
        this(credential, name, false, file);
    }


    @NotNull
    @Override
    public String getEndpoint() {
        return "api/drive/files/create";
    }

    @Override
    public int getSuccessCode() {
        return 200;
    }
}
