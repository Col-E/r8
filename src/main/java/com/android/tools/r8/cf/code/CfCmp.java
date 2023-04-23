// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
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
import com.android.tools.r8.ir.code.Cmp;
import com.android.tools.r8.ir.code.Cmp.Bias;
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

public class CfCmp extends CfInstruction {
  public static final CfCmp LCMP = new CfCmp(Bias.NONE, NumericType.LONG);
  public static final CfCmp FCMPL = new CfCmp(Bias.LT, NumericType.FLOAT);
  public static final CfCmp FCMPG = new CfCmp(Bias.GT, NumericType.FLOAT);
  public static final CfCmp DCMPL = new CfCmp(Bias.LT, NumericType.DOUBLE);
  public static final CfCmp DCMPG = new CfCmp(Bias.GT, NumericType.DOUBLE);

  private final Bias bias;
  private final NumericType type;

  private CfCmp(Bias bias, NumericType type) {
    assert bias != null;
    assert type != null;
    assert type == NumericType.LONG || type == NumericType.FLOAT || type == NumericType.DOUBLE;
    assert type != NumericType.LONG || bias == Cmp.Bias.NONE;
    assert type == NumericType.LONG || bias != Cmp.Bias.NONE;
    this.bias = bias;
    this.type = type;
  }

  @Nonnull
  public static CfInstruction compare(@Nonnull Bias bias, @Nonnull NumericType type) {
    if (bias == Bias.NONE) {
      if (type == NumericType.LONG) return LCMP;
    } else if (bias == Bias.LT) {
      if (type == NumericType.FLOAT) return FCMPL;
      else if (type == NumericType.DOUBLE) return DCMPL;
    } else if (bias == Bias.GT) {
      if (type == NumericType.FLOAT) return FCMPG;
      else if (type == NumericType.DOUBLE) return DCMPG;
    }
    return new CfCmp(bias, type);
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

  public Bias getBias() {
    return bias;
  }

  public NumericType getType() {
    return type;
  }

  public static CfCmp fromAsm(int opcode) {
    switch (opcode) {
      case Opcodes.LCMP:  return LCMP;
      case Opcodes.FCMPL: return FCMPL;
      case Opcodes.FCMPG: return FCMPG;
      case Opcodes.DCMPL: return DCMPL;
      case Opcodes.DCMPG: return DCMPG;
      default:
        throw new Unreachable("Wrong ASM opcode for CfCmp " + opcode);
    }
  }

  public int getAsmOpcode() {
    switch (type) {
      case LONG:
        return Opcodes.LCMP;
      case FLOAT:
        return bias == Cmp.Bias.LT ? Opcodes.FCMPL : Opcodes.FCMPG;
      case DOUBLE:
        return bias == Cmp.Bias.LT ? Opcodes.DCMPL : Opcodes.DCMPG;
      default:
        throw new Unreachable("CfCmp has unknown type " + type);
    }
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
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    int right = state.pop().register;
    int left = state.pop().register;
    builder.addCmp(type, bias, state.push(ValueType.INT).register, left, right);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forBinop();
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ..., value1, value2 →
    // ..., result
    return frame
        .popInitialized(appView, config, type)
        .popInitialized(appView, config, type)
        .push(config, appView.dexItemFactory().intType);
  }
}
