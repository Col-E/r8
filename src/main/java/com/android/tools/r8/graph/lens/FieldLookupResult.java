// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.lens;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import java.util.function.Function;

/**
 * Intermediate result of a field lookup that stores the actual non-rebound reference and the
 * rebound reference that points to the definition of the field.
 */
public class FieldLookupResult extends MemberLookupResult<DexField> {

  private final DexType readCastType;
  private final DexType writeCastType;

  private FieldLookupResult(
      DexField reference, DexField reboundReference, DexType readCastType, DexType writeCastType) {
    super(reference, reboundReference);
    this.readCastType = readCastType;
    this.writeCastType = writeCastType;
  }

  public static Builder builder(GraphLens lens) {
    return new Builder(lens);
  }

  public boolean hasReadCastType() {
    return readCastType != null;
  }

  public DexType getReadCastType() {
    return readCastType;
  }

  public DexType getRewrittenReadCastType(Function<DexType, DexType> fn) {
    return hasReadCastType() ? fn.apply(readCastType) : null;
  }

  public boolean hasWriteCastType() {
    return writeCastType != null;
  }

  public DexType getWriteCastType() {
    return writeCastType;
  }

  @SuppressWarnings("UnusedVariable")
  public DexType getRewrittenWriteCastType(Function<DexType, DexType> fn) {
    return hasWriteCastType() ? fn.apply(writeCastType) : null;
  }

  public static class Builder extends MemberLookupResult.Builder<DexField, Builder> {

    private DexType readCastType;
    private DexType writeCastType;

    @SuppressWarnings("UnusedVariable")
    private GraphLens lens;

    private Builder(GraphLens lens) {
      this.lens = lens;
    }

    public Builder setReadCastType(DexType readCastType) {
      this.readCastType = readCastType;
      return this;
    }

    public Builder setWriteCastType(DexType writeCastType) {
      this.writeCastType = writeCastType;
      return this;
    }

    @Override
    public Builder self() {
      return this;
    }

    public FieldLookupResult build() {
      // TODO(b/168282032): All non-identity graph lenses should set the rebound reference.
      return new FieldLookupResult(reference, reboundReference, readCastType, writeCastType);
    }
  }
}
