// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Set;

/**
 * A dynamic type that encodes that a given value is guaranteed to be non null.
 *
 * <p>This dynamic type is a singleton and does not have an upper bound type. If this dynamic type
 * is used in a context where there is a corresponding static type, then the dynamic upper bound
 * type is the static type with non null information attached. See also {@link
 * #getDynamicUpperBoundType(TypeElement)}.
 */
public class NotNullDynamicType extends DynamicType {

  private static final NotNullDynamicType INSTANCE = new NotNullDynamicType();

  private NotNullDynamicType() {}

  public static NotNullDynamicType get() {
    return INSTANCE;
  }

  @Override
  public ReferenceTypeElement getDynamicUpperBoundType(TypeElement staticType) {
    assert staticType.isReferenceType();
    return staticType.asReferenceType().getOrCreateVariant(Nullability.definitelyNotNull());
  }

  @Override
  public ClassTypeElement getExactClassType() {
    return null;
  }

  @Override
  public Nullability getNullability() {
    return Nullability.definitelyNotNull();
  }

  @Override
  public boolean isNotNullType() {
    return true;
  }

  @Override
  public NotNullDynamicType rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens graphLens, Set<DexType> prunedTypes) {
    return this;
  }

  @Override
  public DynamicType withNullability(Nullability nullability) {
    assert !nullability.isBottom();
    return nullability.isDefinitelyNotNull() ? this : unknown();
  }

  @Override
  public boolean equals(Object other) {
    return this == other;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public String toString() {
    return "NotNullDynamicType";
  }
}
