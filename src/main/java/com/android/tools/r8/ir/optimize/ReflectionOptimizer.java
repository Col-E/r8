// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class ReflectionOptimizer {

  // Rewrite getClass() call to const-class if the type of the given instance is effectively final.
  public static void rewriteGetClass(AppView<AppInfoWithLiveness> appView, IRCode code) {
    InstructionListIterator it = code.instructionListIterator();
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    while (it.hasNext()) {
      Instruction current = it.next();
      // Conservatively bail out if the containing block has catch handlers.
      // TODO(b/118509730): unless join of all catch types is ClassNotFoundException ?
      if (current.getBlock().hasCatchHandlers()) {
        continue;
      }
      if (!current.isInvokeVirtual()) {
        continue;
      }
      InvokeVirtual invoke = current.asInvokeVirtual();
      DexMethod invokedMethod = invoke.getInvokedMethod();
      // Class<?> Object#getClass() is final and cannot be overridden.
      if (invokedMethod != dexItemFactory.objectMethods.getClass) {
        continue;
      }
      Value in = invoke.getReceiver();
      if (in.hasLocalInfo()) {
        continue;
      }
      TypeLatticeElement inType = in.getTypeLattice();
      // Check the receiver is either class type or array type. Also make sure it is not nullable.
      if (!(inType.isClassType() || inType.isArrayType())
          || inType.isNullable()) {
        continue;
      }
      DexType type =
          inType.isClassType()
              ? inType.asClassTypeLatticeElement().getClassType()
              : inType.asArrayTypeLatticeElement().getArrayType(dexItemFactory);
      DexType baseType = type.toBaseType(dexItemFactory);
      // Make sure base type is a class type.
      if (!baseType.isClassType()) {
        continue;
      }
      // Only consider program class, e.g., platform can introduce sub types in different versions.
      DexClass clazz = appView.definitionFor(baseType);
      if (clazz == null || !clazz.isProgramClass()) {
        continue;
      }
      // Only consider effectively final class. Exception: new Base().getClass().
      if (!appView.appInfo().hasSubtypes(baseType)
          || !appView.appInfo().isInstantiatedIndirectly(baseType)
          || (!in.isPhi() && in.definition.isCreatingInstanceOrArray())) {
        // Make sure the target (base) type is visible.
        ConstraintWithTarget constraints =
            ConstraintWithTarget.classIsVisible(code.method.method.holder, baseType, appView);
        if (constraints == ConstraintWithTarget.NEVER) {
          continue;
        }
        TypeLatticeElement typeLattice =
            TypeLatticeElement.classClassType(appView, definitelyNotNull());
        Value value = code.createValue(typeLattice, invoke.getLocalInfo());
        ConstClass constClass = new ConstClass(value, type);
        it.replaceCurrentInstruction(constClass);
      }
    }
    assert code.isConsistentSSA();
  }
}
