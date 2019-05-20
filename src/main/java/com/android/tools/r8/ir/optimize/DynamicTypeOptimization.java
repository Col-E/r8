// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodOptimizationInfo;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.Assume.DynamicTypeAssumption;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.JumpInstruction;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.base.Predicates;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Predicate;

public class DynamicTypeOptimization {

  private final AppView<AppInfoWithLiveness> appView;

  public DynamicTypeOptimization(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public void insertAssumeDynamicTypeInstructions(IRCode code) {
    insertAssumeDynamicTypeInstructionsInBlocks(code, code.listIterator(), Predicates.alwaysTrue());
  }

  public void insertAssumeDynamicTypeInstructionsInBlocks(
      IRCode code, ListIterator<BasicBlock> blockIterator, Predicate<BasicBlock> blockTester) {
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (blockTester.test(block)) {
        insertAssumeDynamicTypeInstructionsInBlock(code, blockIterator, block);
      }
    }
  }

  // TODO(b/127461806): Should also insert AssumeDynamicType instructions after instanceof
  //  instructions.
  private void insertAssumeDynamicTypeInstructionsInBlock(
      IRCode code, ListIterator<BasicBlock> blockIterator, BasicBlock block) {
    InstructionListIterator instructionIterator = block.listIterator();
    while (instructionIterator.hasNext()) {
      Instruction current = instructionIterator.next();
      if (current.isInvokeMethod()) {
        InvokeMethod invoke = current.asInvokeMethod();
        Value outValue = invoke.outValue();
        if (outValue == null) {
          continue;
        }

        DexType staticReturnTypeRaw = invoke.getInvokedMethod().proto.returnType;
        if (!staticReturnTypeRaw.isReferenceType()) {
          continue;
        }

        DexEncodedMethod singleTarget =
            invoke.lookupSingleTarget(appView, code.method.method.holder);
        if (singleTarget == null) {
          continue;
        }

        MethodOptimizationInfo optimizationInfo = singleTarget.getOptimizationInfo();
        if (optimizationInfo.returnsArgument()) {
          continue;
        }

        TypeLatticeElement dynamicReturnType = optimizationInfo.getDynamicReturnType();
        if (dynamicReturnType == null
            || !dynamicReturnType.strictlyLessThan(outValue.getTypeLattice(), appView)) {
          continue;
        }

        // Split block if needed (only debug instructions are allowed after the throwing
        // instruction, if any).
        BasicBlock insertionBlock =
            block.hasCatchHandlers() ? instructionIterator.split(code, blockIterator) : block;

        // Replace usages of out-value by the out-value of the AssumeDynamicType instruction.
        Value specializedOutValue =
            code.createValue(outValue.getTypeLattice(), outValue.getLocalInfo());
        outValue.replaceUsers(specializedOutValue);

        // Insert AssumeDynamicType instruction.
        Assume<DynamicTypeAssumption> assumeInstruction =
            Assume.createAssumeDynamicTypeInstruction(
                dynamicReturnType, specializedOutValue, outValue, invoke, appView);
        assumeInstruction.setPosition(
            appView.options().debug ? invoke.getPosition() : Position.none());
        if (insertionBlock == block) {
          instructionIterator.add(assumeInstruction);
        } else {
          insertionBlock.listIterator().add(assumeInstruction);
        }
      }
    }
  }

  /**
   * Computes the dynamic return type of the given method.
   *
   * <p>If the method has no normal exits, then null is returned.
   */
  public TypeLatticeElement computeDynamicReturnType(DexEncodedMethod method, IRCode code) {
    assert method.method.proto.returnType.isReferenceType();
    List<TypeLatticeElement> returnedTypes = new ArrayList<>();
    for (BasicBlock block : code.blocks) {
      JumpInstruction exitInstruction = block.exit();
      if (exitInstruction.isReturn()) {
        returnedTypes.add(exitInstruction.asReturn().getReturnType());
      }
    }
    return returnedTypes.isEmpty() ? null : TypeLatticeElement.join(returnedTypes, appView);
  }
}
