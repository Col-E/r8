// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

public class ProguardKeepRuleModifiers {
  public static class Builder {

    private boolean allowsAccessModification = false;
    private boolean allowsShrinking = false;
    private boolean allowsOptimization = false;
    private boolean allowsObfuscation = false;
    private boolean includeDescriptorClasses = false;

    private Builder() {}

    public Builder setAllowsAccessModification(boolean allowsAccessModification) {
      this.allowsAccessModification = allowsAccessModification;
      return this;
    }

    public void setAllowsShrinking(boolean allowsShrinking) {
      this.allowsShrinking = allowsShrinking;
    }

    public void setAllowsOptimization(boolean allowsOptimization) {
      this.allowsOptimization = allowsOptimization;
    }

    public Builder setAllowsObfuscation(boolean allowsObfuscation) {
      this.allowsObfuscation = allowsObfuscation;
      return this;
    }

    public void setIncludeDescriptorClasses(boolean includeDescriptorClasses) {
      this.includeDescriptorClasses = includeDescriptorClasses;
    }

    ProguardKeepRuleModifiers build() {
      return new ProguardKeepRuleModifiers(
          allowsAccessModification,
          allowsShrinking,
          allowsOptimization,
          allowsObfuscation,
          includeDescriptorClasses);
    }
  }

  public final boolean allowsAccessModification;
  public final boolean allowsShrinking;
  public final boolean allowsOptimization;
  public final boolean allowsObfuscation;
  public final boolean includeDescriptorClasses;

  private ProguardKeepRuleModifiers(
      boolean allowsAccessModification,
      boolean allowsShrinking,
      boolean allowsOptimization,
      boolean allowsObfuscation,
      boolean includeDescriptorClasses) {
    this.allowsAccessModification = allowsAccessModification;
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

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ProguardKeepRuleModifiers)) {
      return false;
    }
    ProguardKeepRuleModifiers that = (ProguardKeepRuleModifiers) o;
    return allowsAccessModification == that.allowsAccessModification
        && allowsShrinking == that.allowsShrinking
        && allowsOptimization == that.allowsOptimization
        && allowsObfuscation == that.allowsObfuscation
        && includeDescriptorClasses == that.includeDescriptorClasses;
  }

  @Override
  public int hashCode() {
    return (allowsAccessModification ? 1 : 0)
        | (allowsShrinking ? 2 : 0)
        | (allowsOptimization ? 4 : 0)
        | (allowsObfuscation ? 8 : 0)
        | (includeDescriptorClasses ? 16 : 0);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    appendWithComma(builder, allowsAccessModification, "allowaccessmodification");
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
