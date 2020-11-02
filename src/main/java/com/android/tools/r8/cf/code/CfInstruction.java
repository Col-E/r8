// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.code.CfOrDexInstruction;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.ClasspathMethod;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import java.util.ListIterator;
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
  public abstract int internalCompareTo(CfInstruction other, CfCompareHelper helper);

  public final int compareTo(CfInstruction o, CfCompareHelper helper) {
    int diff = getCompareToId() - o.getCompareToId();
    return diff != 0 ? diff : internalCompareTo(o, helper);
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
      UseRegistry registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    // Intentionally empty.
  }

  public CfLabel getTarget() {
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

  public CfConstString asConstString() {
    return null;
  }

  public boolean isConstString() {
    return false;
  }

  public CfFieldInstruction asFieldInstruction() {
    return null;
  }

  public boolean isFieldInstruction() {
    return false;
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

  /** Return true if this instruction or its DEX equivalent can throw. */
  public boolean canThrow() {
    return false;
  }

  public abstract void buildIR(IRBuilder builder, CfState state, CfSourceCode code);

  /** Return true if this instruction directly emits IR instructions. */
  public boolean emitsIR() {
    return true;
  }

  public abstract ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context);

  public abstract void evaluate(
      CfFrameVerificationHelper frameBuilder,
      DexType context,
      DexType returnType,
      DexItemFactory factory,
      InitClassLens initClassLens);
}
