// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.google.common.base.Equivalence.Wrapper;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The class merger is responsible for moving methods from {@link ClassMerger#toMergeGroup} into the
 * class {@link ClassMerger#target}. While performing merging, this class tracks which methods have
 * been moved, as well as which fields have been remapped in the {@link ClassMerger#lensBuilder}.
 */
class ClassMerger {
  private final AppView<?> appView;
  private final DexProgramClass target;
  private final Collection<DexProgramClass> toMergeGroup;
  private final DexItemFactory dexItemFactory;
  private final HorizontalClassMergerGraphLens.Builder lensBuilder;

  private final Map<DexProto, ConstructorMerger.Builder> constructorMergers;

  ClassMerger(
      AppView<?> appView,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      DexProgramClass target,
      Collection<DexProgramClass> toMergeGroup) {
    this.appView = appView;
    this.lensBuilder = lensBuilder;
    this.target = target;
    this.toMergeGroup = toMergeGroup;
    this.constructorMergers = new IdentityHashMap<>();

    this.dexItemFactory = appView.dexItemFactory();
  }

  private Wrapper<DexMethod> bySignature(DexMethod method) {
    return MethodSignatureEquivalence.get().wrap(method);
  }

  void addConstructor(DexEncodedMethod method) {
    assert method.isInstanceInitializer();
    constructorMergers
        .computeIfAbsent(method.proto(), ignore -> new ConstructorMerger.Builder())
        .add(method);
  }

  private void merge(DexProgramClass toMerge) {
    toMerge.forEachProgramMethod(
        programMethod -> {
          DexEncodedMethod method = programMethod.getDefinition();
          assert !method.isClassInitializer();

          if (method.isInstanceInitializer()) {
            addConstructor(method);
          } else {
            // TODO(b/166427795): Ensure that overriding relationships are not changed.
            assert method.isVirtualMethod();

            DexMethod newMethod = renameMethod(programMethod);
            // TODO(b/165000217): Add all methods to `target` in one go using addVirtualMethods().;
            target.addVirtualMethod(method.toTypeSubstitutedMethod(newMethod));
            lensBuilder.moveMethod(method.method, newMethod);
          }
        });

    // Clear the members of the class to be merged since they have now been moved to the target.
    toMerge.setVirtualMethods(null);
    toMerge.setDirectMethods(null);
    toMerge.setInstanceFields(null);
    toMerge.setStaticFields(null);
  }

  /**
   * Find a new name for the method.
   *
   * @param method The class the method originally belonged to.
   */
  DexMethod renameMethod(ProgramMethod method) {
    return dexItemFactory.createFreshMethodName(
        method.getDefinition().method.name.toSourceString(),
        method.getHolderType(),
        method.getDefinition().proto(),
        target.type,
        tryMethod -> target.lookupMethod(tryMethod) == null);
  }

  void mergeConstructors() {
    for (ConstructorMerger.Builder builder : constructorMergers.values()) {
      ConstructorMerger constructorMerger = builder.build(appView, target);
      constructorMerger.merge(lensBuilder);
    }
  }

  /**
   * To ensure constructor merging happens correctly, add all of the target constructors methods to
   * constructor mergers.
   */
  void addTargetConstructors() {
    target.forEachProgramDirectMethod(
        programMethod -> {
          DexEncodedMethod method = programMethod.getDefinition();
          if (method.isInstanceInitializer()) {
            addConstructor(method);
          }
        });
  }

  public void mergeGroup() {
    addTargetConstructors();

    for (DexProgramClass clazz : toMergeGroup) {
      merge(clazz);
    }

    mergeConstructors();
  }
}
