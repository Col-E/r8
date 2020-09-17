// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.naming.ClassNameMapper;
import java.nio.ShortBuffer;

public class ConstString extends Format21c<DexString> {

  public static final int OPCODE = 0x1a;
  public static final String NAME = "ConstString";
  public static final String SMALI_NAME = "const-string";

  ConstString(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getStringMap());
  }

  public ConstString(int register, DexString string) {
    super(register, string);
  }

  public DexString getString() {
    return BBBB;
  }

  @Override
  int internalCompareBBBB(Format21c<?> other) {
    return BBBB.slowCompareTo((DexString) other.BBBB);
  }

  @Override
  public void collectIndexedItems(
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      GraphLens graphLens,
      LensCodeRewriterUtils rewriter) {
    getString().collectIndexedItems(indexedItems);
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
    return OPCODE;
  }

  @Override
  public ConstString asConstString() {
    return this;
  }

  @Override
  public boolean isConstString() {
    return true;
  }

  @Override
  public String toString(ClassNameMapper naming) {
    return formatString("v" + AA + ", \"" + BBBB.toString() + "\"");
  }

  @Override
  public String toSmaliString(ClassNameMapper naming) {
    return formatSmaliString("v" + AA + ", \"" + BBBB.toString() + "\"");
  }

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    int index = BBBB.getOffset(mapping);
    if (index != (index & 0xffff)) {
      throw new InternalCompilerError("String-index overflow.");
    }
    writeFirst(AA, dest);
    write16BitReference(BBBB, dest, mapping);
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addConstString(AA, (DexString) BBBB);
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
