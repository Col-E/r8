// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

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
    this.preconditions = preconditions;
    this.consequences = consequences;
  }

  public KeepPreconditions getPreconditions() {
    return preconditions;
  }

  public KeepConsequences getConsequences() {
    return consequences;
  }
}
