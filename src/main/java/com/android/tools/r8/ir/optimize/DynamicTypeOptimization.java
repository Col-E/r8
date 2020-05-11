// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.Assume.DynamicTypeAssumption;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.JumpInstruction;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Predicate;

public class DynamicTypeOptimization implements Assumer {

  private final AppView<AppInfoWithLiveness> appView;

  public DynamicTypeOptimization(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public void insertAssumeInstructions(IRCode code, Timing timing) {
    insertAssumeInstructionsInBlocks(code, code.listIterator(), alwaysTrue(), timing);
  }

  @Override
  public void insertAssumeInstructionsInBlocks(
      IRCode code,
      BasicBlockIterator blockIterator,
      Predicate<BasicBlock> blockTester,
      Timing timing) {
    timing.begin("Insert assume dynamic type instructions");
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (blockTester.test(block)) {
        insertAssumeDynamicTypeInstructionsInBlock(code, blockIterator, block);
      }
    }
    timing.end();
  }

  // TODO(b/127461806): Should also insert AssumeDynamicType instructions after instanceof
  //  instructions.
  private void insertAssumeDynamicTypeInstructionsInBlock(
      IRCode code, ListIterator<BasicBlock> blockIterator, BasicBlock block) {
    InstructionListIterator instructionIterator = block.listIterator(code);
    while (instructionIterator.hasNext()) {
      Instruction current = instructionIterator.next();
      if (!current.hasOutValue() || !current.outValue().isUsed()) {
        continue;
      }

      TypeElement dynamicUpperBoundType;
      ClassTypeElement dynamicLowerBoundType;
      if (current.isInvokeMethod()) {
        InvokeMethod invoke = current.asInvokeMethod();
        DexMethod invokedMethod = invoke.getInvokedMethod();

        DexType staticReturnTypeRaw = invokedMethod.proto.returnType;
        if (!staticReturnTypeRaw.isReferenceType()) {
          continue;
        }

        if (invokedMethod.holder.isArrayType()
            && invokedMethod.match(appView.dexItemFactory().objectMembers.clone)) {
          dynamicUpperBoundType =
              TypeElement.fromDexType(invokedMethod.holder, definitelyNotNull(), appView);
          dynamicLowerBoundType = null;
        } else {
          DexEncodedMethod singleTarget = invoke.lookupSingleTarget(appView, code.context());
          if (singleTarget == null) {
            continue;
          }

          MethodOptimizationInfo optimizationInfo = singleTarget.getOptimizationInfo();
          if (optimizationInfo.returnsArgument()) {
            // Don't insert an assume-instruction since we will replace all usages of the out-value
            // by the corresponding argument.
            continue;
          }

          dynamicUpperBoundType = optimizationInfo.getDynamicUpperBoundType();
          dynamicLowerBoundType = optimizationInfo.getDynamicLowerBoundType();
        }
      } else if (current.isStaticGet()) {
        StaticGet staticGet = current.asStaticGet();
        DexEncodedField encodedField =
            appView.appInfo().resolveField(staticGet.getField()).getResolvedField();
        if (encodedField == null) {
          continue;
        }

        dynamicUpperBoundType = encodedField.getOptimizationInfo().getDynamicUpperBoundType();
        dynamicLowerBoundType = encodedField.getOptimizationInfo().getDynamicLowerBoundType();
      } else {
        continue;
      }

      Value outValue = current.outValue();
      boolean isTrivial =
          (dynamicUpperBoundType == null
                  || !dynamicUpperBoundType.strictlyLessThan(outValue.getType(), appView))
              && dynamicLowerBoundType == null;
      if (isTrivial) {
        continue;
      }

      if (dynamicUpperBoundType == null) {
        dynamicUpperBoundType = outValue.getType();
      }

      // Split block if needed (only debug instructions are allowed after the throwing
      // instruction, if any).
      BasicBlock insertionBlock =
          block.hasCatchHandlers() ? instructionIterator.split(code, blockIterator) : block;

      // Replace usages of out-value by the out-value of the AssumeDynamicType instruction.
      Value specializedOutValue = code.createValue(outValue.getType(), outValue.getLocalInfo());
      outValue.replaceUsers(specializedOutValue);

      // Insert AssumeDynamicType instruction.
      Assume<DynamicTypeAssumption> assumeInstruction =
          Assume.createAssumeDynamicTypeInstruction(
              dynamicUpperBoundType,
              dynamicLowerBoundType,
              specializedOutValue,
              outValue,
              current,
              appView);
      assumeInstruction.setPosition(
          appView.options().debug ? current.getPosition() : Position.none());
      if (insertionBlock == block) {
        instructionIterator.add(assumeInstruction);
      } else {
        insertionBlock.listIterator(code).add(assumeInstruction);
      }
    }
  }

  /**
   * Computes the dynamic return type of the given method.
   *
   * <p>If the method has no normal exits, then null is returned.
   */
  public TypeElement computeDynamicReturnType(DexEncodedMethod method, IRCode code) {
    assert method.method.proto.returnType.isReferenceType();
    List<TypeElement> returnedTypes = new ArrayList<>();
    for (BasicBlock block : code.blocks) {
      JumpInstruction exitInstruction = block.exit();
      if (exitInstruction.isReturn()) {
        Value returnValue = exitInstruction.asReturn().returnValue();
        returnedTypes.add(returnValue.getDynamicUpperBoundType(appView));
      }
    }
    return returnedTypes.isEmpty() ? null : TypeElement.join(returnedTypes, appView);
  }

  public ClassTypeElement computeDynamicLowerBoundType(DexEncodedMethod method, IRCode code) {
    assert method.method.proto.returnType.isReferenceType();
    ClassTypeElement result = null;
    for (BasicBlock block : code.blocks) {
      JumpInstruction exitInstruction = block.exit();
      if (exitInstruction.isReturn()) {
        Value returnValue = exitInstruction.asReturn().returnValue();
        ClassTypeElement dynamicLowerBoundType = returnValue.getDynamicLowerBoundType(appView);
        if (dynamicLowerBoundType == null) {
          return null;
        }
        if (result == null) {
          result = dynamicLowerBoundType;
        } else if (dynamicLowerBoundType.equalUpToNullability(result)) {
          if (dynamicLowerBoundType.nullability() != result.nullability()) {
            result = dynamicLowerBoundType.join(result, appView).asClassType();
          }
        } else {
          return null;
        }
      }
    }
    return result;
  }
}
