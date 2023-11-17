// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library.primitive;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleBoxedPrimitiveValue;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.library.StatelessLibraryMethodModelCollection;
import java.util.Set;
import java.util.function.Consumer;

public abstract class PrimitiveMethodOptimizer extends StatelessLibraryMethodModelCollection {

  final AppView<?> appView;
  final DexItemFactory dexItemFactory;

  PrimitiveMethodOptimizer(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
  }

  public static void forEachPrimitiveOptimizer(
      AppView<?> appView, Consumer<PrimitiveMethodOptimizer> register) {
    register.accept(new BooleanMethodOptimizer(appView));
    register.accept(new ByteMethodOptimizer(appView));
    register.accept(new CharacterMethodOptimizer(appView));
    register.accept(new DoubleMethodOptimizer(appView));
    register.accept(new FloatMethodOptimizer(appView));
    register.accept(new IntegerMethodOptimizer(appView));
    register.accept(new LongMethodOptimizer(appView));
    register.accept(new ShortMethodOptimizer(appView));
  }

  abstract DexMethod getBoxMethod();

  abstract DexMethod getUnboxMethod();

  abstract boolean isMatchingSingleBoxedPrimitive(AbstractValue abstractValue);

  @Override
  public void optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      Set<Value> affectedValues,
      Set<BasicBlock> blocksToRemove) {
    optimizeBoxingMethods(code, instructionIterator, invoke, singleTarget, affectedValues);
  }

  void optimizeBoxingMethods(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      Set<Value> affectedValues) {
    if (singleTarget.getReference().isIdenticalTo(getUnboxMethod())) {
      optimizeUnboxMethod(code, instructionIterator, invoke);
    } else if (singleTarget.getReference().isIdenticalTo(getBoxMethod())) {
      optimizeBoxMethod(code, instructionIterator, invoke, affectedValues);
    }
  }

  void optimizeBoxMethod(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod boxInvoke,
      Set<Value> affectedValues) {
    Value firstArg = boxInvoke.getFirstArgument();
    if (firstArg
        .getAliasedValue()
        .isDefinedByInstructionSatisfying(i -> i.isInvokeMethod(getUnboxMethod()))) {
      // Optimize Primitive.box(boxed.unbox()) into boxed.
      InvokeMethod unboxInvoke = firstArg.getAliasedValue().getDefinition().asInvokeMethod();
      assert unboxInvoke.isInvokeVirtual();
      Value src = boxInvoke.outValue();
      Value replacement = unboxInvoke.getFirstArgument();
      // We need to update affected values if the nullability is different.
      src.replaceUsers(replacement, affectedValues);
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }

  void optimizeUnboxMethod(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod unboxInvoke) {
    Value firstArg = unboxInvoke.getFirstArgument();
    AbstractValue abstractValue = firstArg.getAbstractValue(appView, code.context());
    if (isMatchingSingleBoxedPrimitive(abstractValue)) {
      // Optimize Primitive.box(cst).unbox() into cst, possibly inter-procedurally.
      if (unboxInvoke.hasOutValue()) {
        SingleBoxedPrimitiveValue singleBoxedNumber = abstractValue.asSingleBoxedPrimitive();
        instructionIterator.replaceCurrentInstruction(
            singleBoxedNumber
                .toPrimitive(appView.abstractValueFactory())
                .createMaterializingInstruction(appView, code, unboxInvoke));
      } else {
        instructionIterator.removeOrReplaceByDebugLocalRead();
      }
      return;
    }
    if (firstArg
        .getAliasedValue()
        .isDefinedByInstructionSatisfying(i -> i.isInvokeMethod(getBoxMethod()))) {
      // Optimize Primitive.box(unboxed).unbox() into unboxed.
      InvokeMethod boxInvoke = firstArg.getAliasedValue().getDefinition().asInvokeMethod();
      assert boxInvoke.isInvokeStatic();
      unboxInvoke.outValue().replaceUsers(boxInvoke.getFirstArgument());
      instructionIterator.replaceCurrentInstructionByNullCheckIfPossible(appView, code.context());
    }
  }
}
