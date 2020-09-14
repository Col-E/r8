// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.FieldMemberType;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.naming.ClassNameMapper;
import java.nio.ShortBuffer;
import java.util.Comparator;

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
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      GraphLens graphLens,
      LensCodeRewriterUtils rewriter) {
    // We intentionally apply the graph lens first, and then the init class lens, using the fact
    // that the init class lens maps classes in the final program to fields in the final program.
    DexType rewrittenClass = graphLens.lookupType(clazz);
    DexField clinitField = indexedItems.getInitClassLens().getInitClassField(rewrittenClass);
    clinitField.collectIndexedItems(indexedItems);
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

  @Override
  int getCompareToId() {
    return DexCompareHelper.INIT_CLASS_COMPARE_ID;
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
  public void write(
      ShortBuffer buffer,
      ProgramMethod context,
      GraphLens graphLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    // We intentionally apply the graph lens first, and then the init class lens, using the fact
    // that the init class lens maps classes in the final program to fields in the final program.
    DexType rewrittenClass = graphLens.lookupType(clazz);
    DexField clinitField = mapping.getClinitField(rewrittenClass);
    writeFirst(dest, buffer, getOpcode(clinitField));
    write16BitReference(clinitField, buffer, mapping);
  }

  @Override
  public int hashCode() {
    return ((clazz.hashCode() << 8) | dest) ^ getClass().hashCode();
  }

  @Override
  final int internalCompareTo(Instruction other) {
    return Comparator.comparingInt((DexInitClass i) -> i.dest)
        .thenComparing(i -> i.clazz, DexType::slowCompareTo)
        .compare(this, (DexInitClass) other);
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
