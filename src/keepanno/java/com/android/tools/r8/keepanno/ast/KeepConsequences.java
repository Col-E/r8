// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Set of consequences of a keep edge.
 *
 * <p>The consequences are "targets" described by item patterns along with "keep options" which
 * detail what aspects of the items must be retained.
 *
 * <p>The consequences come into effect if the preconditions of an edge are met.
 */
public final class KeepConsequences {

  public static class Builder {

    private List<KeepTarget> targets = new ArrayList<>();

    private Builder() {}

    public Builder addTarget(KeepTarget target) {
      targets.add(target);
      return this;
    }

    public KeepConsequences build() {
      if (targets.isEmpty()) {
        throw new KeepEdgeException("Invalid empty consequent set");
      }
      return new KeepConsequences(targets);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private final List<KeepTarget> targets;

  private KeepConsequences(List<KeepTarget> targets) {
    assert targets != null;
    assert !targets.isEmpty();
    this.targets = targets;
  }

  public boolean isEmpty() {
    return targets.isEmpty();
  }

  public void forEachTarget(Consumer<KeepTarget> fn) {
    targets.forEach(fn);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    KeepConsequences that = (KeepConsequences) o;
    return targets.equals(that.targets);
  }

  @Override
  public int hashCode() {
    return targets.hashCode();
  }

  @Override
  public String toString() {
    return targets.stream().map(Object::toString).collect(Collectors.joining(", "));
  }
}
