// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.FieldMemberType;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.nio.ShortBuffer;

public class DexInitClass extends DexBase2Format {

  public static final int OPCODE = 0x60;
  public static final String NAME = "InitClass";
  public static final String SMALI_NAME = "initclass";

  private final int dest;
  private final DexType clazz;

  private static void specify(StructuralSpecification<DexInitClass, ?> spec) {
    spec.withInt(i -> i.dest).withItem(i -> i.clazz);
  }

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
      AppView<?> appView,
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      LensCodeRewriterUtils rewriter) {
    // We intentionally apply the graph lens first, and then the init class lens, using the fact
    // that the init class lens maps classes in the final program to fields in the final program.
    DexType rewrittenClass = appView.graphLens().lookupType(clazz);
    DexField clinitField = appView.initClassLens().getInitClassField(rewrittenClass);
    clinitField.collectIndexedItems(appView, indexedItems);
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
        return DexSget.OPCODE;
      case LONG:
      case DOUBLE:
        return DexSgetWide.OPCODE;
      case OBJECT:
        return DexSgetObject.OPCODE;
      case BOOLEAN:
        return DexSgetBoolean.OPCODE;
      case BYTE:
        return DexSgetByte.OPCODE;
      case CHAR:
        return DexSgetChar.OPCODE;
      case SHORT:
        return DexSgetShort.OPCODE;
      default:
        throw new Unreachable("Unexpected type: " + type);
    }
  }

  @Override
  public void registerUse(UseRegistry<?> registry) {
    registry.registerInitClass(clazz);
  }

  @Override
  public void write(
      ShortBuffer buffer,
      ProgramMethod context,
      GraphLens graphLens,
      GraphLens codeLens,
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
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(this, (DexInitClass) other, DexInitClass::specify);
  }

  @Override
  void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, DexInitClass::specify);
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    return formatSmaliString("v" + dest + ", " + clazz.toSmaliString());
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString("v" + dest + ", " + retracer.toDescriptor(clazz));
  }
}
