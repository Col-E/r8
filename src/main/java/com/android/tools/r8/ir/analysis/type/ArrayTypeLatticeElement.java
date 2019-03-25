// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;

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
    assert memberTypeLattice.isPrimitive() || memberTypeLattice.nullability().isMaybeNull();
    this.memberTypeLattice = memberTypeLattice;
    this.variants = variants;
  }

  public DexType getArrayType(DexItemFactory factory) {
    TypeLatticeElement baseTypeLattice = getArrayBaseTypeLattice();
    DexType baseType;
    if (baseTypeLattice.isPrimitive()) {
      baseType = baseTypeLattice.asPrimitiveTypeLatticeElement().toDexType(factory);
    } else {
      assert baseTypeLattice.isClassType();
      baseType = baseTypeLattice.asClassTypeLatticeElement().getClassType();
    }
    return factory.createArrayType(getNesting(), baseType);
  }

  int getNesting() {
    int nesting = 1;
    TypeLatticeElement member = getArrayMemberTypeAsMemberType();
    while (member.isArrayType()) {
      ++nesting;
      member = member.asArrayTypeLatticeElement().getArrayMemberTypeAsMemberType();
    }
    return nesting;
  }

  TypeLatticeElement getArrayMemberTypeAsMemberType() {
    return memberTypeLattice;
  }

  public TypeLatticeElement getArrayMemberTypeAsValueType() {
    return memberTypeLattice.isFineGrainedType() ? INT : memberTypeLattice;
  }

  private TypeLatticeElement getArrayBaseTypeLattice() {
    TypeLatticeElement base = getArrayMemberTypeAsMemberType();
    while (base.isArrayType()) {
      base = base.asArrayTypeLatticeElement().getArrayMemberTypeAsMemberType();
    }
    return base;
  }

  private ArrayTypeLatticeElement createVariant(
      Nullability nullability, NullabilityVariants<ArrayTypeLatticeElement> variants) {
    assert this.nullability != nullability;
    return new ArrayTypeLatticeElement(memberTypeLattice, nullability, variants);
  }

  @Override
  public TypeLatticeElement asNullable() {
    return getOrCreateVariant(maybeNull());
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
  public TypeLatticeElement asNonNullable() {
    return getOrCreateVariant(definitelyNotNull());
  }

  @Override
  public boolean isBasedOnMissingClass(DexDefinitionSupplier definitions) {
    return memberTypeLattice.isBasedOnMissingClass(definitions);
  }

  @Override
  public boolean isArrayType() {
    return true;
  }

  @Override
  public ArrayTypeLatticeElement asArrayTypeLatticeElement() {
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
    return (isNullable() ? 1 : -1) * memberTypeLattice.hashCode();
  }

  ReferenceTypeLatticeElement join(
      ArrayTypeLatticeElement other, DexDefinitionSupplier definitions) {
    Nullability nullability = nullability().join(other.nullability());
    ReferenceTypeLatticeElement join =
        joinMember(this.memberTypeLattice, other.memberTypeLattice, definitions, nullability);
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
      DexDefinitionSupplier definitions,
      Nullability nullability) {
    if (aMember.equals(bMember)) {
      // Return null indicating the join is the same as the member to avoid object allocation.
      return null;
    }
    if (aMember.isArrayType() && bMember.isArrayType()) {
      TypeLatticeElement join =
          joinMember(
              aMember.asArrayTypeLatticeElement().memberTypeLattice,
              bMember.asArrayTypeLatticeElement().memberTypeLattice,
              definitions,
              maybeNull());
      return join == null ? null : ArrayTypeLatticeElement.create(join, nullability);
    }
    if (aMember.isClassType() && bMember.isClassType()) {
      ReferenceTypeLatticeElement join =
          aMember
              .asClassTypeLatticeElement()
              .join(bMember.asClassTypeLatticeElement(), definitions);
      return ArrayTypeLatticeElement.create(join, nullability);
    }
    if (aMember.isPrimitive() || bMember.isPrimitive()) {
      return aMember.objectClassType(definitions, nullability);
    }
    return aMember.objectArrayType(definitions, nullability);
  }
}
