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

package app.nijimiss.mat.core.requests.misskey.endpoints.admin.announcements;

import app.nijimiss.mat.core.requests.misskey.RequireCredentialRequest;
import app.nijimiss.mat.core.requests.misskey.elements.announcement.Display;
import app.nijimiss.mat.core.requests.misskey.elements.announcement.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Create extends RequireCredentialRequest {

    public Create(@NotNull String title,
                  @NotNull String text,
                  @NotNull Icon icon,
                  @Nullable String imageUrl,
                  @NotNull Display display,
                  boolean needConfirmationToRead,
                  int closeDuration,
                  int displayOrder,
                  boolean silence,
                  @Nullable String userId) {
        add("title", title);
        add("text", text);
        add("icon", icon.getValue());
        add("imageUrl", imageUrl);
        add("display", display.getValue());
        add("needConfirmationToRead", needConfirmationToRead);
        add("closeDuration", closeDuration);
        add("displayOrder", displayOrder);
        add("silence", silence);
        add("userId", userId);
    }

    @NotNull
    @Override
    public String getEndpoint() {
        return "api/admin/announcements/create";
    }

    @Override
    public int getSuccessCode() {
        return 200;
    }


}
