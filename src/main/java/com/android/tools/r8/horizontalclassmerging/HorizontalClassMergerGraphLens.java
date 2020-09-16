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
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HorizontalClassMergerGraphLens extends NestedGraphLens {
  private final AppView<?> appView;
  private final Map<DexMethod, List<ExtraParameter>> methodExtraParameters;
  private final Map<DexMethod, DexMethod> originalConstructorSignatures;

  private HorizontalClassMergerGraphLens(
      AppView<?> appView,
      Map<DexMethod, List<ExtraParameter>> methodExtraParameters,
      Map<DexType, DexType> typeMap,
      Map<DexField, DexField> fieldMap,
      Map<DexMethod, DexMethod> methodMap,
      BiMap<DexField, DexField> originalFieldSignatures,
      BiMap<DexMethod, DexMethod> originalMethodSignatures,
      Map<DexMethod, DexMethod> originalConstructorSignatures,
      GraphLens previousLens) {
    super(
        typeMap,
        methodMap,
        fieldMap,
        originalFieldSignatures,
        originalMethodSignatures,
        previousLens,
        appView.dexItemFactory());
    this.appView = appView;
    this.methodExtraParameters = methodExtraParameters;
    this.originalConstructorSignatures = originalConstructorSignatures;
  }

  @Override
  public DexMethod getOriginalMethodSignature(DexMethod method) {
    DexMethod originalConstructor = originalConstructorSignatures.get(method);
    if (originalConstructor == null) {
      return super.getOriginalMethodSignature(method);
    }
    return getPrevious().getOriginalMethodSignature(originalConstructor);
  }

  public HorizontallyMergedClasses getHorizontallyMergedClasses() {
    return new HorizontallyMergedClasses(this.typeMap);
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
    private final Map<DexType, DexType> typeMap = new IdentityHashMap<>();
    private final BiMap<DexField, DexField> fieldMap = HashBiMap.create();
    private final Map<DexMethod, DexMethod> methodMap = new IdentityHashMap<>();
    private final Map<DexMethod, Set<DexMethod>> completeInverseMethodMap = new IdentityHashMap<>();

    private final BiMap<DexMethod, DexMethod> originalMethodSignatures = HashBiMap.create();
    private final Map<DexMethod, DexMethod> extraOriginalMethodSignatures = new IdentityHashMap<>();

    private final Map<DexMethod, List<ExtraParameter>> methodExtraParameters =
        new IdentityHashMap<>();

    Builder() {}

    public HorizontalClassMergerGraphLens build(AppView<?> appView) {
      assert !typeMap.isEmpty();

      BiMap<DexField, DexField> originalFieldSignatures = fieldMap.inverse();
      return new HorizontalClassMergerGraphLens(
          appView,
          methodExtraParameters,
          typeMap,
          fieldMap,
          methodMap,
          originalFieldSignatures,
          originalMethodSignatures,
          extraOriginalMethodSignatures,
          appView.graphLens());
    }

    public DexType lookupType(DexType type) {
      return typeMap.getOrDefault(type, type);
    }

    public Builder mapType(DexType from, DexType to) {
      typeMap.put(from, to);
      return this;
    }

    /** Bidirectional mapping from one method to another. */
    public Builder moveMethod(DexMethod from, DexMethod to) {
      if (from == to) {
        return this;
      }

      mapMethod(from, to);
      recordOriginalSignature(from, to);
      return this;
    }

    public Builder recordOriginalSignature(DexMethod from, DexMethod to) {
      if (from == to) {
        return this;
      }

      originalMethodSignatures.forcePut(to, originalMethodSignatures.getOrDefault(from, from));
      return this;
    }

    /** Unidirectional mapping from one method to another. */
    public Builder recordExtraOriginalSignature(DexMethod from, DexMethod to) {
      if (from == to) {
        return this;
      }

      extraOriginalMethodSignatures.put(to, extraOriginalMethodSignatures.getOrDefault(from, from));
      return this;
    }

    /** Unidirectional mapping from one method to another. */
    public Builder mapMethod(DexMethod from, DexMethod to) {
      if (from == to) {
        return this;
      }

      for (DexMethod existingFrom :
          completeInverseMethodMap.getOrDefault(from, Collections.emptySet())) {
        methodMap.put(existingFrom, to);

        // We currently assume that a single method can only be remapped twice.
        assert completeInverseMethodMap
            .getOrDefault(existingFrom, Collections.emptySet())
            .isEmpty();
      }

      methodMap.put(from, to);
      completeInverseMethodMap.computeIfAbsent(to, ignore -> new HashSet<>()).add(from);

      return this;
    }

    public boolean hasExtraSignatureMappingFor(DexMethod method) {
      return extraOriginalMethodSignatures.containsKey(method);
    }

    public boolean hasOriginalSignatureMappingFor(DexMethod method) {
      return originalMethodSignatures.containsKey(method);
    }

    /**
     * One way mapping from one constructor to another. This is used for synthesized constructors,
     * where many constructors are merged into a single constructor. The synthesized constructor
     * therefore does not have a unique reverse constructor.
     */
    public Builder mapMergedConstructor(
        DexMethod from, DexMethod to, List<ExtraParameter> extraParameters) {
      mapMethod(from, to);
      methodExtraParameters.put(from, extraParameters);
      return this;
    }
  }
}
