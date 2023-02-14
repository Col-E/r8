// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.keeprules.RulePrinter;
import com.android.tools.r8.keepanno.keeprules.RulePrintingUtils;
import java.util.Set;

public class KeepMethodAccessPattern extends KeepMemberAccessPattern {

  private static final KeepMethodAccessPattern ANY =
      new KeepMethodAccessPattern(
          AccessVisibility.all(),
          ModifierPattern.any(),
          ModifierPattern.any(),
          ModifierPattern.any(),
          ModifierPattern.any(),
          ModifierPattern.any(),
          ModifierPattern.any(),
          ModifierPattern.any(),
          ModifierPattern.any());

  public static KeepMethodAccessPattern anyMethodAccess() {
    return ANY;
  }

  public static Builder builder() {
    return new Builder();
  }

  private final ModifierPattern synchronizedPattern;
  private final ModifierPattern bridgePattern;
  private final ModifierPattern nativePattern;
  private final ModifierPattern abstractPattern;
  private final ModifierPattern strictFpPattern;

  public KeepMethodAccessPattern(
      Set<AccessVisibility> allowedVisibilities,
      ModifierPattern staticPattern,
      ModifierPattern finalPattern,
      ModifierPattern synchronizedPattern,
      ModifierPattern bridgePattern,
      ModifierPattern nativePattern,
      ModifierPattern abstractPattern,
      ModifierPattern syntheticPattern,
      ModifierPattern strictFpPattern) {
    super(allowedVisibilities, staticPattern, finalPattern, syntheticPattern);
    this.synchronizedPattern = synchronizedPattern;
    this.bridgePattern = bridgePattern;
    this.nativePattern = nativePattern;
    this.abstractPattern = abstractPattern;
    this.strictFpPattern = strictFpPattern;
  }

  @Override
  public String toString() {
    if (isAny()) {
      return "*";
    }
    StringBuilder builder = new StringBuilder();
    RulePrintingUtils.printMethodAccess(RulePrinter.withoutBackReferences(builder), this);
    return builder.toString();
  }

  @Override
  public boolean isAny() {
    return super.isAny()
        && synchronizedPattern.isAny()
        && bridgePattern.isAny()
        && nativePattern.isAny()
        && abstractPattern.isAny()
        && strictFpPattern.isAny();
  }

  public ModifierPattern getSynchronizedPattern() {
    return synchronizedPattern;
  }

  public ModifierPattern getBridgePattern() {
    return bridgePattern;
  }

  public ModifierPattern getNativePattern() {
    return nativePattern;
  }

  public ModifierPattern getAbstractPattern() {
    return abstractPattern;
  }

  public ModifierPattern getStrictFpPattern() {
    return strictFpPattern;
  }

  public static class Builder extends BuilderBase<KeepMethodAccessPattern, Builder> {
    private ModifierPattern synchronizedPattern = ModifierPattern.any();
    private ModifierPattern bridgePattern = ModifierPattern.any();
    private ModifierPattern nativePattern = ModifierPattern.any();
    private ModifierPattern abstractPattern = ModifierPattern.any();
    private ModifierPattern strictFpPattern = ModifierPattern.any();

    private Builder() {}

    @Override
    public Builder self() {
      return this;
    }

    @Override
    public KeepMethodAccessPattern build() {
      KeepMethodAccessPattern pattern =
          new KeepMethodAccessPattern(
              getAllowedVisibilities(),
              getStaticPattern(),
              getFinalPattern(),
              getSynchronizedPattern(),
              getBridgePattern(),
              getNativePattern(),
              getAbstractPattern(),
              getSyntheticPattern(),
              getStrictFpPattern());
      return pattern.isAny() ? anyMethodAccess() : pattern;
    }

    public Builder setSynchronized(boolean allow) {
      synchronizedPattern = ModifierPattern.fromAllowValue(allow);
      return this;
    }

    public Builder setBridge(boolean allow) {
      bridgePattern = ModifierPattern.fromAllowValue(allow);
      return this;
    }

    public Builder setNative(boolean allow) {
      nativePattern = ModifierPattern.fromAllowValue(allow);
      return this;
    }

    public Builder setAbstract(boolean allow) {
      abstractPattern = ModifierPattern.fromAllowValue(allow);
      return this;
    }

    public Builder setStrictFp(boolean allow) {
      strictFpPattern = ModifierPattern.fromAllowValue(allow);
      return this;
    }

    public ModifierPattern getSynchronizedPattern() {
      return synchronizedPattern;
    }

    public ModifierPattern getBridgePattern() {
      return bridgePattern;
    }

    public ModifierPattern getNativePattern() {
      return nativePattern;
    }

    public ModifierPattern getAbstractPattern() {
      return abstractPattern;
    }

    public ModifierPattern getStrictFpPattern() {
      return strictFpPattern;
    }
  }
}
