// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import java.util.Objects;

public class ProguardKeepRuleModifiers {
  public static class Builder {

    private boolean allowsAccessModification = false;
    private boolean allowsAnnotationRemoval = false;
    private boolean allowsRepackaging = false;
    private boolean allowsShrinking = false;
    private boolean allowsOptimization = false;
    private boolean allowsObfuscation = false;
    private boolean includeDescriptorClasses = false;

    private Builder() {}

    public Builder setAllowsAll() {
      setAllowsAccessModification(true);
      setAllowsAnnotationRemoval(true);
      setAllowsObfuscation(true);
      setAllowsOptimization(true);
      setAllowsRepackaging(true);
      setAllowsShrinking(true);
      return this;
    }

    public Builder setAllowsAccessModification(boolean allowsAccessModification) {
      this.allowsAccessModification = allowsAccessModification;
      return this;
    }

    public Builder setAllowsAnnotationRemoval(boolean allowsAnnotationRemoval) {
      this.allowsAnnotationRemoval = allowsAnnotationRemoval;
      return this;
    }

    public Builder setAllowsShrinking(boolean allowsShrinking) {
      this.allowsShrinking = allowsShrinking;
      return this;
    }

    public Builder setAllowsOptimization(boolean allowsOptimization) {
      this.allowsOptimization = allowsOptimization;
      return this;
    }

    public Builder setAllowsObfuscation(boolean allowsObfuscation) {
      this.allowsObfuscation = allowsObfuscation;
      if (allowsObfuscation) {
        this.allowsRepackaging = true;
      }
      return this;
    }

    public Builder setAllowsRepackaging(boolean allowsRepackaging) {
      this.allowsRepackaging = allowsRepackaging;
      return this;
    }

    public void setIncludeDescriptorClasses(boolean includeDescriptorClasses) {
      this.includeDescriptorClasses = includeDescriptorClasses;
    }

    ProguardKeepRuleModifiers build() {
      return new ProguardKeepRuleModifiers(
          allowsAccessModification,
          allowsAnnotationRemoval,
          allowsRepackaging,
          allowsShrinking,
          allowsOptimization,
          allowsObfuscation,
          includeDescriptorClasses);
    }
  }

  public final boolean allowsAccessModification;
  public final boolean allowsAnnotationRemoval;
  public final boolean allowsRepackaging;
  public final boolean allowsShrinking;
  public final boolean allowsOptimization;
  public final boolean allowsObfuscation;
  public final boolean includeDescriptorClasses;

  private ProguardKeepRuleModifiers(
      boolean allowsAccessModification,
      boolean allowsAnnotationRemoval,
      boolean allowsRepackaging,
      boolean allowsShrinking,
      boolean allowsOptimization,
      boolean allowsObfuscation,
      boolean includeDescriptorClasses) {
    this.allowsAccessModification = allowsAccessModification;
    this.allowsAnnotationRemoval = allowsAnnotationRemoval;
    this.allowsRepackaging = allowsRepackaging;
    this.allowsShrinking = allowsShrinking;
    this.allowsOptimization = allowsOptimization;
    this.allowsObfuscation = allowsObfuscation;
    this.includeDescriptorClasses = includeDescriptorClasses;
  }

  /**
   * Create a new empty builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  public boolean isBottom() {
    return allowsAccessModification
        && allowsAnnotationRemoval
        && allowsRepackaging
        && allowsObfuscation
        && allowsOptimization
        && allowsShrinking
        && !includeDescriptorClasses;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ProguardKeepRuleModifiers)) {
      return false;
    }
    ProguardKeepRuleModifiers that = (ProguardKeepRuleModifiers) o;
    return allowsAccessModification == that.allowsAccessModification
        && allowsAnnotationRemoval == that.allowsAnnotationRemoval
        && allowsRepackaging == that.allowsRepackaging
        && allowsShrinking == that.allowsShrinking
        && allowsOptimization == that.allowsOptimization
        && allowsObfuscation == that.allowsObfuscation
        && includeDescriptorClasses == that.includeDescriptorClasses;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        allowsAccessModification,
        allowsAnnotationRemoval,
        allowsRepackaging,
        allowsShrinking,
        allowsOptimization,
        allowsObfuscation,
        includeDescriptorClasses);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    appendWithComma(builder, allowsAccessModification, "allowaccessmodification");
    appendWithComma(builder, allowsAnnotationRemoval, "allowannotationremoval");
    appendWithComma(builder, allowsRepackaging, "allowrepackaging");
    appendWithComma(builder, allowsObfuscation, "allowobfuscation");
    appendWithComma(builder, allowsShrinking, "allowshrinking");
    appendWithComma(builder, allowsOptimization, "allowoptimization");
    appendWithComma(builder, includeDescriptorClasses, "includedescriptorclasses");
    return builder.toString();
  }

  private void appendWithComma(StringBuilder builder, boolean predicate, String text) {
    if (!predicate) {
      return;
    }
    if (builder.length() != 0) {
      builder.append(',');
    }
    builder.append(text);
  }
}
