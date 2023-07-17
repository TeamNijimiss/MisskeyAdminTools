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
import org.jetbrains.annotations.Nullable;

public class Add extends RequireCredentialRequest {

    public Add(@NotNull String name,
               @NotNull String[] aliases,
               @NotNull String fileId,
               @Nullable String category,
               @Nullable String license,
               boolean isSensitive,
               boolean localOnly,
               @NotNull String[] roleIdsThatCanBeUsedThisEmojiAsReaction) {
        add("name", name);
        add("aliases", aliases);
        add("fileId", fileId);
        add("category", category);
        add("license", license);
        add("isSensitive", isSensitive);
        add("localOnly", localOnly);
        add("roleIdsThatCanBeUsedThisEmojiAsReaction", roleIdsThatCanBeUsedThisEmojiAsReaction);
    }

    @NotNull
    @Override
    public String getEndpoint() {
        return "api/admin/emoji/add";
    }

    @Override
    public int getSuccessCode() {
        return 200;
    }
}
