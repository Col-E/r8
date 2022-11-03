// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

import java.util.Objects;

/**
 * An edge in the keep graph.
 *
 * <p>An edge describes a set of preconditions and a set of consequences. If the preconditions are
 * met, then the consequences are put into effect.
 */
public final class KeepEdge {

  public static class Builder {
    private KeepPreconditions preconditions = KeepPreconditions.always();
    private KeepConsequences consequences;

    private Builder() {}

    public Builder setPreconditions(KeepPreconditions preconditions) {
      this.preconditions = preconditions;
      return this;
    }

    public Builder setConsequences(KeepConsequences consequences) {
      this.consequences = consequences;
      return this;
    }

    public KeepEdge build() {
      if (consequences.isEmpty()) {
        throw new KeepEdgeException("KeepEdge must have non-empty set of consequences.");
      }
      return new KeepEdge(preconditions, consequences);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private final KeepPreconditions preconditions;
  private final KeepConsequences consequences;

  private KeepEdge(KeepPreconditions preconditions, KeepConsequences consequences) {
    assert preconditions != null;
    assert consequences != null;
    this.preconditions = preconditions;
    this.consequences = consequences;
  }

  public KeepPreconditions getPreconditions() {
    return preconditions;
  }

  public KeepConsequences getConsequences() {
    return consequences;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    KeepEdge keepEdge = (KeepEdge) o;
    return preconditions.equals(keepEdge.preconditions)
        && consequences.equals(keepEdge.consequences);
  }

  @Override
  public int hashCode() {
    return Objects.hash(preconditions, consequences);
  }

  @Override
  public String toString() {
    return "KeepEdge{" + "preconditions=" + preconditions + ", consequences=" + consequences + '}';
  }
}
