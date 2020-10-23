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
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.ir.conversion.ExtraConstantIntParameter;
import com.android.tools.r8.ir.conversion.ExtraParameter;
import com.android.tools.r8.ir.conversion.ExtraUnusedNullParameter;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
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

  public int getArity() {
    return constructors.iterator().next().getReference().getArity();
  }

  public static class Builder {
    private final Collection<DexEncodedMethod> constructors;

    public Builder() {
      constructors = new ArrayList<>();
    }

    public Builder add(DexEncodedMethod constructor) {
      constructors.add(constructor);
      return this;
    }

    public ConstructorMerger build(
        AppView<?> appView, DexProgramClass target, DexField classIdField) {
      return new ConstructorMerger(appView, target, constructors, classIdField);
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
      target.removeMethod(constructor.toReference());
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

  private DexProto getNewConstructorProto(SyntheticArgumentClass syntheticArgumentClass) {
    DexEncodedMethod firstConstructor = constructors.iterator().next();
    DexProto oldProto = firstConstructor.getProto();

    if (isTrivialMerge() && syntheticArgumentClass == null) {
      return oldProto;
    }

    List<DexType> parameters = new ArrayList<>();
    Collections.addAll(parameters, oldProto.parameters.values);
    if (!isTrivialMerge()) {
      parameters.add(dexItemFactory.intType);
    }
    if (syntheticArgumentClass != null) {
      parameters.add(syntheticArgumentClass.getArgumentClass());
    }
    // TODO(b/165783587): add synthesised class to prevent constructor merge conflict
    return dexItemFactory.createProto(oldProto.returnType, parameters);
  }

  private DexMethod getNewConstructorReference(SyntheticArgumentClass syntheticArgumentClass) {
    DexProto proto = getNewConstructorProto(syntheticArgumentClass);
    return appView.dexItemFactory().createMethod(target.type, proto, dexItemFactory.initMethodName);
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

    DexMethod newConstructorReference = getNewConstructorReference(null);
    boolean addExtraNull = target.lookupMethod(newConstructorReference) != null;
    if (addExtraNull) {
      newConstructorReference = getNewConstructorReference(syntheticArgumentClass);
      assert target.lookupMethod(newConstructorReference) == null;
    }

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
      if (addExtraNull) {
        List<ExtraParameter> extraParameters = new LinkedList<>();
        extraParameters.add(new ExtraUnusedNullParameter());
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
        if (addExtraNull) {
          extraParameters.add(new ExtraUnusedNullParameter());
        }

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
