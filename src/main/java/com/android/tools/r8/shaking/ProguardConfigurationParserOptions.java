// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.SystemPropertyUtils.parseSystemPropertyOrDefault;

public class ProguardConfigurationParserOptions {

  private final boolean enableExperimentalCheckEnumUnboxed;
  private final boolean enableExperimentalConvertCheckNotNull;
  private final boolean enableExperimentalWhyAreYouNotInlining;
  private final boolean enableTestingOptions;

  ProguardConfigurationParserOptions(
      boolean enableExperimentalCheckEnumUnboxed,
      boolean enableExperimentalConvertCheckNotNull,
      boolean enableExperimentalWhyAreYouNotInlining,
      boolean enableTestingOptions) {
    this.enableExperimentalCheckEnumUnboxed = enableExperimentalCheckEnumUnboxed;
    this.enableExperimentalConvertCheckNotNull = enableExperimentalConvertCheckNotNull;
    this.enableExperimentalWhyAreYouNotInlining = enableExperimentalWhyAreYouNotInlining;
    this.enableTestingOptions = enableTestingOptions;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isExperimentalCheckEnumUnboxedEnabled() {
    return enableExperimentalCheckEnumUnboxed;
  }

  public boolean isExperimentalConvertCheckNotNullEnabled() {
    return enableExperimentalConvertCheckNotNull;
  }

  public boolean isExperimentalWhyAreYouNotInliningEnabled() {
    return enableExperimentalWhyAreYouNotInlining;
  }

  public boolean isTestingOptionsEnabled() {
    return enableTestingOptions;
  }

  public static class Builder {

    private boolean enableExperimentalCheckEnumUnboxed;
    private boolean enableExperimentalConvertCheckNotNull;
    private boolean enableExperimentalWhyAreYouNotInlining;
    private boolean enableTestingOptions;

    public Builder readEnvironment() {
      enableExperimentalCheckEnumUnboxed =
          parseSystemPropertyOrDefault(
              "com.android.tools.r8.experimental.enablecheckenumunboxed", false);
      enableExperimentalConvertCheckNotNull =
          parseSystemPropertyOrDefault(
              "com.android.tools.r8.experimental.enableconvertchecknotnull", false);
      enableExperimentalWhyAreYouNotInlining =
          parseSystemPropertyOrDefault(
              "com.android.tools.r8.experimental.enablewhyareyounotinlining", false);
      enableTestingOptions =
          parseSystemPropertyOrDefault("com.android.tools.r8.allowTestProguardOptions", false);
      return this;
    }

    public Builder setEnableExperimentalCheckEnumUnboxed(
        boolean enableExperimentalCheckEnumUnboxed) {
      this.enableExperimentalCheckEnumUnboxed = enableExperimentalCheckEnumUnboxed;
      return this;
    }

    public Builder setEnableExperimentalConvertCheckNotNull(
        boolean enableExperimentalConvertCheckNotNull) {
      this.enableExperimentalConvertCheckNotNull = enableExperimentalConvertCheckNotNull;
      return this;
    }

    public Builder setEnableExperimentalWhyAreYouNotInlining(
        boolean enableExperimentalWhyAreYouNotInlining) {
      this.enableExperimentalWhyAreYouNotInlining = enableExperimentalWhyAreYouNotInlining;
      return this;
    }

    public Builder setEnableTestingOptions(boolean enableTestingOptions) {
      this.enableTestingOptions = enableTestingOptions;
      return this;
    }

    public ProguardConfigurationParserOptions build() {
      return new ProguardConfigurationParserOptions(
          enableExperimentalCheckEnumUnboxed,
          enableExperimentalConvertCheckNotNull,
          enableExperimentalWhyAreYouNotInlining,
          enableTestingOptions);
    }
  }
}
