// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.TopDownClassHierarchyTraversal;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.optimize.AffectedValues;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ResourcesMemberOptimizer extends StatelessLibraryMethodModelCollection {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;
  private Optional<Boolean> allowStringInlining = Optional.empty();

  ResourcesMemberOptimizer(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
  }

  @SuppressWarnings("ReferenceEquality")
  private synchronized boolean allowInliningOfGetStringCalls() {
    if (allowStringInlining.isPresent()) {
      return allowStringInlining.get();
    }
    // TODO(b/312406163): Allow androidx classes that overwrite this, but don't change the value
    // or have side effects.
    allowStringInlining = Optional.of(true);
    Map<DexClass, Boolean> cachedResults = new IdentityHashMap<>();
    TopDownClassHierarchyTraversal.forProgramClasses(appView.withClassHierarchy())
        .visit(
            appView.appInfo().classes(),
            clazz -> {
              if (isResourcesSubtype(cachedResults, clazz)) {
                DexEncodedMethod dexEncodedMethod =
                    clazz.lookupMethod(
                        dexItemFactory.androidResourcesGetStringProto,
                        dexItemFactory.androidResourcesGetStringName);
                if (dexEncodedMethod != null) {
                  // TODO(b/312695444): Break out of traversal when supported.
                  allowStringInlining = Optional.of(false);
                }
              }
            });
    return allowStringInlining.get();
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isResourcesSubtype(Map<DexClass, Boolean> cachedLookups, DexClass dexClass) {
    Boolean cachedValue = cachedLookups.get(dexClass);
    if (cachedValue != null) {
      return cachedValue;
    }
    if (dexClass.type == dexItemFactory.androidResourcesType) {
      return true;
    }
    if (dexClass.type == dexItemFactory.objectType) {
      return false;
    }

    if (dexClass.superType != null) {
      DexClass superClass = appView.definitionFor(dexClass.superType);
      if (superClass != null) {
        boolean superIsResourcesSubtype = isResourcesSubtype(cachedLookups, superClass);
        cachedLookups.put(dexClass, superIsResourcesSubtype);
        return superIsResourcesSubtype;
      }
    }
    cachedLookups.put(dexClass, false);
    return false;
  }

  @Override
  public DexType getType() {
    return dexItemFactory.androidResourcesType;
  }

  @Override
  public InstructionListIterator optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      AffectedValues affectedValues,
      Set<BasicBlock> blocksToRemove) {
    if (allowInliningOfGetStringCalls()
        && singleTarget
            .getReference()
            .isIdenticalTo(dexItemFactory.androidResourcesGetStringMethod)) {
      maybeInlineGetString(code, instructionIterator, invoke, affectedValues);
    }
    return instructionIterator;
  }

  @SuppressWarnings("ReferenceEquality")
  private void maybeInlineGetString(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    if (invoke.isInvokeVirtual()) {
      InvokeVirtual invokeVirtual = invoke.asInvokeVirtual();
      DexMethod invokedMethod = invokeVirtual.getInvokedMethod();
      assert invokedMethod.isIdenticalTo(dexItemFactory.androidResourcesGetStringMethod);
      assert invoke.inValues().size() == 2;
      Instruction valueDefinition = invoke.getLastArgument().definition;
      if (valueDefinition != null && valueDefinition.isStaticGet()) {
        DexField field = valueDefinition.asStaticGet().getField();
        FieldResolutionResult fieldResolutionResult =
            appView.appInfo().resolveField(field, code.context());
        ProgramField resolvedField = fieldResolutionResult.getProgramField();
        if (resolvedField != null) {
          String singleStringValueForField =
              appView.getResourceAnalysisResult().getSingleStringValueForField(resolvedField);
          if (singleStringValueForField != null) {
            DexString value = dexItemFactory.createString(singleStringValueForField);
            instructionIterator.replaceCurrentInstructionWithConstString(
                appView, code, value, affectedValues);
          }
        }
      }
    }
  }
}
