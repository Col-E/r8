// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexItemFactory.ObjectsMethods;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.util.Set;

public class ObjectsMethodOptimizer extends StatelessLibraryMethodModelCollection {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;
  private final ObjectsMethods objectsMethods;
  private final InternalOptions options;

  ObjectsMethodOptimizer(AppView<?> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    this.appView = appView;
    this.dexItemFactory = dexItemFactory;
    this.objectsMethods = dexItemFactory.objectsMethods;
    this.options = appView.options();
  }

  @Override
  public DexType getType() {
    return dexItemFactory.objectsType;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public void optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      Set<Value> affectedValues,
      Set<BasicBlock> blocksToRemove) {
    DexMethod singleTargetReference = singleTarget.getReference();
    switch (singleTargetReference.getName().byteAt(0)) {
      case 'e':
        if (singleTargetReference == objectsMethods.equals) {
          optimizeEquals(code, instructionIterator, invoke);
        }
        break;
      case 'h':
        if (singleTargetReference == objectsMethods.hashCode) {
          optimizeHashCode(code, instructionIterator, invoke);
        }
        break;
      case 'i':
        if (singleTargetReference == objectsMethods.isNull) {
          optimizeIsNull(code, instructionIterator, invoke);
        }
        break;
      case 'n':
        if (singleTargetReference == objectsMethods.nonNull) {
          optimizeNonNull(code, instructionIterator, invoke);
        }
        break;
      case 'r':
        if (objectsMethods.isRequireNonNullMethod(singleTargetReference)) {
          optimizeRequireNonNull(
              code,
              blockIterator,
              instructionIterator,
              invoke,
              affectedValues,
              blocksToRemove,
              singleTarget);
        }
        break;
      case 't':
        if (objectsMethods.isToStringMethod(singleTargetReference)) {
          optimizeToStringWithObject(
              code, instructionIterator, invoke, affectedValues, singleTarget);
        }
        break;
      default:
        // Intentionally empty.
        break;
    }
  }

  private void optimizeEquals(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Value aValue = invoke.getFirstArgument();
    Value bValue = invoke.getLastArgument();
    if (aValue.isAlwaysNull(appView)) {
      // Optimize Objects.equals(null, b) into true if b is null, false if b is never null, and
      // Objects.isNull(b) otherwise.
      if (bValue.isAlwaysNull(appView)) {
        instructionIterator.replaceCurrentInstructionWithConstTrue(code);
      } else if (bValue.isNeverNull()) {
        instructionIterator.replaceCurrentInstructionWithConstFalse(code);
      } else if (options.canUseJavaUtilObjectsIsNull()) {
        instructionIterator.replaceCurrentInstruction(
            InvokeStatic.builder()
                .setMethod(objectsMethods.isNull)
                .setOutValue(invoke.outValue())
                .setSingleArgument(bValue)
                .build());
      }
    } else if (aValue.isNeverNull()) {
      // Optimize Objects.equals(nonNull, b) into nonNull.equals(b).
      instructionIterator.replaceCurrentInstruction(
          InvokeVirtual.builder()
              .setMethod(dexItemFactory.objectMembers.equals)
              .setOutValue(invoke.outValue())
              .setArguments(ImmutableList.of(aValue, bValue))
              .build());
    }
  }

  private void optimizeHashCode(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Value inValue = invoke.getFirstArgument();
    if (inValue.isAlwaysNull(appView)) {
      // Optimize Objects.hashCode(null) into 0.
      instructionIterator.replaceCurrentInstructionWithConstInt(code, 0);
    } else if (inValue.isNeverNull()) {
      // Optimize Objects.hashCode(nonNull) into nonNull.hashCode().
      instructionIterator.replaceCurrentInstruction(
          InvokeVirtual.builder()
              .setMethod(dexItemFactory.objectMembers.hashCode)
              .setOutValue(invoke.outValue())
              .setSingleArgument(inValue)
              .build());
    }
  }

  private void optimizeIsNull(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Value inValue = invoke.getFirstArgument();
    if (inValue.isAlwaysNull(appView)) {
      // Optimize Objects.isNull(null) into true.
      instructionIterator.replaceCurrentInstructionWithConstTrue(code);
    } else if (inValue.isNeverNull()) {
      // Optimize Objects.isNull(nonNull) into false.
      instructionIterator.replaceCurrentInstructionWithConstFalse(code);
    }
  }

  private void optimizeNonNull(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Value inValue = invoke.getFirstArgument();
    if (inValue.isAlwaysNull(appView)) {
      // Optimize Objects.nonNull(null) into false.
      instructionIterator.replaceCurrentInstructionWithConstFalse(code);
    } else if (inValue.isNeverNull()) {
      // Optimize Objects.nonNull(nonNull) into true.
      instructionIterator.replaceCurrentInstructionWithConstTrue(code);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void optimizeRequireNonNull(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      Set<Value> affectedValues,
      Set<BasicBlock> blocksToRemove,
      DexClassAndMethod singleTarget) {
    if (invoke.hasOutValue() && invoke.outValue().hasLocalInfo()) {
      // Replacing the out-value with an in-value would change debug info.
      return;
    }

    Value inValue = invoke.getFirstArgument();
    if (inValue.isNeverNull()) {
      // Optimize Objects.requireNonNull*(nonNull, ...) into nonNull.
      if (invoke.hasOutValue()) {
        invoke.outValue().replaceUsers(inValue, affectedValues);
      }
      instructionIterator.removeOrReplaceByDebugLocalRead();
    } else if (inValue.isAlwaysNull(appView)) {
      if (singleTarget.getReference() == objectsMethods.requireNonNull) {
        // Optimize Objects.requireNonNull(null) into throw null.
        if (appView.hasClassHierarchy()) {
          instructionIterator.replaceCurrentInstructionWithThrowNull(
              appView.withClassHierarchy(), code, blockIterator, blocksToRemove, affectedValues);
        }
      } else if (singleTarget.getReference() == objectsMethods.requireNonNullElse) {
        // Optimize Objects.requireNonNullElse(null, defaultObj) into defaultObj if defaultObj
        // is never null.
        if (invoke.getLastArgument().isNeverNull()) {
          if (invoke.hasOutValue()) {
            invoke.outValue().replaceUsers(invoke.getLastArgument(), affectedValues);
          }
          instructionIterator.removeOrReplaceByDebugLocalRead();
        }
      } else if (singleTarget.getReference() == objectsMethods.requireNonNullElseGet) {
        // Don't optimize Objects.requireNonNullElseGet. The result of calling supplier.get() still
        // needs a null-check, so two invokes will be needed.
      }
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void optimizeToStringWithObject(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      Set<Value> affectedValues,
      DexClassAndMethod singleTarget) {
    Value object = invoke.getFirstArgument();
    TypeElement type = object.getType();

    // Optimize Objects.toString(null) into "null".
    if (type.isDefinitelyNull()) {
      if (singleTarget.getReference() == objectsMethods.toStringWithObject) {
        if (invoke.hasOutValue()) {
          affectedValues.addAll(invoke.outValue().affectedValues());
        }
        instructionIterator.replaceCurrentInstructionWithConstString(appView, code, "null");
      } else {
        assert singleTarget.getReference() == objectsMethods.toStringWithObjectAndNullDefault;
        if (invoke.hasOutValue()) {
          invoke.outValue().replaceUsers(invoke.getLastArgument(), affectedValues);
        }
        instructionIterator.removeOrReplaceByDebugLocalRead();
      }
      return;
    }

    // Optimize Objects.toString(nonNullString) into nonNullString.
    if (type.isDefinitelyNotNull() && type.isStringType(dexItemFactory)) {
      if (invoke.hasOutValue()) {
        affectedValues.addAll(invoke.outValue().affectedValues());
        invoke.outValue().replaceUsers(object);
      }
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }
}
