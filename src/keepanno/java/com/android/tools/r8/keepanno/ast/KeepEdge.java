// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import java.util.Objects;

/**
 * An edge in the keep graph.
 *
 * <p>An edge describes a set of preconditions and a set of consequences. If the preconditions are
 * met, then the consequences are put into effect.
 *
 * <p>Below is a BNF of the keep edge AST for reference. The non-terminals are written in ALL_CAPS,
 * possibly-empty repeatable subexpressions denoted with SUB* and non-empty with SUB+
 *
 * <p>In the Java AST, the non-terminals are prefixed with 'Keep' and in CamelCase.
 *
 * <p>TODO(b/248408342): Update the BNF and AST to be complete.
 *
 * <pre>
 *   EDGE ::= PRECONDITIONS -> CONSEQUENCES
 *
 *   PRECONDITIONS ::= always | CONDITION+
 *   CONDITION ::= ITEM_PATTERN
 *
 *   CONSEQUENCES ::= TARGET+
 *   TARGET ::= OPTIONS ITEM_PATTERN
 *   OPTIONS ::= keep-all | OPTION+
 *   OPTION ::= shrinking | optimizing | obfuscating | access-modifying
 *
 *   ITEM_PATTERN
 *     ::= any
 *       | class QUALIFIED_CLASS_NAME_PATTERN extends EXTENDS_PATTERN { MEMBER_PATTERN }
 *
 *   TYPE_PATTERN ::= any
 *   PACKAGE_PATTERN ::= any | exact package-name
 *   QUALIFIED_CLASS_NAME_PATTERN ::= any | PACKAGE_PATTERN | UNQUALIFIED_CLASS_NAME_PATTERN
 *   UNQUALIFIED_CLASS_NAME_PATTERN ::= any | exact simple-class-name
 *   EXTENDS_PATTERN ::= any | QUALIFIED_CLASS_NAME_PATTERN
 *
 *   MEMBER_PATTERN ::= none | all | METHOD_PATTERN
 *
 *   METHOD_PATTERN
 *     ::= METHOD_ACCESS_PATTERN
 *           METHOD_RETURN_TYPE_PATTERN
 *           METHOD_NAME_PATTERN
 *           METHOD_PARAMETERS_PATTERN
 *
 *   METHOD_ACCESS_PATTERN ::= any
 *   METHOD_NAME_PATTERN ::= any | exact method-name
 *   METHOD_RETURN_TYPE_PATTERN ::= void | TYPE_PATTERN
 *   METHOD_PARAMETERS_PATTERN ::= any | none | (TYPE_PATTERN+)
 * </pre>
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
