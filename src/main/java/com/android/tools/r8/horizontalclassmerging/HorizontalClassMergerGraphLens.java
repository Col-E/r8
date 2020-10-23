// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.NestedGraphLens;
import com.android.tools.r8.ir.conversion.ExtraParameter;
import com.android.tools.r8.utils.IterableUtils;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class HorizontalClassMergerGraphLens extends NestedGraphLens {
  private final AppView<?> appView;
  private final Map<DexMethod, List<ExtraParameter>> methodExtraParameters;
  private final Map<DexMethod, DexMethod> originalConstructorSignatures;
  private final HorizontallyMergedClasses mergedClasses;

  private HorizontalClassMergerGraphLens(
      AppView<?> appView,
      HorizontallyMergedClasses mergedClasses,
      Map<DexMethod, List<ExtraParameter>> methodExtraParameters,
      Map<DexField, DexField> fieldMap,
      Map<DexMethod, DexMethod> methodMap,
      BiMap<DexField, DexField> originalFieldSignatures,
      BiMap<DexMethod, DexMethod> originalMethodSignatures,
      Map<DexMethod, DexMethod> originalConstructorSignatures,
      GraphLens previousLens) {
    super(
        mergedClasses.getForwardMap(),
        methodMap,
        fieldMap,
        originalFieldSignatures,
        originalMethodSignatures,
        previousLens,
        appView.dexItemFactory());
    this.appView = appView;
    this.methodExtraParameters = methodExtraParameters;
    this.originalConstructorSignatures = originalConstructorSignatures;
    this.mergedClasses = mergedClasses;
  }

  @Override
  protected Iterable<DexType> internalGetOriginalTypes(DexType previous) {
    return IterableUtils.prependSingleton(previous, mergedClasses.getSourcesFor(previous));
  }

  @Override
  public DexMethod getOriginalMethodSignature(DexMethod method) {
    DexMethod originalConstructor = originalConstructorSignatures.get(method);
    if (originalConstructor == null) {
      return super.getOriginalMethodSignature(method);
    }
    return getPrevious().getOriginalMethodSignature(originalConstructor);
  }

  /**
   * If an overloaded constructor is requested, add the constructor id as a parameter to the
   * constructor. Otherwise return the lookup on the underlying graph lens.
   */
  @Override
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context) {
    List<ExtraParameter> extraParameters = methodExtraParameters.get(previous.getReference());
    MethodLookupResult lookup = super.internalDescribeLookupMethod(previous, context);
    if (extraParameters == null) {
      return lookup;
    }
    return MethodLookupResult.builder(this)
        .setReference(lookup.getReference())
        .setPrototypeChanges(lookup.getPrototypeChanges().withExtraParameters(extraParameters))
        .setType(lookup.getType())
        .build();
  }

  public static class Builder {
    private final BiMap<DexField, DexField> fieldMap = HashBiMap.create();

    private ManyToOneMap<DexMethod, DexMethod> methodMap = new ManyToOneMap<>();

    private final Map<DexMethod, List<ExtraParameter>> methodExtraParameters =
        new IdentityHashMap<>();

    Builder() {}

    public HorizontalClassMergerGraphLens build(
        AppView<?> appView, HorizontallyMergedClasses mergedClasses) {
      ManyToOneInverseMap<DexMethod, DexMethod> inverseMethodMap =
          methodMap.inverse(
              group -> {
                // Every group should have a representative. Fail in debug mode.
                assert false;
                return group.iterator().next();
              });
      BiMap<DexField, DexField> originalFieldSignatures = fieldMap.inverse();

      return new HorizontalClassMergerGraphLens(
          appView,
          mergedClasses,
          methodExtraParameters,
          fieldMap,
          methodMap.getForwardMap(),
          originalFieldSignatures,
          inverseMethodMap.getBiMap(),
          inverseMethodMap.getExtraMap(),
          appView.graphLens());
    }

    public void remapMethods(BiMap<DexMethod, DexMethod> remapMethods) {
      methodMap = methodMap.remap(remapMethods, Function.identity(), Function.identity());
    }

    public Builder mapField(DexField from, DexField to) {
      fieldMap.put(from, to);
      return this;
    }

    /** Unidirectional mapping from one method to another. */
    public Builder recordExtraOriginalSignature(DexMethod from, DexMethod to) {
      methodMap.setRepresentative(from, to);

      return this;
    }

    /** Unidirectional mapping from one method to another. */
    public Builder mapMethod(DexMethod from, DexMethod to) {
      methodMap.put(from, to);

      return this;
    }

    /** Unidirectional mapping from one method to another. */
    public Builder mapMethodInverse(DexMethod from, DexMethod to) {
      methodMap.putInverse(from, to);

      return this;
    }

    public Builder moveMethod(DexMethod from, DexMethod to) {
      mapMethod(from, to);
      mapMethodInverse(from, to);
      return this;
    }

    /**
     * One way mapping from one constructor to another. This is used for synthesized constructors,
     * where many constructors are merged into a single constructor. The synthesized constructor
     * therefore does not have a unique reverse constructor.
     */
    public Builder moveMergedConstructor(
        DexMethod from, DexMethod to, List<ExtraParameter> extraParameters) {
      moveMethod(from, to);
      methodExtraParameters.put(from, extraParameters);
      return this;
    }

    public Builder addExtraParameters(DexMethod to, List<ExtraParameter> extraParameters) {
      Set<DexMethod> mapsFrom = methodMap.lookupReverse(to);
      if (mapsFrom == null) {
        mapsFrom = Collections.singleton(to);
      }
      mapsFrom.forEach(
          originalFrom ->
              methodExtraParameters
                  .computeIfAbsent(originalFrom, ignore -> new ArrayList<>(extraParameters.size()))
                  .addAll(extraParameters));
      return this;
    }
  }
}
