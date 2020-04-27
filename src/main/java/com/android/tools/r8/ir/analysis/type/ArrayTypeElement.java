// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import java.util.Objects;
import java.util.function.Function;

public class ArrayTypeElement extends ReferenceTypeElement {

  private final TypeElement memberTypeLattice;

  // On-demand link between other nullability-variants.
  private final NullabilityVariants<ArrayTypeElement> variants;

  public static ArrayTypeElement create(TypeElement memberTypeLattice, Nullability nullability) {
    return NullabilityVariants.create(
        nullability, (variants) -> new ArrayTypeElement(memberTypeLattice, nullability, variants));
  }

  private ArrayTypeElement(
      TypeElement memberTypeLattice,
      Nullability nullability,
      NullabilityVariants<ArrayTypeElement> variants) {
    super(nullability);
    assert memberTypeLattice.isPrimitiveType() || memberTypeLattice.nullability().isMaybeNull();
    this.memberTypeLattice = memberTypeLattice;
    this.variants = variants;
  }

  public DexType toDexType(DexItemFactory factory) {
    TypeElement baseTypeLattice = getBaseType();
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
    TypeElement member = getMemberType();
    while (member.isArrayType()) {
      ++nesting;
      member = member.asArrayType().getMemberType();
    }
    return nesting;
  }

  public TypeElement getMemberType() {
    return memberTypeLattice;
  }

  public TypeElement getMemberTypeAsValueType() {
    return memberTypeLattice.isFineGrainedType() ? getInt() : memberTypeLattice;
  }

  public TypeElement getBaseType() {
    TypeElement base = getMemberType();
    while (base.isArrayType()) {
      base = base.asArrayType().getMemberType();
    }
    return base;
  }

  private ArrayTypeElement createVariant(
      Nullability nullability, NullabilityVariants<ArrayTypeElement> variants) {
    assert this.nullability != nullability;
    return new ArrayTypeElement(memberTypeLattice, nullability, variants);
  }

  @Override
  public ReferenceTypeElement getOrCreateVariant(Nullability nullability) {
    ArrayTypeElement variant = variants.get(nullability);
    if (variant != null) {
      return variant;
    }
    return variants.getOrCreateElement(nullability, this::createVariant);
  }

  @Override
  public boolean isBasedOnMissingClass(AppView<? extends AppInfoWithClassHierarchy> appView) {
    return memberTypeLattice.isBasedOnMissingClass(appView);
  }

  @Override
  public boolean isArrayType() {
    return true;
  }

  @Override
  public ArrayTypeElement asArrayType() {
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
    if (!(o instanceof ArrayTypeElement)) {
      return false;
    }
    ArrayTypeElement other = (ArrayTypeElement) o;
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
  public ArrayTypeElement fixupClassTypeReferences(
      Function<DexType, DexType> mapping, AppView<? extends AppInfoWithClassHierarchy> appView) {
    if (memberTypeLattice.isReferenceType()) {
      TypeElement substitutedMemberType =
          memberTypeLattice.fixupClassTypeReferences(mapping, appView);
      if (substitutedMemberType != memberTypeLattice) {
        return ArrayTypeElement.create(substitutedMemberType, nullability);
      }
    }
    return this;
  }

  ReferenceTypeElement join(ArrayTypeElement other, AppView<?> appView) {
    Nullability nullability = nullability().join(other.nullability());
    ReferenceTypeElement join =
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

  private static ReferenceTypeElement joinMember(
      TypeElement aMember, TypeElement bMember, AppView<?> appView, Nullability nullability) {
    if (aMember.equals(bMember)) {
      // Return null indicating the join is the same as the member to avoid object allocation.
      return null;
    }
    if (aMember.isArrayType() && bMember.isArrayType()) {
      TypeElement join =
          joinMember(
              aMember.asArrayType().memberTypeLattice,
              bMember.asArrayType().memberTypeLattice,
              appView,
              maybeNull());
      return join == null ? null : ArrayTypeElement.create(join, nullability);
    }
    if (aMember.isClassType() && bMember.isClassType()) {
      ReferenceTypeElement join = aMember.asClassType().join(bMember.asClassType(), appView);
      return ArrayTypeElement.create(join, nullability);
    }
    if (aMember.isPrimitiveType() || bMember.isPrimitiveType()) {
      return objectClassType(appView, nullability);
    }
    return objectArrayType(appView, nullability);
  }
}
