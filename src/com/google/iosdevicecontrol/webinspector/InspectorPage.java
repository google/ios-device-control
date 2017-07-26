// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.iosdevicecontrol.webinspector;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;

/** Information about a page in an inspector message. */
@AutoValue
public abstract class InspectorPage extends MessageDict {
  /** Returns a new builder. */
  public static Builder builder() {
    return new AutoValue_InspectorPage.Builder();
  }

  /** A builder for creating inspector pages. */
  @AutoValue.Builder
  public abstract static class Builder extends MessageDict.Builder {
    @Override
    public final Builder connectionId(String connectionId) {
      return optionalConnectionId(connectionId);
    }

    @Override
    public abstract Builder pageId(int pageId);

    @Override
    public abstract Builder title(String title);

    @Override
    public abstract Builder type(String type);

    @Override
    public abstract Builder url(String url);

    @Override
    public abstract InspectorPage build();

    abstract Builder optionalConnectionId(String connectionId);
  }

  public abstract Optional<String> optionalConnectionId();

  @Override
  public abstract int pageId();

  @Override
  public abstract String title();

  @Override
  public abstract String type();

  @Override
  public abstract String url();

  @Override
  final String connectionId() {
    return fromOptional(optionalConnectionId());
  }
}
