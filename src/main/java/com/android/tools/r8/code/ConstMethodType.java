// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.naming.ClassNameMapper;
import java.nio.ShortBuffer;

public class ConstMethodType extends Format21c<DexProto> {

  public static final int OPCODE = 0xff;
  public static final String NAME = "ConstMethodType";
  public static final String SMALI_NAME = "const-method-type";

  ConstMethodType(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getProtosMap());
  }

  public ConstMethodType(int register, DexProto methodType) {
    super(register, methodType);
  }

  public DexProto getMethodType() {
    return BBBB;
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
  int internalCompareBBBB(Format21c<?> other) {
    return BBBB.slowCompareTo((DexProto) other.BBBB);
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
  public void registerUse(UseRegistry registry) {
    registry.registerProto(getMethodType());
  }

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    DexProto rewritten = rewriter.rewriteProto(getMethodType());
    int index = rewritten.getOffset(mapping);
    if (index != (index & 0xffff)) {
      throw new InternalCompilerError("MethodType-index overflow.");
    }
    writeFirst(AA, dest);
    write16BitReference(rewritten, dest, mapping);
  }

  @Override
  public void collectIndexedItems(
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      GraphLens graphLens,
      LensCodeRewriterUtils rewriter) {
    DexProto rewritten = rewriter.rewriteProto(getMethodType());
    rewritten.collectIndexedItems(indexedItems);
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addConstMethodType(AA, BBBB);
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
