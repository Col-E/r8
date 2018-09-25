// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;

public class DexEncodedField extends KeyedDexItem<DexField> {

  public static final DexEncodedField[] EMPTY_ARRAY = new DexEncodedField[]{};

  public final DexField field;
  public final FieldAccessFlags accessFlags;
  public DexAnnotationSet annotations;
  private DexValue staticValue;

  public DexEncodedField(
      DexField field,
      FieldAccessFlags accessFlags,
      DexAnnotationSet annotations,
      DexValue staticValue) {
    this.field = field;
    this.accessFlags = accessFlags;
    this.annotations = annotations;
    this.staticValue = staticValue;
  }

  @Override
  public void collectIndexedItems(IndexedItemCollection indexedItems,
      DexMethod method, int instructionOffset) {
    field.collectIndexedItems(indexedItems, method, instructionOffset);
    annotations.collectIndexedItems(indexedItems, method, instructionOffset);
    if (accessFlags.isStatic()) {
      getStaticValue().collectIndexedItems(indexedItems, method, instructionOffset);
    }
  }

  @Override
  void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    annotations.collectMixedSectionItems(mixedItems);
  }

  @Override
  public String toString() {
    return "Encoded field " + field;
  }

  @Override
  public String toSmaliString() {
    return field.toSmaliString();
  }

  @Override
  public String toSourceString() {
    return field.toSourceString();
  }

  @Override
  public DexField getKey() {
    return field;
  }

  @Override
  public DexReference toReference() {
    return field;
  }

  @Override
  public boolean isDexEncodedField() {
    return true;
  }

  @Override
  public DexEncodedField asDexEncodedField() {
    return this;
  }

  @Override
  public boolean isStatic() {
    return accessFlags.isStatic();
  }

  @Override
  public boolean isStaticMember() {
    return isStatic();
  }

  public boolean hasAnnotation() {
    return !annotations.isEmpty();
  }

  public boolean hasExplicitStaticValue() {
    assert accessFlags.isStatic();
    return staticValue != null;
  }

  public void setStaticValue(DexValue staticValue) {
    assert accessFlags.isStatic();
    assert staticValue != null;
    this.staticValue = staticValue;
  }

  public DexValue getStaticValue() {
    assert accessFlags.isStatic();
    return staticValue == null ? DexValue.defaultForType(field.type) : staticValue;
  }

  // Returns a const instructions if this field is a compile time final const.
  public Instruction valueAsConstInstruction(AppInfo appInfo, Value dest) {
    // The only way to figure out whether the DexValue contains the final value
    // is ensure the value is not the default or check <clinit> is not present.
    if (accessFlags.isStatic() && accessFlags.isPublic() && accessFlags.isFinal()) {
      DexClass clazz = appInfo.definitionFor(field.getHolder());
      assert clazz != null : "Class for the field must be present";
      return getStaticValue().asConstInstruction(clazz.hasClassInitializer(), dest);
    }
    return null;
  }

  public DexEncodedField toRenamedField(DexString name, DexItemFactory dexItemFactory) {
    return new DexEncodedField(dexItemFactory.createField(field.clazz, field.type, name),
        accessFlags, annotations, staticValue);
  }

  public DexEncodedField toTypeSubstitutedField(DexField field) {
    if (this.field == field) {
      return this;
    }
    return new DexEncodedField(field, accessFlags, annotations, staticValue);
  }
}
