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
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.conversion.ExtraConstantIntParameter;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class HorizontalClassMergerGraphLens extends NestedGraphLens {
  private final AppView<?> appView;
  private final Map<DexMethod, Integer> constructorIds;
  private final Map<DexMethod, DexMethod> originalConstructorSignatures;

  private HorizontalClassMergerGraphLens(
      AppView<?> appView,
      Map<DexMethod, Integer> constructorIds,
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
    this.constructorIds = constructorIds;
    this.originalConstructorSignatures = originalConstructorSignatures;
  }

  @Override
  public DexMethod getOriginalMethodSignature(DexMethod method) {
    DexMethod originalConstructor = originalConstructorSignatures.get(method);
    if (originalConstructor == null) {
      return super.getOriginalMethodSignature(method);
    }
    return previousLens.getOriginalMethodSignature(originalConstructor);
  }

  public HorizontallyMergedClasses getHorizontallyMergedClasses() {
    return new HorizontallyMergedClasses(this.typeMap);
  }

  /**
   * If an overloaded constructor is requested, add the constructor id as a parameter to the
   * constructor. Otherwise return the lookup on the underlying graph lens.
   */
  @Override
  public GraphLensLookupResult lookupMethod(DexMethod method, DexMethod context, Type type) {
    Integer constructorId = constructorIds.get(method);
    GraphLensLookupResult lookup = super.lookupMethod(method, context, type);
    if (constructorId != null) {
      DexMethod newMethod = lookup.getMethod();
      return new GraphLensLookupResult(
          newMethod,
          mapInvocationType(newMethod, method, lookup.getType()),
          lookup
              .getPrototypeChanges()
              .withExtraParameter(new ExtraConstantIntParameter(constructorId)));
    } else {
      return lookup;
    }
  }

  public static class Builder {
    protected final BiMap<DexField, DexField> fieldMap = HashBiMap.create();
    protected final Map<DexMethod, DexMethod> methodMap = new IdentityHashMap<>();
    protected final Map<DexMethod, Set<DexMethod>> completeInverseMethodMap =
        new IdentityHashMap<>();

    private final BiMap<DexMethod, DexMethod> originalMethodSignatures = HashBiMap.create();
    private final Map<DexMethod, DexMethod> extraOriginalMethodSignatures = new IdentityHashMap<>();

    private final Map<DexMethod, Integer> constructorIds = new IdentityHashMap<>();

    Builder() {}

    public HorizontalClassMergerGraphLens build(
        AppView<?> appView, Map<DexType, DexType> mergedClasses) {
      if (mergedClasses.isEmpty()) {
        return null;
      } else {
        BiMap<DexField, DexField> originalFieldSignatures = fieldMap.inverse();
        return new HorizontalClassMergerGraphLens(
            appView,
            constructorIds,
            mergedClasses,
            fieldMap,
            methodMap,
            originalFieldSignatures,
            originalMethodSignatures,
            extraOriginalMethodSignatures,
            appView.graphLens());
      }
    }

    /** Bidirectional mapping from one method to another. */
    public Builder moveMethod(DexMethod from, DexMethod to) {
      mapMethod(from, to);
      recordOriginalSignature(from, to);
      return this;
    }

    public Builder recordOriginalSignature(DexMethod from, DexMethod to) {
      originalMethodSignatures.forcePut(to, originalMethodSignatures.getOrDefault(from, from));
      return this;
    }

    public Builder recordExtraOriginalSignature(DexMethod from, DexMethod to) {
      extraOriginalMethodSignatures.put(to, extraOriginalMethodSignatures.getOrDefault(from, from));
      return this;
    }

    /**
     * Unidirectional mapping from one method to another. This seems to only be used by synthesized
     * constructors so is private for now. See {@link Builder#moveConstructor(DexMethod,
     * DexMethod)}.
     */
    public Builder mapMethod(DexMethod from, DexMethod to) {
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

    public boolean hasOriginalConstructorSignatureMappingFor(DexMethod method) {
      return extraOriginalMethodSignatures.containsKey(method);
    }

    public boolean hasOriginalSignatureMappingFor(DexMethod method) {
      return originalMethodSignatures.containsKey(method);
    }

    /**
     * One way mapping from one constructor to another. This is used for synthesized constructors,
     * where many constructors are merged into a single constructor. The synthesized constructor
     * therefore does not have a unique reverse constructor.
     *
     * @param constructorId The id that must be appended to the constructor call to ensure the
     *     correct constructor is called.
     */
    public Builder mapConstructor(DexMethod from, DexMethod to, int constructorId) {
      mapMethod(from, to);
      constructorIds.put(from, constructorId);
      return this;
    }

    /**
     * Bidirectional mapping from one constructor to another. When a single constructor is simply
     * moved from one class to another, we can uniquely map the new constructor back to the old one.
     */
    public Builder moveConstructor(DexMethod from, DexMethod to) {
      return moveMethod(from, to);
    }
  }
}
