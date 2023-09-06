// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;

public abstract class KeepMethodParametersPattern {

  public static Builder builder() {
    return new Builder();
  }

  public static KeepMethodParametersPattern any() {
    return Any.getInstance();
  }

  public static KeepMethodParametersPattern none() {
    return Some.EMPTY_INSTANCE;
  }

  private KeepMethodParametersPattern() {}

  public boolean isAny() {
    return false;
  }

  public boolean isList() {
    return asList() != null;
  }

  public List<KeepTypePattern> asList() {
    return null;
  }

  public static class Builder {
    ImmutableList.Builder<KeepTypePattern> parameterPatterns = ImmutableList.builder();

    private Builder() {}

    public Builder addParameterTypePattern(KeepTypePattern typePattern) {
      parameterPatterns.add(typePattern);
      return this;
    }

    public KeepMethodParametersPattern build() {
      List<KeepTypePattern> list = parameterPatterns.build();
      if (list.isEmpty()) {
        return Some.EMPTY_INSTANCE;
      }
      return new Some(list);
    }
  }

  private static class Some extends KeepMethodParametersPattern {

    private static final Some EMPTY_INSTANCE = new Some(Collections.emptyList());

    private final List<KeepTypePattern> parameterPatterns;

    private Some(List<KeepTypePattern> parameterPatterns) {
      assert parameterPatterns != null;
      this.parameterPatterns = parameterPatterns;
    }

    @Override
    public List<KeepTypePattern> asList() {
      return parameterPatterns;
    }

    @Override
    @SuppressWarnings("EqualsGetClass")
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Some that = (Some) o;
      return parameterPatterns.equals(that.parameterPatterns);
    }

    @Override
    public int hashCode() {
      return parameterPatterns.hashCode();
    }
  }

  private static class Any extends KeepMethodParametersPattern {
    private static final Any INSTANCE = new Any();

    public static Any getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isAny() {
      return true;
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public String toString() {
      return "(...)";
    }
  }
}
