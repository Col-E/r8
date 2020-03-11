// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.FieldMemberType;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.naming.ClassNameMapper;
import java.nio.ShortBuffer;

public class DexInitClass extends Base2Format {

  public static final int OPCODE = 0x60;
  public static final String NAME = "InitClass";
  public static final String SMALI_NAME = "initclass";

  private final int dest;
  private final DexType clazz;

  public DexInitClass(int dest, DexType clazz) {
    assert clazz.isClassType();
    this.dest = dest;
    this.clazz = clazz;
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addInitClass(dest, clazz);
  }

  @Override
  public void collectIndexedItems(
      IndexedItemCollection indexedItems, DexMethod method, int instructionOffset) {
    DexField field = indexedItems.getInitClassLens().getInitClassField(clazz);
    field.collectIndexedItems(indexedItems, method, instructionOffset);
  }

  @Override
  public boolean canThrow() {
    return true;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getSmaliName() {
    return SMALI_NAME;
  }

  @Override
  public int getOpcode() {
    throw new Unreachable();
  }

  private int getOpcode(DexField field) {
    FieldMemberType type = FieldMemberType.fromDexType(field.type);
    switch (type) {
      case INT:
      case FLOAT:
        return Sget.OPCODE;
      case LONG:
      case DOUBLE:
        return SgetWide.OPCODE;
      case OBJECT:
        return SgetObject.OPCODE;
      case BOOLEAN:
        return SgetBoolean.OPCODE;
      case BYTE:
        return SgetByte.OPCODE;
      case CHAR:
        return SgetChar.OPCODE;
      case SHORT:
        return SgetShort.OPCODE;
      default:
        throw new Unreachable("Unexpected type: " + type);
    }
  }

  @Override
  public void registerUse(UseRegistry registry) {
    registry.registerInitClass(clazz);
  }

  @Override
  public void write(ShortBuffer buffer, ObjectToOffsetMapping mapping) {
    DexField field = mapping.getClinitField(clazz);
    writeFirst(dest, buffer, getOpcode(field));
    write16BitReference(field, buffer, mapping);
  }

  @Override
  public int hashCode() {
    return ((clazz.hashCode() << 8) | dest) ^ getClass().hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    DexInitClass initClass = (DexInitClass) other;
    return dest == initClass.dest && clazz == initClass.clazz;
  }

  @Override
  public String toSmaliString(ClassNameMapper naming) {
    return formatSmaliString("v" + dest + ", " + clazz.toSmaliString());
  }

  @Override
  public String toString(ClassNameMapper naming) {
    StringBuilder builder = new StringBuilder("v").append(dest).append(", ");
    if (naming == null) {
      builder.append(clazz.toSourceString());
    } else {
      builder.append(naming.originalNameOf(clazz));
    }
    return formatString(builder.toString());
  }
}
