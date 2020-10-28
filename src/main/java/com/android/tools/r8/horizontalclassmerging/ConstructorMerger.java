// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.ir.conversion.ExtraConstantIntParameter;
import com.android.tools.r8.ir.conversion.ExtraParameter;
import com.android.tools.r8.ir.conversion.ExtraUnusedNullParameter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.utils.ListUtils;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ConstructorMerger {
  private final AppView<?> appView;
  private final DexProgramClass target;
  private final Collection<DexEncodedMethod> constructors;
  private final DexItemFactory dexItemFactory;
  private final DexField classIdField;

  ConstructorMerger(
      AppView<?> appView,
      DexProgramClass target,
      Collection<DexEncodedMethod> constructors,
      DexField classIdField) {
    this.appView = appView;
    this.target = target;
    this.constructors = constructors;
    this.classIdField = classIdField;

    // Constructors should not be empty and all constructors should have the same prototype.
    assert !constructors.isEmpty();
    assert constructors.stream().map(constructor -> constructor.proto()).distinct().count() == 1;

    this.dexItemFactory = appView.dexItemFactory();
  }

  /**
   * The method reference template describes which arguments the constructor must have, and is used
   * to generate the final reference by appending null arguments until it is fresh.
   */
  private DexMethod generateReferenceMethodTemplate() {
    DexMethod methodTemplate = constructors.iterator().next().getReference();
    if (!isTrivialMerge()) {
      methodTemplate = dexItemFactory.appendTypeToMethod(methodTemplate, dexItemFactory.intType);
    }
    return methodTemplate;
  }

  public int getArity() {
    return constructors.iterator().next().getReference().getArity();
  }

  public static class Builder {
    private int estimatedDexCodeSize;
    private final List<List<DexEncodedMethod>> constructorGroups = new ArrayList<>();
    private AppView<AppInfoWithLiveness> appView;

    public Builder(AppView<AppInfoWithLiveness> appView) {
      this.appView = appView;

      createNewGroup();
    }

    private void createNewGroup() {
      estimatedDexCodeSize = 0;
      constructorGroups.add(new ArrayList<>());
    }

    public Builder add(DexEncodedMethod constructor) {
      int estimatedMaxSizeInBytes = constructor.getCode().estimatedDexCodeSizeUpperBoundInBytes();
      // If the constructor gets too large, then the constructor should be merged into a new group.
      if (estimatedDexCodeSize + estimatedMaxSizeInBytes
              > appView.options().minimumVerificationSizeLimitInBytes() / 2
          && estimatedDexCodeSize > 0) {
        createNewGroup();
      }

      ListUtils.last(constructorGroups).add(constructor);
      estimatedDexCodeSize += estimatedMaxSizeInBytes;
      return this;
    }

    public List<ConstructorMerger> build(
        AppView<?> appView, DexProgramClass target, DexField classIdField) {
      assert constructorGroups.stream().noneMatch(List::isEmpty);
      return ListUtils.map(
          constructorGroups,
          constructors -> new ConstructorMerger(appView, target, constructors, classIdField));
    }
  }

  private boolean isTrivialMerge() {
    return constructors.size() == 1;
  }

  private DexMethod moveConstructor(DexEncodedMethod constructor) {
    DexMethod method =
        dexItemFactory.createFreshMethodName(
            "constructor",
            constructor.holder(),
            constructor.proto(),
            target.type,
            tryMethod -> target.lookupMethod(tryMethod) == null);

    if (constructor.holder() == target.type) {
      target.removeMethod(constructor.getReference());
    }

    DexEncodedMethod encodedMethod = constructor.toTypeSubstitutedMethod(method);
    encodedMethod.getMutableOptimizationInfo().markForceInline();
    encodedMethod.accessFlags.unsetConstructor();
    encodedMethod.accessFlags.unsetPublic();
    encodedMethod.accessFlags.unsetProtected();
    encodedMethod.accessFlags.setPrivate();
    target.addDirectMethod(encodedMethod);
    return method;
  }

  private MethodAccessFlags getAccessFlags() {
    // TODO(b/164998929): ensure this behaviour is correct, should probably calculate upper bound
    return MethodAccessFlags.fromSharedAccessFlags(
        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, true);
  }

  /** Synthesize a new method which selects the constructor based on a parameter type. */
  void merge(
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder,
      Reference2IntMap<DexType> classIdentifiers,
      SyntheticArgumentClass syntheticArgumentClass) {
    // Tree map as must be sorted.
    Int2ReferenceSortedMap<DexMethod> typeConstructorClassMap = new Int2ReferenceAVLTreeMap<>();

    CfVersion classFileVersion = null;
    for (DexEncodedMethod constructor : constructors) {
      if (constructor.hasClassFileVersion()) {
        classFileVersion =
            CfVersion.maxAllowNull(classFileVersion, constructor.getClassFileVersion());
      }
      DexMethod movedConstructor = moveConstructor(constructor);
      lensBuilder.mapMethod(movedConstructor, movedConstructor);
      lensBuilder.mapMethodInverse(constructor.method, movedConstructor);
      typeConstructorClassMap.put(
          classIdentifiers.getInt(constructor.getHolderType()), movedConstructor);
    }

    DexMethod methodReferenceTemplate = generateReferenceMethodTemplate();
    DexMethod newConstructorReference =
        dexItemFactory.createInstanceInitializerWithFreshProto(
            methodReferenceTemplate,
            syntheticArgumentClass.getArgumentClass(),
            tryMethod -> target.lookupMethod(tryMethod) == null);
    int extraNulls = newConstructorReference.getArity() - methodReferenceTemplate.getArity();

    DexMethod representativeConstructorReference = constructors.iterator().next().method;
    ConstructorEntryPointSynthesizedCode synthesizedCode =
        new ConstructorEntryPointSynthesizedCode(
            typeConstructorClassMap,
            newConstructorReference,
            classIdField,
            appView.graphLens().getOriginalMethodSignature(representativeConstructorReference));
    DexEncodedMethod newConstructor =
        new DexEncodedMethod(
            newConstructorReference,
            getAccessFlags(),
            MethodTypeSignature.noSignature(),
            DexAnnotationSet.empty(),
            ParameterAnnotationsList.empty(),
            synthesizedCode,
            true,
            classFileVersion);

    if (isTrivialMerge()) {
      // The constructor does not require the additional argument, just map it like a regular
      // method.
      DexEncodedMethod oldConstructor = constructors.iterator().next();
      if (extraNulls > 0) {
        List<ExtraParameter> extraParameters = new LinkedList<>();
        extraParameters.addAll(Collections.nCopies(extraNulls, new ExtraUnusedNullParameter()));
        lensBuilder.moveMergedConstructor(
            oldConstructor.method, newConstructorReference, extraParameters);
      } else {
        lensBuilder.moveMethod(oldConstructor.method, newConstructorReference);
      }
    } else {
      // Map each old constructor to the newly synthesized constructor in the graph lens.
      for (DexEncodedMethod oldConstructor : constructors) {
        int classIdentifier = classIdentifiers.getInt(oldConstructor.getHolderType());

        List<ExtraParameter> extraParameters = new LinkedList<>();
        extraParameters.add(new ExtraConstantIntParameter(classIdentifier));
        extraParameters.addAll(Collections.nCopies(extraNulls, new ExtraUnusedNullParameter()));

        lensBuilder.moveMergedConstructor(
            oldConstructor.method, newConstructorReference, extraParameters);
      }
    }
    // Map the first constructor to the newly synthesized constructor.
    lensBuilder.recordExtraOriginalSignature(
        representativeConstructorReference, newConstructorReference);

    target.addDirectMethod(newConstructor);

    fieldAccessChangesBuilder.fieldWrittenByMethod(classIdField, newConstructorReference);
  }
}
