// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.synthetic.AbstractSynthesizedCode;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class VirtualMethodMerger {
  private final DexProgramClass target;
  private final DexItemFactory dexItemFactory;
  private final Collection<ProgramMethod> methods;
  private final DexField classIdField;
  private final AppView<AppInfoWithLiveness> appView;
  private final Map<DexType, DexType> mergedClasses;

  public VirtualMethodMerger(
      AppView<AppInfoWithLiveness> appView,
      DexProgramClass target,
      Collection<ProgramMethod> methods,
      DexField classIdField,
      Map<DexType, DexType> mergedClasses) {
    this.dexItemFactory = appView.dexItemFactory();
    this.target = target;
    this.classIdField = classIdField;
    this.methods = methods;
    this.appView = appView;
    this.mergedClasses = mergedClasses;
  }

  public static class Builder {
    private final Collection<ProgramMethod> methods = new ArrayList<>();

    public Builder add(ProgramMethod constructor) {
      methods.add(constructor);
      return this;
    }

    public VirtualMethodMerger build(
        AppView<AppInfoWithLiveness> appView,
        DexProgramClass target,
        DexField classIdField,
        Map<DexType, DexType> mergedClasses) {
      return new VirtualMethodMerger(appView, target, methods, classIdField, mergedClasses);
    }
  }

  private DexMethod moveMethod(ProgramMethod oldMethod) {
    DexMethod oldMethodReference = oldMethod.getReference();
    DexMethod method =
        dexItemFactory.createFreshMethodName(
            oldMethodReference.name.toSourceString(),
            oldMethod.getHolderType(),
            oldMethodReference.proto,
            target.type,
            tryMethod -> target.lookupMethod(tryMethod) == null);

    if (oldMethod.getHolderType() == target.type) {
      target.removeMethod(oldMethod.getReference());
    }

    DexEncodedMethod encodedMethod = oldMethod.getDefinition().toTypeSubstitutedMethod(method);
    MethodAccessFlags flags = encodedMethod.accessFlags;
    flags.unsetProtected();
    flags.unsetPublic();
    flags.setPrivate();
    target.addDirectMethod(encodedMethod);

    return encodedMethod.method;
  }

  private MethodAccessFlags getAccessFlags() {
    // TODO(b/164998929): ensure this behaviour is correct, should probably calculate upper bound
    return methods.iterator().next().getDefinition().getAccessFlags();
  }

  private DexMethod superMethod() {
    // TODO(b/167664411): remove all super type remapping by running this before any classes have
    // been mapped.
    DexMethod template = methods.iterator().next().getReference();
    // If the parent was already merged into another class, look for the method on the old class.
    DexType superType = mergedClasses.getOrDefault(target.superType, target.superType);
    SingleResolutionResult resolutionResult =
        appView
            .withLiveness()
            .appInfo()
            .resolveMethodOnClass(template, superType)
            .asSingleResolution();
    if (resolutionResult == null) {
      return null;
    }
    // Make sure that the parent method reference is on the original class if the original class
    // was merged into another class. This ensures that the method reference is correctly fixed.
    DexMethod maybeMovedSuperMethod = resolutionResult.getResolvedMethod().method;
    return dexItemFactory.createMethod(
        target.superType, maybeMovedSuperMethod.proto, maybeMovedSuperMethod.name);
  }

  /**
   * If there is only a single method that does not override anything then it is safe to just move
   * it to the target type if it is not already in it.
   */
  public void mergeTrivial(HorizontalClassMergerGraphLens.Builder lensBuilder) {
    ProgramMethod method = methods.iterator().next();

    if (method.getHolderType() != target.type) {
      // If the method is not in the target type, move it and record it in the lens.
      DexEncodedMethod newMethod =
          method.getDefinition().toRenamedHolderMethod(target.type, dexItemFactory);
      target.addVirtualMethod(newMethod);
      lensBuilder.moveMethod(method.getReference(), newMethod.getReference());
    }
  }

  public void merge(
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder,
      Reference2IntMap classIdentifiers) {

    assert !methods.isEmpty();
    DexMethod superMethod = superMethod();

    // Handle trivial merges.
    if (superMethod == null && methods.size() == 1) {
      mergeTrivial(lensBuilder);
      return;
    }

    Int2ReferenceSortedMap<DexMethod> classIdToMethodMap = new Int2ReferenceAVLTreeMap<>();

    int classFileVersion = -1;
    for (ProgramMethod method : methods) {
      if (method.getDefinition().hasClassFileVersion()) {
        classFileVersion =
            Integer.max(classFileVersion, method.getDefinition().getClassFileVersion());
      }
      DexMethod newMethod = moveMethod(method);
      lensBuilder.recordOriginalSignature(method.getReference(), newMethod);
      classIdToMethodMap.put(classIdentifiers.getInt(method.getHolderType()), newMethod);
    }

    // Use the first of the original methods as the original method for the merged constructor.
    DexMethod originalMethodReference = methods.iterator().next().getReference();

    DexMethod newMethodReference =
        dexItemFactory.createMethod(
            target.type, originalMethodReference.proto, originalMethodReference.name);
    AbstractSynthesizedCode synthesizedCode =
        new VirtualMethodEntryPointSynthesizedCode(
            classIdToMethodMap,
            classIdField,
            superMethod,
            newMethodReference,
            originalMethodReference);
    DexEncodedMethod newMethod =
        new DexEncodedMethod(
            newMethodReference,
            getAccessFlags(),
            DexAnnotationSet.empty(),
            ParameterAnnotationsList.empty(),
            synthesizedCode,
            classFileVersion,
            true);

    // Map each old method to the newly synthesized method in the graph lens.
    for (ProgramMethod oldMethod : methods) {
      lensBuilder.mapMethod(oldMethod.getReference(), newMethodReference);
    }
    lensBuilder.recordExtraOriginalSignature(originalMethodReference, newMethodReference);

    target.addVirtualMethod(newMethod);

    fieldAccessChangesBuilder.fieldReadByMethod(classIdField, newMethod.method);
  }
}
