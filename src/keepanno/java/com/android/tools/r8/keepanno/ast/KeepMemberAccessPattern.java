// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.keeprules.RulePrinter;
import com.android.tools.r8.keepanno.keeprules.RulePrintingUtils;
import java.util.Set;

public class KeepMemberAccessPattern {

  private static final KeepMemberAccessPattern ANY =
      new KeepMemberAccessPattern(
          AccessVisibility.all(),
          ModifierPattern.any(),
          ModifierPattern.any(),
          ModifierPattern.any());

  public static KeepMemberAccessPattern anyMemberAccess() {
    return ANY;
  }

  public static Builder memberBuilder() {
    return new Builder();
  }

  private final Set<AccessVisibility> allowedVisibilities;
  private final ModifierPattern staticPattern;
  private final ModifierPattern finalPattern;
  private final ModifierPattern syntheticPattern;

  public KeepMemberAccessPattern(
      Set<AccessVisibility> allowedVisibilities,
      ModifierPattern staticPattern,
      ModifierPattern finalPattern,
      ModifierPattern syntheticPattern) {
    this.allowedVisibilities = allowedVisibilities;
    this.staticPattern = staticPattern;
    this.finalPattern = finalPattern;
    this.syntheticPattern = syntheticPattern;
  }

  @Override
  public String toString() {
    if (isAny()) {
      return "*";
    }
    StringBuilder builder = new StringBuilder();
    RulePrinter printer = RulePrinter.withoutBackReferences(builder);
    RulePrintingUtils.printMemberAccess(printer, this);
    return builder.toString();
  }

  /** True if this matches any possible access flag. */
  public boolean isAny() {
    return isAnyVisibility()
        && staticPattern.isAny()
        && finalPattern.isAny()
        && syntheticPattern.isAny();
  }

  public boolean isAnyVisibility() {
    return AccessVisibility.containsAll(allowedVisibilities);
  }

  public boolean isVisibilityAllowed(AccessVisibility visibility) {
    return allowedVisibilities.contains(visibility);
  }

  public Set<AccessVisibility> getAllowedAccessVisibilities() {
    return allowedVisibilities;
  }

  public ModifierPattern getStaticPattern() {
    return staticPattern;
  }

  public ModifierPattern getFinalPattern() {
    return finalPattern;
  }

  public ModifierPattern getSyntheticPattern() {
    return syntheticPattern;
  }

  public static class Builder extends BuilderBase<KeepMemberAccessPattern, Builder> {

    @Override
    public Builder self() {
      return this;
    }

    @Override
    public KeepMemberAccessPattern build() {
      Set<AccessVisibility> allowedVisibilities = getAllowedVisibilities();
      ModifierPattern staticPattern = getStaticPattern();
      ModifierPattern finalPattern = getFinalPattern();
      ModifierPattern syntheticPattern = getSyntheticPattern();
      if (AccessVisibility.containsAll(allowedVisibilities)
          && staticPattern.isAny()
          && finalPattern.isAny()
          && syntheticPattern.isAny()) {
        return KeepMemberAccessPattern.anyMemberAccess();
      }
      KeepMemberAccessPattern pattern =
          new KeepMemberAccessPattern(
              allowedVisibilities, staticPattern, finalPattern, syntheticPattern);
      assert !pattern.isAny();
      return pattern;
    }
  }

  public abstract static class BuilderBase<
      M extends KeepMemberAccessPattern, B extends BuilderBase<M, B>> {
    private final Set<AccessVisibility> allowed = AccessVisibility.createSet();
    private final Set<AccessVisibility> disallowed = AccessVisibility.createSet();
    private ModifierPattern staticPattern = ModifierPattern.any();
    private ModifierPattern finalPattern = ModifierPattern.any();
    private ModifierPattern syntheticPattern = ModifierPattern.any();

    BuilderBase() {}

    public abstract B self();

    public abstract M build();

    public B copyOfMemberAccess(KeepMemberAccessPattern accessPattern) {
      allowed.clear();
      disallowed.clear();
      allowed.addAll(accessPattern.getAllowedAccessVisibilities());
      staticPattern = accessPattern.getStaticPattern();
      finalPattern = accessPattern.getFinalPattern();
      return self();
    }

    public ModifierPattern getStaticPattern() {
      return staticPattern;
    }

    public ModifierPattern getFinalPattern() {
      return finalPattern;
    }

    public ModifierPattern getSyntheticPattern() {
      return syntheticPattern;
    }

    public Set<AccessVisibility> getAllowedVisibilities() {
      // Fast path for any visibility pattern.
      if (allowed.isEmpty() && disallowed.isEmpty()) {
        return AccessVisibility.all();
      }
      // If no explict "allows" have been set, all visibilities are allowed.
      Set<AccessVisibility> result = AccessVisibility.createSet();
      if (allowed.isEmpty()) {
        result.addAll(AccessVisibility.all());
      } else {
        result.addAll(allowed);
      }
      // Any explict disallow narrows the allowed visibilities.
      result.removeAll(disallowed);
      if (result.isEmpty()) {
        throw new KeepEdgeException("Empty access visibility pattern will never match a member");
      }
      return result;
    }

    public B setAccessVisibility(AccessVisibility visibility, boolean allow) {
      Set<AccessVisibility> set = allow ? allowed : disallowed;
      set.add(visibility);
      return self();
    }

    public B setStatic(boolean allow) {
      staticPattern = ModifierPattern.fromAllowValue(allow);
      return self();
    }

    public B setFinal(boolean allow) {
      finalPattern = ModifierPattern.fromAllowValue(allow);
      return self();
    }

    public B setSynthetic(boolean allow) {
      syntheticPattern = ModifierPattern.fromAllowValue(allow);
      return self();
    }
  }
}
