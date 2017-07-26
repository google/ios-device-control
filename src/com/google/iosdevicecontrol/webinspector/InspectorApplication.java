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

/** Information about an application in an inspector message. */
@AutoValue
public abstract class InspectorApplication extends MessageDict {
  /** Returns a new builder. */
  public static Builder builder() {
    return new AutoValue_InspectorApplication.Builder();
  }

  /** A builder for creating inspector applications. */
  @AutoValue.Builder
  public abstract static class Builder extends MessageDict.Builder {
    @Override
    public abstract Builder applicationBundleId(String applicationBundleId);

    @Override
    public abstract Builder applicationId(String applicationId);

    @Override
    public abstract Builder applicationName(String applicationName);

    @Override
    public final Builder hostApplicationId(String hostApplicationId) {
      return optionalHostApplicationId(hostApplicationId);
    }

    @Override
    public abstract Builder isApplicationActive(boolean isApplicationActive);

    @Override
    public abstract Builder isApplicationProxy(boolean isApplicationProxy);

    @Override
    public final Builder isApplicationReady(boolean isApplicationReady) {
      return optionalIsApplicationReady(isApplicationReady);
    }

    @Override
    public final Builder remoteAutomationEnabled(boolean remoteAutomationEnabled) {
      return optionalRemoteAutomationEnabled(remoteAutomationEnabled);
    }

    @Override
    public abstract InspectorApplication build();

    abstract Builder optionalHostApplicationId(String hostApplicationId);

    abstract Builder optionalIsApplicationReady(boolean isApplicationReady);

    abstract Builder optionalRemoteAutomationEnabled(boolean remoteAutomationEnabled);
  }

  @Override
  public abstract String applicationBundleId();

  @Override
  public abstract String applicationId();

  @Override
  public abstract String applicationName();

  public abstract Optional<String> optionalHostApplicationId();

  @Override
  public abstract boolean isApplicationActive();

  @Override
  public abstract boolean isApplicationProxy();

  public abstract Optional<Boolean> optionalIsApplicationReady();

  public abstract Optional<Boolean> optionalRemoteAutomationEnabled();

  @Override
  final String hostApplicationId() {
    return fromOptional(optionalHostApplicationId());
  }

  @Override
  final boolean isApplicationReady() {
    return fromOptional(optionalIsApplicationReady());
  }

  @Override
  final boolean remoteAutomationEnabled() {
    return fromOptional(optionalRemoteAutomationEnabled());
  }
}
