// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import java.util.Objects;
import java.util.function.Function;

public class ArrayTypeLatticeElement extends ReferenceTypeLatticeElement {

  private final TypeLatticeElement memberTypeLattice;

  // On-demand link between other nullability-variants.
  private final NullabilityVariants<ArrayTypeLatticeElement> variants;

  public static ArrayTypeLatticeElement create(
      TypeLatticeElement memberTypeLattice, Nullability nullability) {
    return NullabilityVariants.create(
        nullability,
        (variants) -> new ArrayTypeLatticeElement(memberTypeLattice, nullability, variants));
  }

  private ArrayTypeLatticeElement(
      TypeLatticeElement memberTypeLattice,
      Nullability nullability,
      NullabilityVariants<ArrayTypeLatticeElement> variants) {
    super(nullability);
    assert memberTypeLattice.isPrimitiveType() || memberTypeLattice.nullability().isMaybeNull();
    this.memberTypeLattice = memberTypeLattice;
    this.variants = variants;
  }

  public DexType toDexType(DexItemFactory factory) {
    TypeLatticeElement baseTypeLattice = getBaseType();
    DexType baseType;
    if (baseTypeLattice.isPrimitiveType()) {
      baseType = baseTypeLattice.asPrimitiveType().toDexType(factory);
    } else {
      assert baseTypeLattice.isClassType();
      baseType = baseTypeLattice.asClassType().getClassType();
    }
    return factory.createArrayType(getNesting(), baseType);
  }

  public int getNesting() {
    int nesting = 1;
    TypeLatticeElement member = getMemberType();
    while (member.isArrayType()) {
      ++nesting;
      member = member.asArrayType().getMemberType();
    }
    return nesting;
  }

  public TypeLatticeElement getMemberType() {
    return memberTypeLattice;
  }

  public TypeLatticeElement getMemberTypeAsValueType() {
    return memberTypeLattice.isFineGrainedType() ? getInt() : memberTypeLattice;
  }

  public TypeLatticeElement getBaseType() {
    TypeLatticeElement base = getMemberType();
    while (base.isArrayType()) {
      base = base.asArrayType().getMemberType();
    }
    return base;
  }

  private ArrayTypeLatticeElement createVariant(
      Nullability nullability, NullabilityVariants<ArrayTypeLatticeElement> variants) {
    assert this.nullability != nullability;
    return new ArrayTypeLatticeElement(memberTypeLattice, nullability, variants);
  }

  @Override
  public ReferenceTypeLatticeElement getOrCreateVariant(Nullability nullability) {
    ArrayTypeLatticeElement variant = variants.get(nullability);
    if (variant != null) {
      return variant;
    }
    return variants.getOrCreateElement(nullability, this::createVariant);
  }

  @Override
  public boolean isBasedOnMissingClass(AppView<? extends AppInfoWithSubtyping> appView) {
    return memberTypeLattice.isBasedOnMissingClass(appView);
  }

  @Override
  public boolean isArrayType() {
    return true;
  }

  @Override
  public ArrayTypeLatticeElement asArrayType() {
    return this;
  }

  @Override
  public String toString() {
    return nullability.toString() + " (" + memberTypeLattice.toString() + "[])";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ArrayTypeLatticeElement)) {
      return false;
    }
    ArrayTypeLatticeElement other = (ArrayTypeLatticeElement) o;
    if (nullability() != other.nullability()) {
      return false;
    }
    return memberTypeLattice.equals(other.memberTypeLattice);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nullability, memberTypeLattice);
  }

  @Override
  public ArrayTypeLatticeElement fixupClassTypeReferences(
      Function<DexType, DexType> mapping, AppView<? extends AppInfoWithSubtyping> appView) {
    if (memberTypeLattice.isReferenceType()) {
      TypeLatticeElement substitutedMemberType =
          memberTypeLattice.fixupClassTypeReferences(mapping, appView);
      if (substitutedMemberType != memberTypeLattice) {
        return ArrayTypeLatticeElement.create(substitutedMemberType, nullability);
      }
    }
    return this;
  }

  ReferenceTypeLatticeElement join(ArrayTypeLatticeElement other, AppView<?> appView) {
    Nullability nullability = nullability().join(other.nullability());
    ReferenceTypeLatticeElement join =
        joinMember(this.memberTypeLattice, other.memberTypeLattice, appView, nullability);
    if (join == null) {
      // Check if other has the right nullability before creating it.
      if (other.nullability == nullability) {
        return other;
      } else {
        return getOrCreateVariant(nullability);
      }
    } else {
      assert join.nullability == nullability;
      return join;
    }
  }

  private static ReferenceTypeLatticeElement joinMember(
      TypeLatticeElement aMember,
      TypeLatticeElement bMember,
      AppView<?> appView,
      Nullability nullability) {
    if (aMember.equals(bMember)) {
      // Return null indicating the join is the same as the member to avoid object allocation.
      return null;
    }
    if (aMember.isArrayType() && bMember.isArrayType()) {
      TypeLatticeElement join =
          joinMember(
              aMember.asArrayType().memberTypeLattice,
              bMember.asArrayType().memberTypeLattice,
              appView,
              maybeNull());
      return join == null ? null : ArrayTypeLatticeElement.create(join, nullability);
    }
    if (aMember.isClassType() && bMember.isClassType()) {
      ReferenceTypeLatticeElement join = aMember.asClassType().join(bMember.asClassType(), appView);
      return ArrayTypeLatticeElement.create(join, nullability);
    }
    if (aMember.isPrimitiveType() || bMember.isPrimitiveType()) {
      return objectClassType(appView, nullability);
    }
    return objectArrayType(appView, nullability);
  }
}
