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

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/** Skeletal support for a message containing an application. */
abstract class ApplicationMessage extends InspectorMessage {
  /** Abstract builder for application messages. */
  abstract static class Builder<B extends Builder<B>> extends InspectorMessage.Builder {
    @Override
    public abstract B applicationBundleId(String applicationBundleId);

    @Override
    public abstract B applicationId(String applicationId);

    @Override
    public abstract B applicationName(String applicationName);

    @Override
    public final B hostApplicationId(String hostApplicationId) {
      return optionalHostApplicationId(hostApplicationId);
    }

    @Override
    public abstract B isApplicationActive(boolean isApplicationActive);

    @Override
    public abstract B isApplicationProxy(boolean isApplicationProxy);

    @Override
    public final B isApplicationReady(boolean isApplicationReady) {
      return optionalIsApplicationReady(isApplicationReady);
    }

    @Override
    public final B remoteAutomationEnabled(boolean remoteAutomationEnabled) {
      return optionalRemoteAutomationEnabled(remoteAutomationEnabled);
    }

    abstract B optionalHostApplicationId(String hostApplicationId);

    abstract B optionalIsApplicationReady(boolean isApplicationReady);

    abstract B optionalRemoteAutomationEnabled(boolean remoteAutomationEnabled);
  }

  private final Supplier<InspectorApplication> application =
      Suppliers.memoize(
          () -> {
            InspectorApplication.Builder builder =
                InspectorApplication.builder()
                    .applicationBundleId(applicationBundleId())
                    .applicationId(applicationId())
                    .applicationName(applicationName())
                    .isApplicationActive(isApplicationActive())
                    .isApplicationProxy(isApplicationProxy());
            if (optionalHostApplicationId().isPresent()) {
              builder.hostApplicationId(optionalHostApplicationId().get());
            }
            if (optionalIsApplicationReady().isPresent()) {
              builder.isApplicationReady(optionalIsApplicationReady().get());
            }
            if (optionalRemoteAutomationEnabled().isPresent()) {
              builder.remoteAutomationEnabled(optionalRemoteAutomationEnabled().get());
            }
            return builder.build();
          });

  /** Returns the properties of this message as an application dictionary. */
  public InspectorApplication asApplication() {
    return application.get();
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
