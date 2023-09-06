// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import java.util.List;

public class MaximumRemovedAndroidLogLevelRule extends ProguardConfigurationRule {

  public static final String RULE_NAME = "maximumremovedandroidloglevel";

  public static final int NOT_SET = 0;
  public static final int NONE = 1;
  public static final int VERBOSE = 2;
  public static final int DEBUG = 3;
  public static final int INFO = 4;
  public static final int WARN = 5;
  public static final int ERROR = 6;
  public static final int ASSERT = 7;

  @SuppressWarnings("NonCanonicalType")
  public static class Builder
      extends ProguardConfigurationRule.Builder<MaximumRemovedAndroidLogLevelRule, Builder> {

    private int maxRemovedAndroidLogLevel;

    private Builder() {
      super();
    }

    public Builder setMaxRemovedAndroidLogLevel(int maxRemovedAndroidLogLevel) {
      this.maxRemovedAndroidLogLevel = maxRemovedAndroidLogLevel;
      return this;
    }

    @Override
    public Builder self() {
      return this;
    }

    @Override
    public MaximumRemovedAndroidLogLevelRule build() {
      assert maxRemovedAndroidLogLevel >= NONE;
      return new MaximumRemovedAndroidLogLevelRule(
          origin,
          getPosition(),
          source,
          buildClassAnnotations(),
          classAccessFlags,
          negatedClassAccessFlags,
          classTypeNegated,
          classType,
          classNames,
          buildInheritanceAnnotations(),
          inheritanceClassName,
          inheritanceIsExtends,
          memberRules,
          maxRemovedAndroidLogLevel);
    }
  }

  private int maxRemovedAndroidLogLevel;

  protected MaximumRemovedAndroidLogLevelRule(
      Origin origin,
      Position position,
      String source,
      List<ProguardTypeMatcher> classAnnotations,
      ProguardAccessFlags classAccessFlags,
      ProguardAccessFlags negatedClassAccessFlags,
      boolean classTypeNegated,
      ProguardClassType classType,
      ProguardClassNameList classNames,
      List<ProguardTypeMatcher> inheritanceAnnotations,
      ProguardTypeMatcher inheritanceClassName,
      boolean inheritanceIsExtends,
      List<ProguardMemberRule> memberRules,
      int maxRemovedAndroidLogLevel) {
    super(
        origin,
        position,
        source,
        classAnnotations,
        classAccessFlags,
        negatedClassAccessFlags,
        classTypeNegated,
        classType,
        classNames,
        inheritanceAnnotations,
        inheritanceClassName,
        inheritanceIsExtends,
        memberRules);
    this.maxRemovedAndroidLogLevel = maxRemovedAndroidLogLevel;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static int joinMaxRemovedAndroidLogLevel(int a, int b) {
    if (a == NOT_SET) {
      return b;
    }
    if (b == NOT_SET) {
      return a;
    }
    // If multiple maximum log levels are provided for the same method, the minimum between them
    // is selected as the maximum removed log level
    return Math.min(a, b);
  }

  public int getMaxRemovedAndroidLogLevel() {
    return maxRemovedAndroidLogLevel;
  }

  @Override
  public boolean isMaximumRemovedAndroidLogLevelRule() {
    return true;
  }

  @Override
  public MaximumRemovedAndroidLogLevelRule asMaximumRemovedAndroidLogLevelRule() {
    return this;
  }

  @Override
  String typeString() {
    return RULE_NAME;
  }

  @Override
  String typeSuffix() {
    return Integer.toString(maxRemovedAndroidLogLevel);
  }
}
