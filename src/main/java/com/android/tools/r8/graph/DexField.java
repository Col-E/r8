// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class DexField extends DexMember<DexEncodedField, DexField> {

  @SuppressWarnings("ReferenceEquality")
  public static boolean identical(DexField t1, DexField t2) {
    return t1 == t2;
  }

  public final boolean isIdenticalTo(DexField other) {
    return identical(this, other);
  }

  public final DexType type;

  DexField(DexType holder, DexType type, DexString name, boolean skipNameValidationForTesting) {
    super(holder, name);
    this.type = type;
    if (!skipNameValidationForTesting && !name.isValidFieldName()) {
      throw new CompilationError(
          "Field name '" + name.toString() + "' cannot be represented in dex format.");
    }
  }

  private static void specify(StructuralSpecification<DexField, ?> spec) {
    spec.withItem(DexField::getHolderType).withItem(DexField::getName).withItem(DexField::getType);
  }

  @Override
  public int compareTo(DexReference other) {
    if (other.isDexField()) {
      return compareTo(other.asDexField());
    }
    if (other.isDexMethod()) {
      int comparisonResult = getHolderType().compareTo(other.getContextType());
      return comparisonResult != 0 ? comparisonResult : -1;
    }
    int comparisonResult = getHolderType().compareTo(other.asDexType());
    return comparisonResult != 0 ? comparisonResult : 1;
  }

  @Override
  public LirConstantOrder getLirConstantOrder() {
    return LirConstantOrder.FIELD;
  }

  @Override
  public int internalLirConstantAcceptCompareTo(LirConstant other, CompareToVisitor visitor) {
    return acceptCompareTo((DexField) other, visitor);
  }

  @Override
  public void internalLirConstantAcceptHashing(HashingVisitor visitor) {
    acceptHashing(visitor);
  }

  @Override
  public DexField self() {
    return this;
  }

  @Override
  public StructuralMapping<DexField> getStructuralMapping() {
    return DexField::specify;
  }

  public DexType getType() {
    return type;
  }

  public TypeElement getTypeElement(AppView<?> appView) {
    return TypeElement.fromDexType(getType(), maybeNull(), appView);
  }

  @Override
  public DexEncodedField lookupOnClass(DexClass clazz) {
    return clazz != null ? clazz.lookupField(this) : null;
  }

  @Override
  public DexClassAndField lookupMemberOnClass(DexClass clazz) {
    return clazz != null ? clazz.lookupClassField(this) : null;
  }

  @Override
  public ProgramField lookupOnProgramClass(DexProgramClass clazz) {
    return clazz != null ? clazz.lookupProgramField(this) : null;
  }

  @Override
  public <T> T apply(Function<DexField, T> fieldConsumer, Function<DexMethod, T> methodConsumer) {
    return fieldConsumer.apply(this);
  }

  @Override
  public <T> T apply(
      Function<DexType, T> classConsumer,
      Function<DexField, T> fieldConsumer,
      Function<DexMethod, T> methodConsumer) {
    return fieldConsumer.apply(this);
  }

  @Override
  public void accept(
      Consumer<DexType> classConsumer,
      Consumer<DexField> fieldConsumer,
      Consumer<DexMethod> methodConsumer) {
    fieldConsumer.accept(this);
  }

  @Override
  public <T> void accept(
      BiConsumer<DexType, T> classConsumer,
      BiConsumer<DexField, T> fieldConsumer,
      BiConsumer<DexMethod, T> methodConsumer,
      T arg) {
    fieldConsumer.accept(this, arg);
  }

  @Override
  public int computeHashCode() {
    return holder.hashCode()
        + type.hashCode() * 7
        + name.hashCode() * 31;
  }

  @Override
  public boolean computeEquals(Object other) {
    if (other instanceof DexField) {
      DexField o = (DexField) other;
      return holder.equals(o.holder)
          && type.equals(o.type)
          && name.equals(o.name);
    }
    return false;
  }

  @Override
  public String toString() {
    return "Field " + type + " " + holder + "." + name;
  }

  @Override
  public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
    if (indexedItems.addField(this)) {
      holder.collectIndexedItems(appView, indexedItems);
      type.collectIndexedItems(appView, indexedItems);
      appView.getNamingLens().lookupName(this).collectIndexedItems(indexedItems);
    }
  }

  @Override
  public int getOffset(ObjectToOffsetMapping mapping) {
    return mapping.getOffsetFor(this);
  }

  @Override
  public boolean isDexField() {
    return true;
  }

  @Override
  public DexField asDexField() {
    return this;
  }

  @Override
  public Iterable<DexType> getReferencedTypes() {
    return Collections.singleton(type);
  }

  @Override
  public int acceptCompareTo(DexField other, CompareToVisitor visitor) {
    return visitor.visitDexField(this, other);
  }

  @Override
  public void acceptHashing(HashingVisitor visitor) {
    visitor.visitDexField(this);
  }

  @Override
  public boolean match(DexField field) {
    return name.isIdenticalTo(field.name) && type.isIdenticalTo(field.type);
  }

  @Override
  public boolean match(DexEncodedField encodedField) {
    return match(encodedField.getReference());
  }

  public String qualifiedName() {
    return holder + "." + name;
  }

  @Override
  public String toSmaliString() {
    return holder.toSmaliString() + "->" + name + ":" + type.toSmaliString();
  }

  @Override
  public String toSourceString() {
    return type.toSourceString() + " " + holder.toSourceString() + "." + name.toSourceString();
  }

  @Override
  public DexField withHolder(DexType holder, DexItemFactory dexItemFactory) {
    return dexItemFactory.createField(holder, type, name);
  }

  public DexField withName(DexString name, DexItemFactory dexItemFactory) {
    return dexItemFactory.createField(holder, type, name);
  }

  public DexField withType(DexType type, DexItemFactory dexItemFactory) {
    return dexItemFactory.createField(holder, type, name);
  }

  public FieldReference asFieldReference() {
    return Reference.field(
        Reference.classFromDescriptor(holder.toDescriptorString()),
        name.toString(),
        Reference.typeFromDescriptor(type.toDescriptorString()));
  }
}
