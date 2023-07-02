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
 *   EDGE ::= METAINFO BINDINGS PRECONDITIONS -> CONSEQUENCES
 *   METAINFO ::= [CONTEXT] [DESCRIPTION]
 *   CONTEXT ::= class-descriptor | method-descriptor | field-descriptor
 *   DESCRIPTION ::= string-content
 *
 *   BINDINGS ::= (BINDING_NAME = ITEM_PATTERN)*
 *   BINDING_NAME ::= string-content
 *   BINDING_REFERENCE ::= BINDING_NAME
 *
 *   PRECONDITIONS ::= always | CONDITION+
 *   CONDITION ::= ITEM_REFERENCE
 *
 *   CONSEQUENCES ::= TARGET+
 *   TARGET ::= OPTIONS ITEM_REFERENCE
 *   OPTIONS ::= keep-all | OPTION+
 *   OPTION ::= shrinking | optimizing | obfuscating | access-modification | annotation-removal
 *
 *   ITEM_REFERENCE  ::= BINDING_REFERENCE | ITEM_PATTERN
 *   CLASS_REFERENCE ::= BINDING_REFERENCE | QUALIFIED_CLASS_NAME_PATTERN
 *
 *   ITEM_PATTERN
 *     ::= any
 *       | ITEM_KIND class CLASS_REFERENCE extends EXTENDS_PATTERN { MEMBER_PATTERN }
 *
 *   ITEM_KIND ::= ONLY_CLASS | ONLY_MEMBERS | CLASS_AND_MEMBERS
 *
 *   TYPE_PATTERN ::= any | exact type-descriptor
 *   PACKAGE_PATTERN ::= any | exact package-name
 *
 *   QUALIFIED_CLASS_NAME_PATTERN
 *     ::= any
 *       | PACKAGE_PATTERN UNQUALIFIED_CLASS_NAME_PATTERN
 *       | BINDING_REFERENCE
 *
 *   UNQUALIFIED_CLASS_NAME_PATTERN ::= any | exact simple-class-name
 *
 *   EXTENDS_PATTERN ::= any | QUALIFIED_CLASS_NAME_PATTERN
 *
 *   MEMBER_PATTERN ::= none | all | FIELD_PATTERN | METHOD_PATTERN
 *
 *   FIELD_PATTERN
 *     ::= FIELD_ACCESS_PATTERN
 *           FIELD_TYPE_PATTERN
 *           FIELD_NAME_PATTERN;
 *
 *   METHOD_PATTERN
 *     ::= METHOD_ACCESS_PATTERN
 *           METHOD_RETURN_TYPE_PATTERN
 *           METHOD_NAME_PATTERN
 *           METHOD_PARAMETERS_PATTERN
 *
 *   FIELD_ACCESS_PATTERN ::= any | FIELD_ACCESS_FLAG* | (!FIELD_ACCESS_FLAG)*
 *   FIELD_ACCESS_FLAG ::= MEMBER_ACCESS_FLAG | volatile | transient
 *
 *   METHOD_ACCESS_PATTERN ::= any | METHOD_ACCESS_FLAG* | (!METHOD_ACCESS_FLAG)*
 *   METHOD_NAME_PATTERN ::= any | exact method-name
 *   METHOD_RETURN_TYPE_PATTERN ::= void | TYPE_PATTERN
 *   METHOD_PARAMETERS_PATTERN ::= any | none | (TYPE_PATTERN+)
 *   METHOD_ACCESS_FLAG
 *     ::= MEMBER_ACCESS_FLAG
 *       | synchronized | bridge | native | abstract | strict-fp
 *
 *   MEMBER_ACCESS_FLAG
 *     ::= public | protected | package-private | private | static | final | synthetic
 * </pre>
 */
public final class KeepEdge extends KeepDeclaration {

  public static class Builder {
    private KeepEdgeMetaInfo metaInfo = KeepEdgeMetaInfo.none();
    private KeepBindings bindings = KeepBindings.none();
    private KeepPreconditions preconditions = KeepPreconditions.always();
    private KeepConsequences consequences;

    private Builder() {}

    public Builder setMetaInfo(KeepEdgeMetaInfo metaInfo) {
      this.metaInfo = metaInfo;
      return this;
    }

    public Builder setBindings(KeepBindings bindings) {
      this.bindings = bindings;
      return this;
    }

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
      return new KeepEdge(metaInfo, bindings, preconditions, consequences);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private final KeepEdgeMetaInfo metaInfo;
  private final KeepBindings bindings;
  private final KeepPreconditions preconditions;
  private final KeepConsequences consequences;

  private KeepEdge(
      KeepEdgeMetaInfo metaInfo,
      KeepBindings bindings,
      KeepPreconditions preconditions,
      KeepConsequences consequences) {
    assert metaInfo != null;
    assert bindings != null;
    assert preconditions != null;
    assert consequences != null;
    this.metaInfo = metaInfo;
    this.bindings = bindings;
    this.preconditions = preconditions;
    this.consequences = consequences;
  }

  @Override
  public KeepEdge asKeepEdge() {
    return this;
  }

  public KeepEdgeMetaInfo getMetaInfo() {
    return metaInfo;
  }

  public KeepBindings getBindings() {
    return bindings;
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
