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

package app.nijimiss.mat.core.requests.misskey.endpoints.notes;

import app.nijimiss.mat.core.requests.misskey.RequireCredentialRequest;
import org.jetbrains.annotations.NotNull;

public class Create extends RequireCredentialRequest {

    public Create(String credential,
                  Visibility visibility,
                  String[] visibleUserIds,
                  String text,
                  String cw,
                  String replyId,
                  String renoteId,
                  String[] fileIds,
                  String channelId,
                  boolean localOnly,
                  boolean noExtractMentions,
                  boolean noExtractHash,
                  boolean noExtractEmojis) {
        super(credential);
        add("visibility", visibility.name().toLowerCase());
        if (visibility == Visibility.SPECIFIED) add("visibleUserIds", visibleUserIds);
        add("text", text);
        add("cw", cw);
        add("replyId", replyId);
        add("renoteId", renoteId);
        add("fileIds", fileIds);
        add("channelId", channelId);
        add("localOnly", localOnly);
        add("noExtractMentions", noExtractMentions);
        add("noExtractHash", noExtractHash);
        add("noExtractEmojis", noExtractEmojis);
    }

    public Create(String credential,
                  String text) {
        super(credential);
        add("text", text);
    }

    @NotNull
    @Override
    public String getEndpoint() {
        return "api/notes/create";
    }

    @Override
    public int getSuccessCode() {
        return 200;
    }

    public enum Visibility {
        PUBLIC, HOME, FOLLOWERS, SPECIFIED
    }
}
