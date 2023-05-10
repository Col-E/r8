// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.dex.CodeToKeep;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.dex.code.DexInvokeDirect;
import com.android.tools.r8.dex.code.DexNewInstance;
import com.android.tools.r8.dex.code.DexThrow;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.Copyable;
import com.android.tools.r8.utils.structural.HashingVisitor;
import javax.annotation.Nonnull;

import java.nio.ShortBuffer;
import java.util.Objects;

public class ThrowExceptionCode extends Code implements DexWritableCode, Copyable<ThrowExceptionCode> {

  private final DexType exceptionType;

  private ThrowExceptionCode(DexType exceptionType) {
    this.exceptionType = exceptionType;
  }

  public static ThrowExceptionCode create(DexType exceptionType) {
    return new ThrowExceptionCode(exceptionType);
  }

  @Override
  public Code asCode() {
    return this;
  }

  @Override
  public void acceptHashing(HashingVisitor visitor) {
    visitor.visitInt(getDexWritableCodeKind().hashCode());
    visitor.visitDexType(exceptionType);
  }

  @Override
  public IRCode buildIR(
      ProgramMethod method,
      AppView<?> appView,
      Origin origin,
      MutableMethodConversionOptions conversionOptions) {
    throw new Unreachable("Should not be called");
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
    throw new Unreachable("Should not be called");
  }

  @Override
  public int codeSizeInBytes() {
    return DexNewInstance.SIZE + DexInvokeDirect.SIZE + DexThrow.SIZE;
  }

  @Override
  public void collectIndexedItems(
      AppView<?> appView,
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      LensCodeRewriterUtils rewriter) {
    rewriter
        .dexItemFactory()
        .createInstanceInitializer(exceptionType)
        .collectIndexedItems(appView, indexedItems);
  }

  @Override
  public void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    // Intentionally empty.
  }

  @Override
  protected int computeHashCode() {
    return Objects.hash(DexWritableCodeKind.THROW_EXCEPTION, exceptionType.hashCode());
  }

  @Override
  protected boolean computeEquals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ThrowExceptionCode)) {
      return false;
    }
    ThrowExceptionCode that = (ThrowExceptionCode) other;
    return Objects.equals(exceptionType, that.exceptionType);
  }

  @Override
  public int estimatedDexCodeSizeUpperBoundInBytes() {
    return codeSizeInBytes();
  }

  @Override
  public DexWritableCodeKind getDexWritableCodeKind() {
    return DexWritableCodeKind.THROW_EXCEPTION;
  }

  @Override
  public DexDebugInfoForWriting getDebugInfoForWriting() {
    return null;
  }

  @Override
  public TryHandler[] getHandlers() {
    return new TryHandler[0];
  }

  @Override
  public DexString getHighestSortingString() {
    return null;
  }

  @Override
  public int getIncomingRegisterSize(ProgramMethod method) {
    return 0;
  }

  @Override
  public int getOutgoingRegisterSize() {
    return 1;
  }

  @Override
  public int getRegisterSize(ProgramMethod method) {
    return 1;
  }

  @Override
  public Try[] getTries() {
    return new Try[0];
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
  public boolean isThrowExceptionCode() {
    return true;
  }

  @Override
  public ThrowExceptionCode asThrowExceptionCode() {
    return this;
  }

  @Override
  public void registerCodeReferences(ProgramMethod method, UseRegistry registry) {
    throw new Unreachable("Should not be called");
  }

  @Override
  public void registerCodeReferencesForDesugaring(ClasspathMethod method, UseRegistry registry) {
    throw new Unreachable("Should not be called");
  }

  @Override
  public DexWritableCode rewriteCodeWithJumboStrings(
      ProgramMethod method, ObjectToOffsetMapping mapping, DexItemFactory factory, boolean force) {
    // Intentionally empty. This piece of code does not have any const-string instructions.
    return this;
  }

  @Override
  public void setCallSiteContexts(ProgramMethod method) {
    // Intentionally empty.
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
    int notUsed = 0;
    int argumentCount = 1;
    new DexNewInstance(register, exceptionType)
        .write(shortBuffer, context, graphLens, codeLens, mapping, lensCodeRewriter);
    DexMethod instanceInitializer =
        lensCodeRewriter.dexItemFactory().createInstanceInitializer(exceptionType);
    new DexInvokeDirect(
            argumentCount, instanceInitializer, register, notUsed, notUsed, notUsed, notUsed)
        .write(shortBuffer, context, graphLens, codeLens, mapping, lensCodeRewriter);
    new DexThrow(register)
        .write(shortBuffer, context, graphLens, codeLens, mapping, lensCodeRewriter);
  }

  @Override
  public void writeKeepRulesForDesugaredLibrary(CodeToKeep codeToKeep) {
    // Intentionally empty.
  }

  @Override
  public DexWritableCacheKey getCacheLookupKey(ProgramMethod method, DexItemFactory factory) {
    return new AmendedDexWritableCodeKey<DexType>(
        this, exceptionType, getIncomingRegisterSize(method), getRegisterSize(method));
  }

  @Override
  public String toString() {
    return "ThrowExceptionCode";
  }

  @Override
  public String toString(DexEncodedMethod method, RetracerForCodePrinting retracer) {
    return "ThrowExceptionCode";
  }

  @Nonnull
  @Override
  public ThrowExceptionCode copy() {
    return this;
  }

  @Nonnull
  @Override
  public Code copySubtype() {
    return copy();
  }
}
