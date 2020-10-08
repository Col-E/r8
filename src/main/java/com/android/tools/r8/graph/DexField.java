// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.Reference;
import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class DexField extends DexMember<DexEncodedField, DexField> {

  public final DexType type;

  DexField(DexType holder, DexType type, DexString name, boolean skipNameValidationForTesting) {
    super(holder, name);
    this.type = type;
    if (!skipNameValidationForTesting && !name.isValidFieldName()) {
      throw new CompilationError(
          "Field name '" + name.toString() + "' cannot be represented in dex format.");
    }
  }

  public DexType getHolderType() {
    return holder;
  }

  public DexType getType() {
    return type;
  }

  @Override
  public DexEncodedField lookupOnClass(DexClass clazz) {
    return clazz != null ? clazz.lookupField(this) : null;
  }

  @Override
  public ProgramField lookupOnProgramClass(DexProgramClass clazz) {
    return clazz != null ? clazz.lookupProgramField(this) : null;
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
  public void collectIndexedItems(IndexedItemCollection indexedItems) {
    if (indexedItems.addField(this)) {
      holder.collectIndexedItems(indexedItems);
      type.collectIndexedItems(indexedItems);
      indexedItems.getRenamedName(this).collectIndexedItems(indexedItems);
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
  public int slowCompareTo(DexField other) {
    int result = holder.slowCompareTo(other.holder);
    if (result != 0) {
      return result;
    }
    result = name.slowCompareTo(other.name);
    if (result != 0) {
      return result;
    }
    return type.slowCompareTo(other.type);
  }

  @Override
  public int slowCompareTo(DexField other, NamingLens namingLens) {
    int result = holder.slowCompareTo(other.holder, namingLens);
    if (result != 0) {
      return result;
    }
    result = namingLens.lookupName(this).slowCompareTo(namingLens.lookupName(other));
    if (result != 0) {
      return result;
    }
    return type.slowCompareTo(other.type, namingLens);
  }

  @Override
  public boolean match(DexField field) {
    return field.name == name && field.type == type;
  }

  @Override
  public boolean match(DexEncodedField encodedField) {
    return match(encodedField.field);
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

  public DexField withHolder(DexType holder, DexItemFactory dexItemFactory) {
    return dexItemFactory.createField(holder, type, name);
  }

  public DexField withName(DexString name, DexItemFactory dexItemFactory) {
    return dexItemFactory.createField(holder, type, name);
  }

  public FieldReference asFieldReference() {
    return Reference.field(
        Reference.classFromDescriptor(holder.toDescriptorString()),
        name.toString(),
        Reference.typeFromDescriptor(type.toDescriptorString()));
  }
}
