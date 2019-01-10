// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import java.util.Collections;
import java.util.Set;

public class ReferenceTypeLatticeElement extends TypeLatticeElement {
  private static final ReferenceTypeLatticeElement NULL_INSTANCE =
      new ReferenceTypeLatticeElement(
          Nullability.definitelyNull(), DexItemFactory.nullValueType);

  // TODO(b/72693244): Consider moving this to ClassTypeLatticeElement.
  final DexType type;

  final Nullability nullability;
  // On-demand link between maybe-null (primary) and definitely-null reference type lattices.
  private ReferenceTypeLatticeElement primaryOrNullVariant;
  // On-demand link between maybe-null (primary) and definitely-not-null reference type lattices.
  // This link will be null for non-primary variants.
  private ReferenceTypeLatticeElement nonNullVariant;

  ReferenceTypeLatticeElement(Nullability nullability, DexType type) {
    this.nullability = nullability;
    this.type = type;
  }

  public ReferenceTypeLatticeElement getOrCreateVariant(Nullability variantNullability) {
    if (nullability == variantNullability) {
      return this;
    }
    ReferenceTypeLatticeElement primary = nullability.isMaybeNull() ? this : primaryOrNullVariant;
    synchronized (this) {
      // If the link towards the factory-created, canonicalized MAYBE_NULL variant doesn't exist,
      // we are in the middle of join() computation.
      if (primary == null) {
        primary = createVariant(Nullability.maybeNull());
        linkVariant(primary, this);
      }
    }
    if (variantNullability.isMaybeNull()) {
      return primary;
    }
    synchronized (primary) {
      ReferenceTypeLatticeElement variant =
          variantNullability.isDefinitelyNull()
              ? primary.primaryOrNullVariant
              : primary.nonNullVariant;
      if (variant == null) {
        variant = createVariant(variantNullability);
        linkVariant(primary, variant);
      }
      return variant;
    }
  }

  ReferenceTypeLatticeElement createVariant(Nullability nullability) {
    throw new Unreachable("Should be defined by class/array type lattice element");
  }

  private static void linkVariant(
      ReferenceTypeLatticeElement primary, ReferenceTypeLatticeElement variant) {
    assert primary.nullability().isMaybeNull();
    assert variant.primaryOrNullVariant == null && variant.nonNullVariant == null;
    variant.primaryOrNullVariant = primary;
    if (variant.nullability().isDefinitelyNotNull()) {
      assert primary.nonNullVariant == null;
      primary.nonNullVariant = variant;
    } else {
      assert variant.nullability().isDefinitelyNull();
      assert primary.primaryOrNullVariant == null;
      primary.primaryOrNullVariant = variant;
    }
  }

  @Override
  public Nullability nullability() {
    return nullability;
  }

  static ReferenceTypeLatticeElement getNullTypeLatticeElement() {
    return NULL_INSTANCE;
  }

  public Set<DexType> getInterfaces() {
    return Collections.emptySet();
  }

  @Override
  public boolean isNullType() {
    return type == DexItemFactory.nullValueType;
  }

  @Override
  public TypeLatticeElement asNullable() {
    assert isNullType();
    return this;
  }

  @Override
  public boolean isReference() {
    return true;
  }

  @Override
  public String toString() {
    return nullability.toString() + " " + type.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ReferenceTypeLatticeElement)) {
      return false;
    }
    ReferenceTypeLatticeElement other = (ReferenceTypeLatticeElement) o;
    if (nullability() != other.nullability()) {
      return false;
    }
    if (!type.equals(other.type)) {
      return false;
    }
    Set<DexType> thisInterfaces = getInterfaces();
    Set<DexType> otherInterfaces = other.getInterfaces();
    if (thisInterfaces.size() != otherInterfaces.size()) {
      return false;
    }
    return thisInterfaces.containsAll(otherInterfaces);
  }

  @Override
  public int hashCode() {
    assert isNullType();
    return System.identityHashCode(this);
  }
}
