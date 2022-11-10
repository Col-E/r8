// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.UseRegistry.MethodHandleUse;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.nio.ShortBuffer;

public class DexConstMethodHandle extends DexFormat21c<DexMethodHandle> {

  public static final int OPCODE = 0xfe;
  public static final String NAME = "ConstMethodHandle";
  public static final String SMALI_NAME = "const-method-handle";

  DexConstMethodHandle(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getMethodHandleMap());
  }

  public DexConstMethodHandle(int register, DexMethodHandle methodHandle) {
    super(register, methodHandle);
  }

  public DexMethodHandle getMethodHandle() {
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
  void internalSubSpecify(StructuralSpecification<DexFormat21c<DexMethodHandle>, ?> spec) {
    spec.withItem(i -> i.BBBB);
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString("v" + AA + ", \"" + BBBB.toString() + "\"");
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    return formatSmaliString("v" + AA + ", \"" + BBBB.toString() + "\"");
  }

  @Override
  public void registerUse(UseRegistry<?> registry) {
    registry.registerMethodHandle(
        getMethodHandle(), MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY);
  }

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    DexMethodHandle rewritten =
        rewriter.rewriteDexMethodHandle(
            getMethodHandle(), MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY, context);
    int index = rewritten.getOffset(mapping);
    if (index != (index & 0xffff)) {
      throw new InternalCompilerError("MethodHandle-index overflow.");
    }
    writeFirst(AA, dest);
    write16BitReference(rewritten, dest, mapping);
  }

  @Override
  public void collectIndexedItems(
      AppView<?> appView,
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      LensCodeRewriterUtils rewriter) {
    DexMethodHandle rewritten =
        rewriter.rewriteDexMethodHandle(
            getMethodHandle(), MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY, context);
    rewritten.collectIndexedItems(appView, indexedItems);
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addConstMethodHandle(AA, BBBB);
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
