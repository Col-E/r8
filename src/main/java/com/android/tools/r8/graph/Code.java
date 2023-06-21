// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.dex.code.CfOrDexInstruction;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeInstructionMetadata;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadata;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.PositionBuilder;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.Copyable;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

public abstract class Code extends CachedHashValueDexItem {

  public final IRCode buildIR(ProgramMethod method, AppView<?> appView, Origin origin) {
    return buildIR(method, appView, origin, new MutableMethodConversionOptions(appView.options()));
  }

  public abstract IRCode buildIR(
      ProgramMethod method,
      AppView<?> appView,
      Origin origin,
      MutableMethodConversionOptions conversionOptions);

  public IRCode buildInliningIR(
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      GraphLens codeLens,
      NumberGenerator valueNumberGenerator,
      Position callerPosition,
      Origin origin,
      RewrittenPrototypeDescription protoChanges) {
    throw new Unreachable("Unexpected attempt to build IR graph for inlining from: "
        + getClass().getCanonicalName());
  }

  public GraphLens getCodeLens(AppView<?> appView) {
    return appView.codeLens();
  }

  public BytecodeMetadata<? extends CfOrDexInstruction> getMetadata() {
    return null;
  }

  public BytecodeInstructionMetadata getMetadata(CfOrDexInstruction instruction) {
    return null;
  }

  public abstract void registerCodeReferences(ProgramMethod method, UseRegistry registry);

  public abstract void registerCodeReferencesForDesugaring(
      ClasspathMethod method, UseRegistry registry);

  public void registerArgumentReferences(DexEncodedMethod method, ArgumentUse registry) {
    throw new Unreachable();
  }

  public Int2ReferenceMap<DebugLocalInfo> collectParameterInfo(
      DexEncodedMethod encodedMethod, AppView<?> appView) {
    throw new Unreachable();
  }

  @Override
  public abstract String toString();

  public abstract String toString(DexEncodedMethod method, RetracerForCodePrinting retracer);

  public boolean isCfCode() {
    return false;
  }

  public boolean isCfWritableCode() {
    return false;
  }

  public boolean isDefaultInstanceInitializerCode() {
    return false;
  }

  public DefaultInstanceInitializerCode asDefaultInstanceInitializerCode() {
    return null;
  }

  public boolean isDexCode() {
    return false;
  }

  public boolean isDexWritableCode() {
    return false;
  }

  public boolean isHorizontalClassMergerCode() {
    return false;
  }

  public boolean isIncompleteHorizontalClassMergerCode() {
    return false;
  }

  public boolean isOutlineCode() {
    return false;
  }

  public boolean isSharedCodeObject() {
    return false;
  }

  public boolean isThrowNullCode() {
    return false;
  }

  public boolean hasMonitorInstructions() {
    return false;
  }

  public boolean isThrowExceptionCode() {
    return false;
  }

  public ThrowNullCode asThrowNullCode() {
    return null;
  }

  public ThrowExceptionCode asThrowExceptionCode() {
    return null;
  }

  /** Estimate the number of IR instructions emitted by buildIR(). */
  public int estimatedSizeForInlining() {
    return Integer.MAX_VALUE;
  }

  /** Compute estimatedSizeForInlining() <= threshold. */
  public boolean estimatedSizeForInliningAtMost(int threshold) {
    return estimatedSizeForInlining() <= threshold;
  }

  public abstract int estimatedDexCodeSizeUpperBoundInBytes();

  public final boolean isLirCode() {
    return asLirCode() != null;
  }

  public LirCode<Integer> asLirCode() {
    return null;
  }

  public CfCode asCfCode() {
    throw new Unreachable(getClass().getCanonicalName() + ".asCfCode()");
  }

  public CfWritableCode asCfWritableCode() {
    throw new Unreachable(getClass().getCanonicalName() + ".asCfWritableCode()");
  }

  public LazyCfCode asLazyCfCode() {
    throw new Unreachable(getClass().getCanonicalName() + ".asLazyCfCode()");
  }

  public DexCode asDexCode() {
    throw new Unreachable(getClass().getCanonicalName() + ".asDexCode()");
  }

  public DexWritableCode asDexWritableCode() {
    throw new Unreachable(getClass().getCanonicalName() + ".asDexWritableCode()");
  }

  @Override
  protected void collectMixedSectionItems(MixedSectionCollection collection) {
    throw new Unreachable();
  }

  public abstract boolean isEmptyVoidMethod();

  public boolean verifyNoInputReaders() {
    return true;
  }

  public Code getCodeAsInlining(DexMethod caller, DexEncodedMethod callee, DexItemFactory factory) {
    return getCodeAsInlining(caller, callee.getReference(), factory, callee.isD8R8Synthesized());
  }

  public Code getCodeAsInlining(
      DexMethod caller, DexMethod callee, DexItemFactory factory, boolean isCalleeD8R8Synthesized) {
    throw new Unreachable();
  }

  public static Position newInlineePosition(
      Position callerPosition, Position oldPosition, boolean isCalleeD8R8Synthesized) {
    Position outermostCaller = oldPosition.getOutermostCaller();
    if (!isCalleeD8R8Synthesized) {
      return removeSameMethodAndLineZero(oldPosition, callerPosition);
    }
    // We can replace the position since the callee was synthesized by the compiler, however, if
    // the position carries special information we need to copy it.
    if (!outermostCaller.isOutline() && !outermostCaller.isRemoveInnerFramesIfThrowingNpe()) {
      return oldPosition.replacePosition(outermostCaller, callerPosition);
    }
    assert !callerPosition.isOutline();
    PositionBuilder<?, ?> positionBuilder =
        outermostCaller
            .builderWithCopy()
            .setMethod(callerPosition.getMethod())
            .setLine(callerPosition.getLine());
    if (callerPosition.isRemoveInnerFramesIfThrowingNpe()) {
      positionBuilder.setRemoveInnerFramesIfThrowingNpe(true);
    }
    return oldPosition.replacePosition(outermostCaller, positionBuilder.build());
  }

  @Nonnull
  public abstract Code copySubtype();

  @Deprecated()
  // TODO(b/261971803): When having complete control over the positions we should not need this.
  private static Position removeSameMethodAndLineZero(
      Position calleePosition, Position callerPosition) {
    Position outermostCaller = calleePosition.getOutermostCaller();
    if (outermostCaller.getLine() == 0) {
      while (callerPosition != null
          && outermostCaller.getMethod() == callerPosition.getMethod()
          && callerPosition.getLine() == 0) {
        callerPosition = callerPosition.getCallerPosition();
      }
    }
    return calleePosition.withOutermostCallerPosition(callerPosition);
  }

  public void forEachPosition(DexMethod method, Consumer<Position> positionConsumer) {
    // Intentionally empty. Override where we have fully build CF or DEX code.
  }
}
