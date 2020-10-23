// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.synthetic.AbstractSynthesizedCode;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.ArrayList;
import java.util.Collection;

public class VirtualMethodMerger {
  private final DexProgramClass target;
  private final DexItemFactory dexItemFactory;
  private final Collection<ProgramMethod> methods;
  private final DexField classIdField;
  private final AppView<AppInfoWithLiveness> appView;
  private final DexMethod superMethod;

  public VirtualMethodMerger(
      AppView<AppInfoWithLiveness> appView,
      DexProgramClass target,
      Collection<ProgramMethod> methods,
      DexField classIdField,
      DexMethod superMethod) {
    this.dexItemFactory = appView.dexItemFactory();
    this.target = target;
    this.classIdField = classIdField;
    this.methods = methods;
    this.appView = appView;
    this.superMethod = superMethod;
  }

  public static class Builder {
    private final Collection<ProgramMethod> methods = new ArrayList<>();

    public Builder add(ProgramMethod constructor) {
      methods.add(constructor);
      return this;
    }

    /** Get the super method handle if this method overrides a parent method. */
    private DexMethod superMethod(AppView<AppInfoWithLiveness> appView, DexProgramClass target) {
      // TODO(b/167981556): Correctly detect super methods defined on interfaces.
      DexMethod template = methods.iterator().next().getReference();
      SingleResolutionResult resolutionResult =
          appView
              .withLiveness()
              .appInfo()
              .resolveMethodOnClass(template, target.getSuperType())
              .asSingleResolution();
      if (resolutionResult == null) {
        return null;
      }
      if (resolutionResult.getResolvedMethod().isAbstract()) {
        return null;
      }
      if (resolutionResult.getResolvedHolder().isInterface()) {
        // Ensure that invoke virtual isn't called on an interface method.
        return resolutionResult
            .getResolvedMethod()
            .getReference()
            .withHolder(target.getSuperType(), appView.dexItemFactory());
      }
      return resolutionResult.getResolvedMethod().getReference();
    }

    public VirtualMethodMerger build(
        AppView<AppInfoWithLiveness> appView, DexProgramClass target, DexField classIdField) {
      DexMethod superMethod = superMethod(appView, target);
      return new VirtualMethodMerger(appView, target, methods, classIdField, superMethod);
    }
  }

  public int getArity() {
    return methods.iterator().next().getReference().getArity();
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
    MethodAccessFlags flags = methods.iterator().next().getDefinition().getAccessFlags().copy();
    if (flags.isFinal() && Iterables.any(methods, method -> !method.getAccessFlags().isFinal())) {
      flags.unsetFinal();
    }
    return flags;
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

    // Handle trivial merges.
    if (superMethod == null && methods.size() == 1) {
      mergeTrivial(lensBuilder);
      return;
    }

    Int2ReferenceSortedMap<DexMethod> classIdToMethodMap = new Int2ReferenceAVLTreeMap<>();

    CfVersion classFileVersion = null;
    for (ProgramMethod method : methods) {
      if (method.getDefinition().hasClassFileVersion()) {
        CfVersion methodVersion = method.getDefinition().getClassFileVersion();
        classFileVersion = CfVersion.maxAllowNull(classFileVersion, methodVersion);
      }
      DexMethod newMethod = moveMethod(method);
      lensBuilder.mapMethod(newMethod, newMethod);
      lensBuilder.mapMethodInverse(method.getReference(), newMethod);
      classIdToMethodMap.put(classIdentifiers.getInt(method.getHolderType()), newMethod);
    }

    // Use the first of the original methods as the original method for the merged constructor.
    DexMethod templateReference = methods.iterator().next().getReference();
    DexMethod originalMethodReference =
        appView.graphLens().getOriginalMethodSignature(templateReference);
    DexMethod bridgeMethodReference =
        dexItemFactory.createFreshMethodName(
            originalMethodReference.getName().toSourceString() + "$bridge",
            null,
            originalMethodReference.proto,
            originalMethodReference.getHolderType(),
            tryMethod -> target.lookupMethod(tryMethod) == null);

    DexMethod newMethodReference =
        dexItemFactory.createMethod(target.type, templateReference.proto, templateReference.name);
    AbstractSynthesizedCode synthesizedCode =
        new VirtualMethodEntryPointSynthesizedCode(
            classIdToMethodMap,
            classIdField,
            superMethod,
            newMethodReference,
            bridgeMethodReference);
    DexEncodedMethod newMethod =
        new DexEncodedMethod(
            newMethodReference,
            getAccessFlags(),
            MethodTypeSignature.noSignature(),
            DexAnnotationSet.empty(),
            ParameterAnnotationsList.empty(),
            synthesizedCode,
            true,
            classFileVersion);

    // Map each old method to the newly synthesized method in the graph lens.
    for (ProgramMethod oldMethod : methods) {
      lensBuilder.moveMethod(oldMethod.getReference(), newMethodReference);
    }
    lensBuilder.recordExtraOriginalSignature(bridgeMethodReference, newMethodReference);

    target.addVirtualMethod(newMethod);

    fieldAccessChangesBuilder.fieldReadByMethod(classIdField, newMethod.method);
  }
}
