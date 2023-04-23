// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.InitClassLens;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import javax.annotation.Nonnull;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.annotation.Nonnull;
import java.util.Map;

public class CfNeg extends CfInstruction {
  public static final CfNeg INEG = new CfNeg(NumericType.INT);
  public static final CfNeg LNEG = new CfNeg(NumericType.LONG);
  public static final CfNeg FNEG = new CfNeg(NumericType.FLOAT);
  public static final CfNeg DNEG = new CfNeg(NumericType.DOUBLE);
  
  private final NumericType type;

  private CfNeg(NumericType type) {
    this.type = type;
  }
  
  @Nonnull
  public static CfNeg neg(@Nonnull NumericType type) {
    switch (type) {
      case BYTE:
      case CHAR:
      case SHORT:
      case INT:
        return INEG;
      case LONG:
        return LNEG;
      case FLOAT:
        return FNEG;
      case DOUBLE:
        return DNEG;
      default:
        throw new Unreachable("Invalid type for CfNeg " + type);
    }
  }

  public NumericType getType() {
    return type;
  }

  @Override
  public int getCompareToId() {
    return getAsmOpcode();
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    return CfCompareHelper.compareIdUniquelyDeterminesEquality(this, other);
  }

  @Override
  public void internalAcceptHashing(HashingVisitor visitor) {
    // Nothing to add.
  }

  @Override
  public void write(
      AppView<?> appView,
      ProgramMethod context,
      DexItemFactory dexItemFactory,
      GraphLens graphLens,
      InitClassLens initClassLens,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor) {
    visitor.visitInsn(getAsmOpcode());
  }

  @Override
  public int bytecodeSizeUpperBound() {
    return 1;
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Nonnull
  @Override
  public CfInstruction copy(@Nonnull Map<CfLabel, CfLabel> labelMap) {
    return this;
  }

  public int getAsmOpcode() {
    switch (type) {
      case BYTE:
      case CHAR:
      case SHORT:
      case INT:
        return Opcodes.INEG;
      case LONG:
        return Opcodes.LNEG;
      case FLOAT:
        return Opcodes.FNEG;
      case DOUBLE:
        return Opcodes.DNEG;
      default:
        throw new Unreachable("Invalid type for CfNeg " + type);
    }
  }

  public static CfNeg fromAsm(int opcode) {
    switch (opcode) {
      case Opcodes.INEG: return INEG;
      case Opcodes.LNEG: return LNEG;
      case Opcodes.FNEG: return FNEG;
      case Opcodes.DNEG: return DNEG;
      default:
        throw new Unreachable("Invalid opcode for CfNeg " + opcode);
    }
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    int value = state.pop().register;
    builder.addNeg(type, state.push(ValueType.fromNumericType(type)).register, value);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forUnop();
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ..., value →
    // ..., result
    return frame.popInitialized(appView, config, type).push(appView, config, type);
  }
}
