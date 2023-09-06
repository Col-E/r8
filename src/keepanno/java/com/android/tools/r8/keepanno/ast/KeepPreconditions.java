// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class KeepPreconditions {

  public abstract void forEach(Consumer<KeepCondition> fn);

  public static class Builder {

    private List<KeepCondition> preconditions = new ArrayList<>();

    private Builder() {}

    public Builder addCondition(KeepCondition condition) {
      preconditions.add(condition);
      return this;
    }

    public KeepPreconditions build() {
      return preconditions.isEmpty()
          ? KeepPreconditions.always()
          : new KeepPreconditionsSome(preconditions);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static KeepPreconditions always() {
    return Always.getInstance();
  }

  public abstract boolean isAlways();

  private static class Always extends KeepPreconditions {

    private static final Always INSTANCE = new Always();

    public static Always getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isAlways() {
      return true;
    }

    @Override
    public void forEach(Consumer<KeepCondition> fn) {
      // Empty.
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
      return "true";
    }
  }

  private static class KeepPreconditionsSome extends KeepPreconditions {

    private final List<KeepCondition> preconditions;

    private KeepPreconditionsSome(List<KeepCondition> preconditions) {
      assert preconditions != null;
      assert !preconditions.isEmpty();
      this.preconditions = preconditions;
    }

    @Override
    public boolean isAlways() {
      return false;
    }

    @Override
    public void forEach(Consumer<KeepCondition> fn) {
      preconditions.forEach(fn);
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
      KeepPreconditionsSome that = (KeepPreconditionsSome) o;
      return preconditions.equals(that.preconditions);
    }

    @Override
    public int hashCode() {
      return preconditions.hashCode();
    }

    @Override
    public String toString() {
      return preconditions.toString();
    }
  }
}
