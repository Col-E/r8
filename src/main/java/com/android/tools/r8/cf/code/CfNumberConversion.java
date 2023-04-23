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

import java.util.Map;

public class CfNumberConversion extends CfInstruction {
  public static final CfNumberConversion I2L = new CfNumberConversion( NumericType.INT, NumericType.LONG);
  public static final CfNumberConversion I2F = new CfNumberConversion( NumericType.INT, NumericType.FLOAT);
  public static final CfNumberConversion I2D = new CfNumberConversion( NumericType.INT, NumericType.DOUBLE);
  public static final CfNumberConversion L2I = new CfNumberConversion( NumericType.LONG, NumericType.INT);
  public static final CfNumberConversion L2F = new CfNumberConversion( NumericType.LONG, NumericType.FLOAT);
  public static final CfNumberConversion L2D = new CfNumberConversion( NumericType.LONG, NumericType.DOUBLE);
  public static final CfNumberConversion F2I = new CfNumberConversion( NumericType.FLOAT, NumericType.INT);
  public static final CfNumberConversion F2L = new CfNumberConversion( NumericType.FLOAT, NumericType.LONG);
  public static final CfNumberConversion F2D = new CfNumberConversion( NumericType.FLOAT, NumericType.DOUBLE);
  public static final CfNumberConversion D2I = new CfNumberConversion( NumericType.DOUBLE, NumericType.INT);
  public static final CfNumberConversion D2L = new CfNumberConversion( NumericType.DOUBLE, NumericType.LONG);
  public static final CfNumberConversion D2F = new CfNumberConversion( NumericType.DOUBLE, NumericType.FLOAT);
  public static final CfNumberConversion I2B = new CfNumberConversion( NumericType.INT, NumericType.BYTE);
  public static final CfNumberConversion I2C = new CfNumberConversion( NumericType.INT, NumericType.CHAR);
  public static final CfNumberConversion I2S = new CfNumberConversion( NumericType.INT, NumericType.SHORT);

  private final NumericType from;
  private final NumericType to;

  public CfNumberConversion(NumericType from, NumericType to) {
    assert from != to;
    assert from != NumericType.BYTE && from != NumericType.SHORT && from != NumericType.CHAR;
    assert (to != NumericType.BYTE && to != NumericType.SHORT && to != NumericType.CHAR)
        || from == NumericType.INT;
    this.from = from;
    this.to = to;
  }

  public static CfInstruction convert(NumericType from, NumericType to) {
    return fromAsm(getAsmOpcode(from, to));
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

  public NumericType getFromType() {
    return from;
  }

  public NumericType getToType() {
    return to;
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
    return getAsmOpcode(from, to);
  }

  public static int getAsmOpcode(NumericType from, NumericType to) {
    switch (from) {
      case INT:
        switch (to) {
          case BYTE:
            return Opcodes.I2B;
          case CHAR:
            return Opcodes.I2C;
          case SHORT:
            return Opcodes.I2S;
          case LONG:
            return Opcodes.I2L;
          case FLOAT:
            return Opcodes.I2F;
          case DOUBLE:
            return Opcodes.I2D;
          default:
            throw new Unreachable("Invalid CfNumberConversion from " + from + " to " + to);
        }
      case LONG:
        switch (to) {
          case INT:
            return Opcodes.L2I;
          case FLOAT:
            return Opcodes.L2F;
          case DOUBLE:
            return Opcodes.L2D;
          default:
            throw new Unreachable("Invalid CfNumberConversion from " + from + " to " + to);
        }
      case FLOAT:
        switch (to) {
          case INT:
            return Opcodes.F2I;
          case LONG:
            return Opcodes.F2L;
          case DOUBLE:
            return Opcodes.F2D;
          default:
            throw new Unreachable("Invalid CfNumberConversion from " + from + " to " + to);
        }
      case DOUBLE:
        switch (to) {
          case INT:
            return Opcodes.D2I;
          case LONG:
            return Opcodes.D2L;
          case FLOAT:
            return Opcodes.D2F;
          default:
            throw new Unreachable("Invalid CfNumberConversion from " + from + " to " + to);
        }
      default:
        throw new Unreachable("Invalid CfNumberConversion from " + from + " to " + to);
    }
  }

  @Nonnull
  public static CfNumberConversion fromAsm(int opcode) {
    switch (opcode) {
      case Opcodes.I2L: return I2L;
      case Opcodes.I2F: return I2F;
      case Opcodes.I2D: return I2D;
      case Opcodes.L2I: return L2I;
      case Opcodes.L2F: return L2F;
      case Opcodes.L2D: return L2D;
      case Opcodes.F2I: return F2I;
      case Opcodes.F2L: return F2L;
      case Opcodes.F2D: return F2D;
      case Opcodes.D2I: return D2I;
      case Opcodes.D2L: return D2L;
      case Opcodes.D2F: return D2F;
      case Opcodes.I2B: return I2B;
      case Opcodes.I2C: return I2C;
      case Opcodes.I2S: return I2S;
      default:
        throw new Unreachable("Unexpected CfNumberConversion opcode " + opcode);
    }
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    int source = state.pop().register;
    builder.addConversion(to, from, state.push(ValueType.fromNumericType(to)).register, source);
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
    return frame.popInitialized(appView, config, from).push(appView, config, to);
  }
}
