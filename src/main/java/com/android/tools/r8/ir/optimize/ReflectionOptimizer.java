// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InitClass;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.Set;
import java.util.function.BiConsumer;

public class ReflectionOptimizer {

  // Rewrite getClass() to const-class if the type of the given instance is effectively final.
  // Rewrite forName() to const-class if the type is resolvable, accessible and already initialized.
  public static void rewriteGetClassOrForNameToConstClass(
      AppView<AppInfoWithLiveness> appView, IRCode code) {
    if (!appView.appInfo().canUseConstClassInstructions(appView.options())) {
      return;
    }
    AffectedValues affectedValues = new AffectedValues();
    ProgramMethod context = code.context();
    BasicBlockIterator blockIterator = code.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      InstructionListIterator it = block.listIterator(code);
      while (it.hasNext()) {
        InvokeMethod invoke = it.nextUntil(x -> x.isInvokeStatic() || x.isInvokeVirtual());
        if (invoke == null) {
          continue;
        }

        if (invoke.isInvokeStatic()) {
          applyTypeForClassForNameTo(
              appView,
              context,
              invoke.asInvokeStatic(),
              rewriteSingleGetClassOrForNameToConstClass(
                  appView, code, blockIterator, it, invoke, affectedValues));
        } else {
          applyTypeForGetClassTo(
              appView,
              context,
              invoke.asInvokeVirtual(),
              rewriteSingleGetClassOrForNameToConstClass(
                  appView, code, blockIterator, it, invoke, affectedValues));
        }
      }
    }
    // Newly introduced const-class is not null, and thus propagate that information.
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    code.removeRedundantBlocks();
    assert code.isConsistentSSA(appView);
  }

  private static BiConsumer<DexType, DexClass> rewriteSingleGetClassOrForNameToConstClass(
      AppView<AppInfoWithLiveness> appView,
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      Set<Value> affectedValues) {
    return (type, baseClass) -> {
      InitClass initClass = null;
      if (invoke.getInvokedMethod().match(appView.dexItemFactory().classMethods.forName)) {
        // Bail-out if the optimization could increase the size of the main dex.
        if (baseClass.isProgramClass()
            && !appView
                .appInfo()
                .getMainDexInfo()
                .canRebindReference(
                    code.context(), baseClass.getType(), appView.getSyntheticItems())) {
          return;
        }

        // We need to initialize the type if it may have observable side effects.
        if (type.isClassType()
            && baseClass.classInitializationMayHaveSideEffectsInContext(appView, code.context())) {
          if (!baseClass.isProgramClass() || !appView.canUseInitClass()) {
            // No way to trigger the class initialization of the given class without
            // Class.forName(), so skip.
            return;
          }

          initClass =
              InitClass.builder()
                  .setFreshOutValue(code, TypeElement.getInt())
                  .setType(type)
                  .setPosition(invoke)
                  .build();
        }
      }

      // If there are no users of the const-class then simply remove the instruction.
      if (!invoke.hasOutValue() || !invoke.outValue().hasAnyUsers()) {
        if (initClass != null) {
          instructionIterator.replaceCurrentInstruction(initClass);
        } else {
          instructionIterator.removeOrReplaceByDebugLocalRead();
        }
        return;
      }

      // Otherwise insert a const-class instruction.
      BasicBlock block = invoke.getBlock();
      affectedValues.addAll(invoke.outValue().affectedValues());
      instructionIterator.replaceCurrentInstructionWithConstClass(
          appView, code, type, invoke.getLocalInfo());

      if (initClass != null) {
        if (block.hasCatchHandlers()) {
          instructionIterator
              .splitCopyCatchHandlers(code, blockIterator, appView.options())
              .listIterator(code)
              .add(initClass);
        } else {
          instructionIterator.add(initClass);
        }
      }

      if (appView.options().isGeneratingClassFiles()) {
        code.method()
            .upgradeClassFileVersion(
                appView.options().requiredCfVersionForConstClassInstructions());
      }
    };
  }

  @SuppressWarnings("ReferenceEquality")
  private static void applyTypeForGetClassTo(
      AppView<AppInfoWithLiveness> appView,
      ProgramMethod context,
      InvokeVirtual invoke,
      BiConsumer<DexType, ? super DexClass> consumer) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexMethod invokedMethod = invoke.getInvokedMethod();
    // Class<?> Object#getClass() is final and cannot be overridden.
    if (invokedMethod != dexItemFactory.objectMembers.getClass) {
      return;
    }
    Value in = invoke.getReceiver();
    if (in.hasLocalInfo()) {
      return;
    }
    TypeElement inType = in.getType();
    // Check the receiver is either class type or array type. Also make sure it is not
    // nullable.
    if (!(inType.isClassType() || inType.isArrayType())
        || inType.isNullable()) {
      return;
    }
    DexType type =
        inType.isClassType()
            ? inType.asClassType().getClassType()
            : inType.asArrayType().toDexType(dexItemFactory);
    DexType baseType = type.toBaseType(dexItemFactory);
    // Make sure base type is a class type.
    if (!baseType.isClassType()) {
      return;
    }
    // Only consider program class, e.g., platform can introduce subtypes in different
    // versions.
    DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(baseType));
    if (clazz == null) {
      return;
    }
    // Only consider effectively final class. Exception: new Base().getClass().
    if (!clazz.isEffectivelyFinal(appView)
        && (in.isPhi() || !in.definition.isCreatingInstanceOrArray())) {
      return;
    }
    // Make sure the target (base) type is visible.
    ConstraintWithTarget constraints =
        ConstraintWithTarget.classIsVisible(context, baseType, appView);
    if (constraints == ConstraintWithTarget.NEVER) {
      return;
    }

    consumer.accept(type, clazz);
  }

  @SuppressWarnings("ReferenceEquality")
  private static void applyTypeForClassForNameTo(
      AppView<AppInfoWithLiveness> appView,
      ProgramMethod context,
      InvokeStatic invoke,
      BiConsumer<DexType, ? super DexClass> consumer) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexMethod invokedMethod = invoke.getInvokedMethod();
    // Class<?> Class#forName(String) is final and cannot be overridden.
    if (invokedMethod != dexItemFactory.classMethods.forName) {
      return;
    }
    assert invoke.arguments().size() == 1;
    Value in = invoke.getArgument(0).getAliasedValue();
    // Only consider const-string input without locals.
    if (in.hasLocalInfo() || in.isPhi()) {
      return;
    }
    // Also, check if the result of forName() is updatable via locals.
    if (invoke.hasOutValue() && invoke.outValue().hasLocalInfo()) {
      return;
    }
    DexType type = null;
    if (in.definition.isDexItemBasedConstString()) {
      if (in.definition.asDexItemBasedConstString().getItem().isDexType()) {
        type = in.definition.asDexItemBasedConstString().getItem().asDexType();
      }
    } else if (in.definition.isConstString()) {
      String name = in.definition.asConstString().getValue().toString();
      // Convert the name into descriptor if the given name is a valid java type.
      String descriptor = DescriptorUtils.javaTypeToDescriptorIfValidJavaType(name);
      // Otherwise, it may be an array's fully qualified name from Class<?>#getName().
      if (descriptor == null && name.startsWith("[") && name.endsWith(";")) {
        // E.g., [Lx.y.Z; -> [Lx/y/Z;
        descriptor = name.replace(
            DescriptorUtils.JAVA_PACKAGE_SEPARATOR,
            DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR);
      }
      if (descriptor == null
          || descriptor.indexOf(DescriptorUtils.JAVA_PACKAGE_SEPARATOR) > 0) {
        return;
      }
      type = dexItemFactory.createType(descriptor);
      // Check if the given name refers to a reference type.
      if (!type.isReferenceType()) {
        return;
      }
    } else {
      // Bail out for non-deterministic input to Class<?>#forName(name).
      return;
    }
    if (type == null) {
      return;
    }
    // Make sure the (base) type is resolvable.
    DexType baseType = type.toBaseType(dexItemFactory);
    DexClass baseClass = appView.appInfo().definitionForWithoutExistenceAssert(baseType);
    if (baseClass == null || !baseClass.isResolvable(appView)) {
      return;
    }

    // Make sure the (base) type is visible.
    ClassToFeatureSplitMap classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
    if (AccessControl.isClassAccessible(baseClass, context, appView).isPossiblyFalse()) {
      return;
    }

    // If the type is guaranteed to be visible, it must be in the same feature as the current method
    // or in the base.
    assert !baseClass.isProgramClass()
        || classToFeatureSplitMap.isInBaseOrSameFeatureAs(
            baseClass.asProgramClass(), context, appView);

    consumer.accept(type, baseClass);
  }
}
