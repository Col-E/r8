// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.dex.code.CfOrDexInstruction;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.ClasspathMethod;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.InitClassLens;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import java.util.ListIterator;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.objectweb.asm.MethodVisitor;

public abstract class CfInstruction implements CfOrDexInstruction {

  public abstract void write(
      AppView<?> appView,
      ProgramMethod context,
      DexItemFactory dexItemFactory,
      GraphLens graphLens,
      InitClassLens initClassLens,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor);

  public abstract void print(CfPrinter printer);

  /**
   * Base compare id for each instruction.
   *
   * <p>The id is required to be unique for each instruction class and define a order on
   * instructions up to the instructions data payload which is ordered by {@code internalCompareTo}.
   * Currently we represent the ID using the ASM opcode of the instruction or in case the
   * instruction is not represented externally, some non-overlapping ID defined in {@code
   * CfCompareHelper}.
   */
  public abstract int getCompareToId();

  /**
   * Compare two instructions with the same compare id.
   *
   * <p>The internal compare may assume to only be called on instructions that have the same
   * "compare id". Overrides of this method can assume 'other' to be of the same type (as this is a
   * requirement for the defintion of the "compare id").
   *
   * <p>If an instruction is uniquely determined by the "compare id" then the override should simply
   * call '{@code CfCompareHelper::compareIdUniquelyDeterminesEquality}'.
   */
  public abstract int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper);

  public int bytecodeSizeUpperBound() {
    throw new Unreachable("Instruction must specify size");
  }

  public final int acceptCompareTo(
      CfInstruction o, CompareToVisitor visitor, CfCompareHelper helper) {
    int diff = visitor.visitInt(getCompareToId(), o.getCompareToId());
    if (diff != 0) {
      return diff;
    }
    return internalAcceptCompareTo(o, visitor, helper);
  }

  public abstract void internalAcceptHashing(HashingVisitor visitor);

  public final void acceptHashing(HashingVisitor visitor) {
    visitor.visitInt(getCompareToId());
    internalAcceptHashing(visitor);
  }

  @Override
  public String toString() {
    CfPrinter printer = new CfPrinter();
    print(printer);
    return printer.toString();
  }

  public void registerUse(
      UseRegistry registry, ProgramMethod context, ListIterator<CfInstruction> iterator) {
    internalRegisterUse(registry, context, iterator);
  }

  public void registerUseForDesugaring(
      UseRegistry registry, ClasspathMethod context, ListIterator<CfInstruction> iterator) {
    internalRegisterUse(registry, context, iterator);
  }

  void internalRegisterUse(
      UseRegistry<?> registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    // Intentionally empty.
  }

  public CfLabel getTarget() {
    return null;
  }

  public final void forEachNormalTarget(
      Consumer<? super CfInstruction> consumer, CfInstruction fallthroughInstruction) {
    traverseNormalTargets(
        (target, ignore) -> {
          consumer.accept(target);
          return TraversalContinuation.doContinue();
        },
        fallthroughInstruction,
        null);
  }

  public <BT, CT> TraversalContinuation<BT, CT> traverseNormalTargets(
      BiFunction<? super CfInstruction, ? super CT, TraversalContinuation<BT, CT>> fn,
      CfInstruction fallthroughInstruction,
      CT initialValue) {
    // The method is overridden in each jump instruction.
    assert !isJump();
    if (fallthroughInstruction != null) {
      return fn.apply(fallthroughInstruction, initialValue);
    }
    // There may be a label after the last return.
    assert isLabel();
    return TraversalContinuation.doContinue(initialValue);
  }

  public boolean isArrayStore() {
    return false;
  }

  public CfArrayStore asArrayStore() {
    return null;
  }

  @Override
  public CfInstruction asCfInstruction() {
    return this;
  }

  @Override
  public boolean isCfInstruction() {
    return true;
  }

  @Override
  public DexInstruction asDexInstruction() {
    return null;
  }

  public CfRecordFieldValues asRecordFieldValues() {
    return null;
  }

  public boolean isRecordFieldValues() {
    return false;
  }

  public CfNew asNew() {
    return null;
  }

  public boolean isNew() {
    return false;
  }

  public CfConstString asConstString() {
    return null;
  }

  public boolean isConstString() {
    return false;
  }

  public CfConstDynamic asConstDynamic() {
    return null;
  }

  public boolean isConstDynamic() {
    return false;
  }

  public CfFieldInstruction asFieldInstruction() {
    return null;
  }

  public boolean isFieldInstruction() {
    return false;
  }

  public boolean isFieldGet() {
    return false;
  }

  public boolean isInstanceFieldGet() {
    return false;
  }

  public CfInstanceFieldRead asInstanceFieldGet() {
    return null;
  }

  public boolean isStaticFieldGet() {
    return false;
  }

  public boolean isFieldPut() {
    return false;
  }

  public boolean isInstanceFieldPut() {
    return false;
  }

  public CfInstanceFieldWrite asInstanceFieldPut() {
    return null;
  }

  public boolean isStaticFieldPut() {
    return false;
  }

  public CfStaticFieldWrite asStaticFieldPut() {
    return null;
  }

  public CfGoto asGoto() {
    return null;
  }

  public boolean isGoto() {
    return false;
  }

  public CfInvoke asInvoke() {
    return null;
  }

  public boolean isInvoke() {
    return false;
  }

  public CfInvokeDynamic asInvokeDynamic() {
    return null;
  }

  public boolean isInvokeDynamic() {
    return false;
  }

  public boolean isInvokeSpecial() {
    return false;
  }

  public boolean isInvokeStatic() {
    return false;
  }

  public boolean isInvokeVirtual() {
    return false;
  }

  public boolean isInvokeInterface() {
    return false;
  }

  public CfLabel asLabel() {
    return null;
  }

  public boolean isLabel() {
    return false;
  }

  public CfFrame asFrame() {
    return null;
  }

  public boolean isFrame() {
    return false;
  }

  public CfPosition asPosition() {
    return null;
  }

  public boolean isPosition() {
    return false;
  }

  public CfLoad asLoad() {
    return null;
  }

  public boolean isLoad() {
    return false;
  }

  public CfStore asStore() {
    return null;
  }

  public boolean isInstanceOf() {
    return false;
  }

  public CfInstanceOf asInstanceOf() {
    return null;
  }

  public boolean isStore() {
    return false;
  }

  public CfSwitch asSwitch() {
    return null;
  }

  public boolean isSwitch() {
    return false;
  }

  public CfThrow asThrow() {
    return null;
  }

  public boolean isThrow() {
    return false;
  }

  public CfTypeInstruction asTypeInstruction() {
    return null;
  }

  public boolean isTypeInstruction() {
    return false;
  }

  public boolean isInitClass() {
    return false;
  }

  public CfDexItemBasedConstString asDexItemBasedConstString() {
    return null;
  }

  public boolean isDexItemBasedConstString() {
    return false;
  }

  /** Return true if this instruction is CfReturn or CfReturnVoid. */
  public boolean isReturn() {
    return false;
  }

  public CfReturn asReturn() {
    return null;
  }

  public boolean isReturnVoid() {
    return false;
  }

  /** Return true if this instruction is CfIf or CfIfCmp. */
  public boolean isConditionalJump() {
    return false;
  }

  /** Return true if this instruction is CfIf, CfIfCmp, CfSwitch, CfGoto, CfThrow,
   * CfReturn or CfReturnVoid. */
  public boolean isJump() {
    return false;
  }

  public CfJumpInstruction asJump() {
    return null;
  }

  /**
   * @return true if this instruction is {@link CfGoto}, {@link CfIf}, {@link CfIfCmp}, or {@link
   *     CfSwitch}.
   */
  public boolean isJumpWithNormalTarget() {
    return false;
  }

  /** Return true if this instruction or its DEX equivalent can throw. */
  public boolean canThrow() {
    return false;
  }

  @Override
  public final boolean instructionTypeCanThrow() {
    return canThrow();
  }

  public abstract void buildIR(IRBuilder builder, CfState state, CfSourceCode code);

  /** Return true if this instruction directly emits IR instructions. */
  public boolean emitsIR() {
    return true;
  }

  public abstract ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context);

  public abstract CfFrameState evaluate(
      CfFrameState frame, AppView<?> appView, CfAnalysisConfig config);

  public boolean isCheckCast() {
    return false;
  }

  public CfCheckCast asCheckCast() {
    return null;
  }

  public boolean isConstClass() {
    return false;
  }

  public CfConstClass asConstClass() {
    return null;
  }
}
