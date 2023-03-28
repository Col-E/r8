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
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.TraversalUtils;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfSwitch extends CfJumpInstruction {

  public enum Kind { LOOKUP, TABLE }

  private final Kind kind;
  private final CfLabel defaultTarget;
  private final int[] keys;
  private final List<CfLabel> targets;

  public CfSwitch(Kind kind, CfLabel defaultTarget, int[] keys, List<CfLabel> targets) {
    this.kind = kind;
    this.defaultTarget = defaultTarget;
    this.keys = keys;
    this.targets = targets;
    assert kind != Kind.LOOKUP || keys.length == targets.size();
    assert kind != Kind.TABLE || keys.length == 1;
  }

  @Override
  public <BT, CT> TraversalContinuation<BT, CT> traverseNormalTargets(
      BiFunction<? super CfInstruction, ? super CT, TraversalContinuation<BT, CT>> fn,
      CfInstruction fallthroughInstruction,
      CT initialValue) {
    return TraversalUtils.traverseIterable(targets, fn, initialValue)
        .ifContinueThen(
            continuation -> fn.apply(defaultTarget, continuation.getValueOrDefault(null)));
  }

  @Override
  public int getCompareToId() {
    return kind == Kind.LOOKUP ? Opcodes.LOOKUPSWITCH : Opcodes.TABLESWITCH;
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    assert kind == ((CfSwitch) other).kind;
    return visitor.visit(
        this,
        (CfSwitch) other,
        spec ->
            spec.withCustomItem(CfSwitch::getDefaultTarget, helper.labelAcceptor())
                .withIntArray(i -> i.keys)
                .withCustomItemCollection(CfSwitch::getSwitchTargets, helper.labelAcceptor()));
  }

  @Override
  public void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visitInt(keys.length);
    for (int i = 0; i < keys.length; i++) {
      visitor.visitInt(keys[i]);
    }
    // No label identity info to add.
  }

  public Kind getKind() {
    return kind;
  }

  public CfLabel getDefaultTarget() {
    return defaultTarget;
  }

  public List<Integer> getKeys() {
    return new IntArrayList(keys);
  }

  public List<CfLabel> getSwitchTargets() {
    return targets;
  }

  @Override
  public CfSwitch asSwitch() {
    return this;
  }

  @Override
  public boolean isSwitch() {
    return true;
  }

  @Override
  public boolean isJumpWithNormalTarget() {
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
    Label[] labels = new Label[targets.size()];
    for (int i = 0; i < targets.size(); i++) {
      labels[i] = targets.get(i).getLabel();
    }
    switch (kind) {
      case LOOKUP:
        visitor.visitLookupSwitchInsn(defaultTarget.getLabel(), keys, labels);
        break;
      case TABLE: {
        int min = keys[0];
        int max = min + targets.size() - 1;
        visitor.visitTableSwitchInsn(min, max, defaultTarget.getLabel(), labels);
      }
    }
  }

  @Override
  public int bytecodeSizeUpperBound() {
    switch (kind) {
      case LOOKUP:
        return 8 + keys.length * 8;
      case TABLE:
        int min = keys[0];
        int max = min + targets.size() - 1;
        return 16 + (max - min + 1) * 4;
      default:
        throw new Unreachable();
    }
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    int[] labelOffsets = new int[targets.size()];
    for (int i = 0; i < targets.size(); i++) {
      labelOffsets[i] = code.getLabelOffset(targets.get(i));
    }
    Slot value = state.pop();
    builder.addSwitch(value.register, keys, code.getLabelOffset(defaultTarget), labelOffsets);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forJumpInstruction();
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ..., index/key →
    // ...
    return frame.popInitialized(appView, config, appView.dexItemFactory().intType);
  }
}
