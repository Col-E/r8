// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.dex.CodeToKeep;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.dex.code.DexConst4;
import com.android.tools.r8.dex.code.DexThrow;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.ir.conversion.SyntheticStraightLineSourceCode;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.google.common.collect.ImmutableList;
import java.nio.ShortBuffer;
import java.util.List;
import java.util.function.Consumer;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ThrowNullCode extends Code implements CfWritableCode, DexWritableCode {

  private static final ThrowNullCode INSTANCE = new ThrowNullCode();

  private ThrowNullCode() {}

  public static ThrowNullCode get() {
    return INSTANCE;
  }

  @Override
  public Code asCode() {
    return this;
  }

  @Override
  public void acceptHashing(HashingVisitor visitor) {
    visitor.visitInt(getCfWritableCodeKind().hashCode());
  }

  @Override
  public Code getCodeAsInlining(
      DexMethod caller,
      boolean isCallerD8R8Synthesized,
      DexMethod callee,
      boolean isCalleeD8R8Synthesized,
      DexItemFactory factory) {
    // We don't maintain a position on the throwing stub. We may want to reconsider this as it
    // would allow retracing to recover inlinings of this stub.
    return this;
  }

  @Override
  public IRCode buildIR(
      ProgramMethod method,
      AppView<?> appView,
      Origin origin,
      MutableMethodConversionOptions conversionOptions) {
    ThrowNullSourceCode source = new ThrowNullSourceCode(method);
    return IRBuilder.create(method, appView, source, origin).build(method, conversionOptions);
  }

  @Override
  public IRCode buildInliningIR(
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      GraphLens codeLens,
      NumberGenerator valueNumberGenerator,
      Position callerPosition,
      Origin origin,
      RewrittenPrototypeDescription protoChanges) {
    ThrowNullSourceCode source = new ThrowNullSourceCode(method, callerPosition);
    return IRBuilder.createForInlining(
            method, appView, codeLens, source, origin, valueNumberGenerator, protoChanges)
        .build(context, MethodConversionOptions.nonConverting());
  }

  @Override
  public int codeSizeInBytes() {
    return DexConst4.SIZE + DexThrow.SIZE;
  }

  @Override
  public void collectIndexedItems(
      AppView<?> appView,
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      LensCodeRewriterUtils rewriter) {
    // Intentionally empty.
  }

  @Override
  public void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    // Intentionally empty.
  }

  @Override
  protected int computeHashCode() {
    return System.identityHashCode(this);
  }

  @Override
  protected boolean computeEquals(Object other) {
    return this == other;
  }

  @Override
  public int estimatedDexCodeSizeUpperBoundInBytes() {
    return codeSizeInBytes();
  }

  @Override
  public CfWritableCodeKind getCfWritableCodeKind() {
    return CfWritableCodeKind.THROW_NULL;
  }

  @Override
  public DexWritableCodeKind getDexWritableCodeKind() {
    return DexWritableCodeKind.THROW_NULL;
  }

  @Override
  public DexDebugInfoForWriting getDebugInfoForWriting() {
    return null;
  }

  @Override
  public TryHandler[] getHandlers() {
    return TryHandler.EMPTY_ARRAY;
  }

  @Override
  public DexString getHighestSortingString() {
    return null;
  }

  @Override
  public int getIncomingRegisterSize(ProgramMethod method) {
    return getMaxLocals(method);
  }

  private int getMaxLocals(ProgramMethod method) {
    int maxLocals = method.getAccessFlags().isStatic() ? 0 : 1;
    for (DexType parameter : method.getParameters()) {
      maxLocals += parameter.getRequiredRegisters();
    }
    return maxLocals;
  }

  @Override
  public int getOutgoingRegisterSize() {
    return 0;
  }

  @Override
  public int getRegisterSize(ProgramMethod method) {
    return Math.max(getIncomingRegisterSize(method), 1);
  }

  @Override
  public Try[] getTries() {
    return Try.EMPTY_ARRAY;
  }

  @Override
  public boolean isCfWritableCode() {
    return true;
  }

  @Override
  public CfWritableCode asCfWritableCode() {
    return this;
  }

  @Override
  public boolean isDexWritableCode() {
    return true;
  }

  @Override
  public DexWritableCode asDexWritableCode() {
    return this;
  }

  @Override
  public boolean isEmptyVoidMethod() {
    return false;
  }

  @Override
  public boolean isSharedCodeObject() {
    return true;
  }

  @Override
  public boolean isThrowNullCode() {
    return true;
  }

  @Override
  public ThrowNullCode asThrowNullCode() {
    return this;
  }

  @Override
  public void registerCodeReferences(ProgramMethod method, UseRegistry registry) {
    // Intentionally empty.
  }

  @Override
  public void registerCodeReferencesForDesugaring(ClasspathMethod method, UseRegistry registry) {
    // Intentionally empty.
  }

  @Override
  public DexWritableCode rewriteCodeWithJumboStrings(
      ProgramMethod method, ObjectToOffsetMapping mapping, AppView<?> appView, boolean force) {
    // Intentionally empty. This piece of code does not have any const-string instructions.
    return this;
  }

  @Override
  public void setCallSiteContexts(ProgramMethod method) {
    // Intentionally empty. This piece of code does not have any call sites.
  }

  @Override
  public void writeCf(
      ProgramMethod method,
      CfVersion classFileVersion,
      AppView<?> appView,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor) {
    int maxStack = 1;
    visitor.visitInsn(Opcodes.ACONST_NULL);
    visitor.visitInsn(Opcodes.ATHROW);
    visitor.visitEnd();
    visitor.visitMaxs(maxStack, getMaxLocals(method));
  }

  @Override
  public void writeDex(
      ShortBuffer shortBuffer,
      ProgramMethod context,
      GraphLens graphLens,
      GraphLens codeLens,
      LensCodeRewriterUtils lensCodeRewriter,
      ObjectToOffsetMapping mapping) {
    int register = 0;
    new DexConst4(register, 0)
        .write(shortBuffer, context, graphLens, codeLens, mapping, lensCodeRewriter);
    new DexThrow(register)
        .write(shortBuffer, context, graphLens, codeLens, mapping, lensCodeRewriter);
  }

  @Override
  public void writeKeepRulesForDesugaredLibrary(CodeToKeep codeToKeep) {
    // Intentionally empty.
  }

  @Override
  public String toString() {
    return "ThrowNullCode";
  }

  @Override
  public String toString(DexEncodedMethod method, RetracerForCodePrinting retracer) {
    return "ThrowNullCode";
  }

  @Override
  public DexWritableCacheKey getCacheLookupKey(ProgramMethod method, DexItemFactory factory) {
    return new AmendedDexWritableCodeKey<DexWritableCode>(
        this, this, getIncomingRegisterSize(method), getRegisterSize(method));
  }

  static class ThrowNullSourceCode extends SyntheticStraightLineSourceCode {

    ThrowNullSourceCode(ProgramMethod method) {
      this(method, null);
    }

    ThrowNullSourceCode(ProgramMethod method, Position callerPosition) {
      super(
          getInstructionBuilders(),
          SyntheticPosition.builder()
              .setLine(0)
              .setMethod(method.getReference())
              .setCallerPosition(callerPosition)
              .build());
    }

    private static List<Consumer<IRBuilder>> getInstructionBuilders() {
      return ImmutableList.of(builder -> builder.addNullConst(0), builder -> builder.addThrow(0));
    }
  }
}
