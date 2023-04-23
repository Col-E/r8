// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.InitClassLens;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.CfState.Slot;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import com.android.tools.r8.optimize.interfaces.analysis.ErroneousCfFrameState;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import javax.annotation.Nonnull;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.annotation.Nonnull;
import java.util.Map;

public class CfLoad extends CfInstruction {
  public static final CfLoad ALOAD_0 = new CfLoad(ValueType.OBJECT, 0);
  public static final CfLoad ALOAD_1 = new CfLoad(ValueType.OBJECT, 1);
  public static final CfLoad ALOAD_2 = new CfLoad(ValueType.OBJECT, 2);
  public static final CfLoad ALOAD_3 = new CfLoad(ValueType.OBJECT, 3);
  public static final CfLoad ALOAD_4 = new CfLoad(ValueType.OBJECT, 4);
  public static final CfLoad ALOAD_5 = new CfLoad(ValueType.OBJECT, 5);
  public static final CfLoad ALOAD_6 = new CfLoad(ValueType.OBJECT, 6);
  public static final CfLoad ALOAD_7 = new CfLoad(ValueType.OBJECT, 7);
  public static final CfLoad ILOAD_0 = new CfLoad(ValueType.INT, 0);
  public static final CfLoad ILOAD_1 = new CfLoad(ValueType.INT, 1);
  public static final CfLoad ILOAD_2 = new CfLoad(ValueType.INT, 2);
  public static final CfLoad ILOAD_3 = new CfLoad(ValueType.INT, 3);
  public static final CfLoad ILOAD_4 = new CfLoad(ValueType.INT, 4);
  public static final CfLoad ILOAD_5 = new CfLoad(ValueType.INT, 5);
  public static final CfLoad ILOAD_6 = new CfLoad(ValueType.INT, 6);
  public static final CfLoad ILOAD_7 = new CfLoad(ValueType.INT, 7);
  public static final CfLoad FLOAD_0 = new CfLoad(ValueType.FLOAT, 0);
  public static final CfLoad FLOAD_1 = new CfLoad(ValueType.FLOAT, 1);
  public static final CfLoad FLOAD_2 = new CfLoad(ValueType.FLOAT, 2);
  public static final CfLoad FLOAD_3 = new CfLoad(ValueType.FLOAT, 3);
  public static final CfLoad FLOAD_4 = new CfLoad(ValueType.FLOAT, 4);
  public static final CfLoad FLOAD_5 = new CfLoad(ValueType.FLOAT, 5);
  public static final CfLoad LLOAD_0 = new CfLoad(ValueType.LONG, 0);
  public static final CfLoad LLOAD_1 = new CfLoad(ValueType.LONG, 1);
  public static final CfLoad LLOAD_2 = new CfLoad(ValueType.LONG, 2);
  public static final CfLoad LLOAD_3 = new CfLoad(ValueType.LONG, 3);
  public static final CfLoad LLOAD_4 = new CfLoad(ValueType.LONG, 4);
  public static final CfLoad LLOAD_5 = new CfLoad(ValueType.LONG, 5);
  public static final CfLoad DLOAD_0 = new CfLoad(ValueType.DOUBLE, 0);
  public static final CfLoad DLOAD_1 = new CfLoad(ValueType.DOUBLE, 1);
  public static final CfLoad DLOAD_2 = new CfLoad(ValueType.DOUBLE, 2);
  public static final CfLoad DLOAD_3 = new CfLoad(ValueType.DOUBLE, 3);
  public static final CfLoad DLOAD_4 = new CfLoad(ValueType.DOUBLE, 4);
  public static final CfLoad DLOAD_5 = new CfLoad(ValueType.DOUBLE, 5);
  private final int var;
  private final ValueType type;

  private CfLoad(ValueType type, int var) {
    this.var = var;
    this.type = type;
  }

  @Nonnull
  public static CfLoad load(ValueType type, int var) {
    switch (type) {
      case OBJECT: return loadObject(var);
      case INT:    return loadInt(var);
      case FLOAT:  return loadFloat(var);
      case LONG:   return loadLong(var);
      case DOUBLE: return loadDouble(var);
      default:
        throw new IllegalStateException("Unknown value type: " + type);
    }
  }

  @Nonnull
  public static CfLoad loadObject(int var) {
    switch (var) {
      case 0: return ALOAD_0;
      case 1: return ALOAD_1;
      case 2: return ALOAD_2;
      case 3: return ALOAD_3;
      case 4: return ALOAD_4;
      case 5: return ALOAD_5;
      case 6: return ALOAD_6;
      case 7: return ALOAD_7;
      default:
        return new CfLoad(ValueType.OBJECT, var);
    }
  }

  @Nonnull
  public static CfLoad loadInt(int var) {
    switch (var) {
      case 0: return ILOAD_0;
      case 1: return ILOAD_1;
      case 2: return ILOAD_2;
      case 3: return ILOAD_3;
      case 4: return ILOAD_4;
      case 5: return ILOAD_5;
      case 6: return ILOAD_6;
      case 7: return ILOAD_7;
      default:
        return new CfLoad(ValueType.INT, var);
    }
  }

  @Nonnull
  public static CfLoad loadFloat(int var) {
    switch (var) {
      case 0: return FLOAD_0;
      case 1: return FLOAD_1;
      case 2: return FLOAD_2;
      case 3: return FLOAD_3;
      case 4: return FLOAD_4;
      case 5: return FLOAD_5;
      default:
        return new CfLoad(ValueType.FLOAT, var);
    }
  }

  @Nonnull
  public static CfLoad loadLong(int var) {
    switch (var) {
      case 0: return LLOAD_0;
      case 1: return LLOAD_1;
      case 2: return LLOAD_2;
      case 3: return LLOAD_3;
      case 4: return LLOAD_4;
      case 5: return LLOAD_5;
      default:
        return new CfLoad(ValueType.LONG, var);
    }
  }

  @Nonnull
  public static CfLoad loadDouble(int var) {
    switch (var) {
      case 0: return DLOAD_0;
      case 1: return DLOAD_1;
      case 2: return DLOAD_2;
      case 3: return DLOAD_3;
      case 4: return DLOAD_4;
      case 5: return DLOAD_5;
      default:
        return new CfLoad(ValueType.DOUBLE, var);
    }
  }

  @Override
  public int getCompareToId() {
    return getLoadType();
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    return visitor.visitInt(var, other.asLoad().var);
  }

  @Override
  public void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visitInt(var);
  }

  private int getLoadType() {
    switch (type) {
      case OBJECT: return Opcodes.ALOAD;
      case INT:    return Opcodes.ILOAD;
      case FLOAT:  return Opcodes.FLOAD;
      case LONG:   return Opcodes.LLOAD;
      case DOUBLE: return Opcodes.DLOAD;
      default: throw new Unreachable("Unexpected type " + type);
    }
  }

  @Override
  public CfLoad asLoad() {
    return this;
  }

  @Override
  public boolean isLoad() {
    return true;
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
    visitor.visitVarInsn(getLoadType(), var);
  }

  @Override
  public int bytecodeSizeUpperBound() {
    // xload_0 .. xload_3, xload or wide xload, where x is a, i, f, l or d
    return var <= 3 ? 1 : ((var < 256) ? 2 : 4);
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

  public ValueType getType() {
    return type;
  }

  public int getLocalIndex() {
    return var;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    Slot local = state.read(var);
    Slot stack = state.push(local);
    builder.addMove(local.type, stack.register, local.register);
  }

  @Override
  public boolean emitsIR() {
    return false;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forLoad();
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ... →
    // ..., objectref
    return frame.readLocal(
        appView,
        config,
        getLocalIndex(),
        type,
        (state, frameType) ->
            frameType.isPrecise() ? state.push(config, frameType.asPrecise()): error(frameType));
  }

  private ErroneousCfFrameState error(FrameType frameType) {
    assert frameType.isOneWord() || frameType.isTwoWord();
    StringBuilder message =
        new StringBuilder("Unexpected attempt to read local of type top at index ")
            .append(getLocalIndex());
    if (type.isWide()) {
      message.append(" and ").append(getLocalIndex() + 1);
    }
    return CfFrameState.error(message.toString());
  }
}
