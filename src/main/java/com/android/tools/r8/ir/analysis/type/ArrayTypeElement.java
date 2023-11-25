// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import java.util.Objects;
import java.util.Set;
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

  @Override
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

  @Override
  public boolean isPrimitiveArrayType() {
    return memberTypeLattice.isPrimitiveType();
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
  public ArrayTypeElement getOrCreateVariant(Nullability nullability) {
    return nullability.equals(nullability())
        ? this
        : variants.getOrCreateElement(nullability, this::createVariant);
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
  @SuppressWarnings("ReferenceEquality")
  public ArrayTypeElement fixupClassTypeReferences(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Function<DexType, DexType> mapping,
      Set<DexType> prunedTypes) {
    if (memberTypeLattice.isReferenceType()) {
      TypeElement substitutedMemberType =
          memberTypeLattice.fixupClassTypeReferences(appView, mapping, prunedTypes);
      if (substitutedMemberType != memberTypeLattice) {
        return ArrayTypeElement.create(substitutedMemberType, nullability);
      }
    }
    return this;
  }

  ReferenceTypeElement join(ArrayTypeElement other, AppView<?> appView) {
    Nullability nullability = nullability().join(other.nullability());
    ReferenceTypeElement join =
        joinMember(getMemberType(), other.getMemberType(), appView, nullability);
    if (join == null) {
      // Check if other has the right nullability before creating it.
      if (other.nullability() == nullability) {
        return other;
      } else {
        return getOrCreateVariant(nullability);
      }
    } else {
      assert join.nullability() == nullability;
      return join;
    }
  }

  ReferenceTypeElement join(ClassTypeElement other, AppView<?> appView) {
    return other.join(this, appView);
  }

  @Override
  public ReferenceTypeElement join(ReferenceTypeElement other, AppView<?> appView) {
    if (other.isArrayType()) {
      return join(other.asArrayType(), appView);
    }
    if (other.isClassType()) {
      return join(other.asClassType(), appView);
    }
    assert other.isNullType();
    return joinNullability(other.nullability());
  }

  private static ReferenceTypeElement joinMember(
      TypeElement aMember, TypeElement bMember, AppView<?> appView, Nullability nullability) {
    if (aMember.equals(bMember)) {
      // Return null indicating the join is the same as the member to avoid object allocation.
      return null;
    }
    if (aMember.isReferenceType() && bMember.isReferenceType()) {
      if (aMember.isArrayType() && bMember.isArrayType()) {
        TypeElement aMemberMember = aMember.asArrayType().getMemberType();
        TypeElement bMemberMember = bMember.asArrayType().getMemberType();
        TypeElement join =
            joinMember(
                aMemberMember,
                bMemberMember,
                appView,
                aMember.nullability().join(bMember.nullability()));
        return join == null ? null : ArrayTypeElement.create(join, nullability);
      }
      ReferenceTypeElement join =
          aMember.asReferenceType().join(bMember.asReferenceType(), appView);
      return ArrayTypeElement.create(join, nullability);
    } else {
      assert aMember.isPrimitiveType() || bMember.isPrimitiveType();
      if (appView.enableWholeProgramOptimizations()) {
        assert appView.hasClassHierarchy();
        DexItemFactory dexItemFactory = appView.dexItemFactory();
        InterfaceCollection interfaceCollection =
            InterfaceCollection.builder()
                .addKnownInterface(dexItemFactory.cloneableType)
                .addKnownInterface(dexItemFactory.serializableType)
                .build();
        return ClassTypeElement.create(
            dexItemFactory.objectType,
            nullability,
            appView.withClassHierarchy(),
            interfaceCollection);
      }
      return objectClassType(appView, nullability);
    }
  }
}
