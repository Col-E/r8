// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.lens.NestedGraphLens;
import com.android.tools.r8.ir.conversion.ExtraParameter;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneRepresentativeMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HorizontalClassMergerGraphLens extends NestedGraphLens {

  private final Map<DexMethod, List<ExtraParameter>> methodExtraParameters;
  private final HorizontallyMergedClasses mergedClasses;

  private HorizontalClassMergerGraphLens(
      AppView<?> appView,
      HorizontallyMergedClasses mergedClasses,
      Map<DexMethod, List<ExtraParameter>> methodExtraParameters,
      BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap,
      Map<DexMethod, DexMethod> methodMap,
      BidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod> newMethodSignatures) {
    super(appView, fieldMap, methodMap, mergedClasses.getBidirectionalMap(), newMethodSignatures);
    this.methodExtraParameters = methodExtraParameters;
    this.mergedClasses = mergedClasses;
  }

  DexMethod getNextMethodToInvoke(DexMethod method) {
    DexMethod nextMethod = methodMap.apply(method);
    return nextMethod != null ? nextMethod : method;
  }

  @Override
  public DexType getPreviousClassType(DexType type) {
    return type;
  }

  @Override
  public boolean isHorizontalClassMergerGraphLens() {
    return true;
  }

  @Override
  protected Iterable<DexType> internalGetOriginalTypes(DexType previous) {
    return IterableUtils.prependSingleton(previous, mergedClasses.getSourcesFor(previous));
  }

  /**
   * If an overloaded constructor is requested, add the constructor id as a parameter to the
   * constructor. Otherwise return the lookup on the underlying graph lens.
   */
  @Override
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
    List<ExtraParameter> extraParameters = methodExtraParameters.get(previous.getReference());
    MethodLookupResult lookup = super.internalDescribeLookupMethod(previous, context, codeLens);
    if (extraParameters == null) {
      return lookup;
    }
    return MethodLookupResult.builder(this)
        .setReference(lookup.getReference())
        .setPrototypeChanges(lookup.getPrototypeChanges().withExtraParameters(extraParameters))
        .setType(lookup.getType())
        .build();
  }

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    FieldLookupResult lookup = super.internalDescribeLookupField(previous);
    if (lookup.getReference() == previous.getReference()) {
      return lookup;
    }
    return FieldLookupResult.builder(this)
        .setReference(lookup.getReference())
        .setReboundReference(lookup.getReboundReference())
        .setReadCastType(
            lookup.getReference().getType() != previous.getReference().getType()
                ? lookupType(previous.getReference().getType())
                : null)
        .setWriteCastType(previous.getRewrittenWriteCastType(this::getNextClassType))
        .build();
  }

  public static class Builder {

    private final MutableBidirectionalManyToOneRepresentativeMap<DexField, DexField>
        newFieldSignatures = BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    final MutableBidirectionalManyToOneMap<DexMethod, DexMethod> methodMap =
        BidirectionalManyToOneHashMap.newIdentityHashMap();
    private final MutableBidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod>
        newMethodSignatures = BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    private final Map<DexMethod, List<ExtraParameter>> methodExtraParameters =
        new IdentityHashMap<>();

    private final MutableBidirectionalManyToOneMap<DexMethod, DexMethod> pendingMethodMapUpdates =
        BidirectionalManyToOneHashMap.newIdentityHashMap();
    private final MutableBidirectionalManyToOneRepresentativeMap<DexField, DexField>
        pendingNewFieldSignatureUpdates =
            BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    private final MutableBidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod>
        pendingNewMethodSignatureUpdates =
            BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();

    Builder() {}

    HorizontalClassMergerGraphLens build(
        AppView<?> appView, HorizontallyMergedClasses mergedClasses) {
      assert pendingMethodMapUpdates.isEmpty();
      assert pendingNewFieldSignatureUpdates.isEmpty();
      assert pendingNewMethodSignatureUpdates.isEmpty();
      assert newMethodSignatures.values().stream()
          .allMatch(
              value -> {
                assert newMethodSignatures.getKeys(value).size() == 1
                    || newMethodSignatures.hasExplicitRepresentativeKey(value);
                return true;
              });
      return new HorizontalClassMergerGraphLens(
          appView,
          mergedClasses,
          methodExtraParameters,
          newFieldSignatures,
          methodMap.getForwardMap(),
          newMethodSignatures);
    }

    DexMethod getRenamedMethodSignature(DexMethod method) {
      assert newMethodSignatures.containsKey(method);
      return newMethodSignatures.get(method);
    }

    void recordNewFieldSignature(DexField oldFieldSignature, DexField newFieldSignature) {
      newFieldSignatures.put(oldFieldSignature, newFieldSignature);
    }

    void recordNewFieldSignature(
        Iterable<DexField> oldFieldSignatures,
        DexField newFieldSignature,
        DexField representative) {
      assert Streams.stream(oldFieldSignatures)
          .anyMatch(oldFieldSignature -> oldFieldSignature != newFieldSignature);
      assert Streams.stream(oldFieldSignatures).noneMatch(newFieldSignatures::containsValue);
      assert Iterables.contains(oldFieldSignatures, representative);
      for (DexField oldFieldSignature : oldFieldSignatures) {
        recordNewFieldSignature(oldFieldSignature, newFieldSignature);
      }
      newFieldSignatures.setRepresentative(newFieldSignature, representative);
    }

    void fixupField(DexField oldFieldSignature, DexField newFieldSignature) {
      fixupOriginalMemberSignatures(
          oldFieldSignature,
          newFieldSignature,
          newFieldSignatures,
          pendingNewFieldSignatureUpdates);
    }

    void mapMethod(DexMethod oldMethodSignature, DexMethod newMethodSignature) {
      methodMap.put(oldMethodSignature, newMethodSignature);
    }

    void moveMethod(DexMethod from, DexMethod to) {
      moveMethod(from, to, false);
    }

    void moveMethod(DexMethod from, DexMethod to, boolean isRepresentative) {
      mapMethod(from, to);
      recordNewMethodSignature(from, to, isRepresentative);
    }

    void moveMethods(Iterable<ProgramMethod> methods, DexMethod to) {
      moveMethods(methods, to, null);
    }

    void moveMethods(Iterable<ProgramMethod> methods, DexMethod to, ProgramMethod representative) {
      for (ProgramMethod from : methods) {
        boolean isRepresentative = representative != null && from == representative;
        moveMethod(from.getReference(), to, isRepresentative);
      }
    }

    void recordNewMethodSignature(DexMethod oldMethodSignature, DexMethod newMethodSignature) {
      recordNewMethodSignature(oldMethodSignature, newMethodSignature, false);
    }

    void recordNewMethodSignature(
        DexMethod oldMethodSignature, DexMethod newMethodSignature, boolean isRepresentative) {
      newMethodSignatures.put(oldMethodSignature, newMethodSignature);
      if (isRepresentative) {
        newMethodSignatures.setRepresentative(newMethodSignature, oldMethodSignature);
      }
    }

    void fixupMethod(DexMethod oldMethodSignature, DexMethod newMethodSignature) {
      fixupMethodMap(oldMethodSignature, newMethodSignature);
      fixupOriginalMemberSignatures(
          oldMethodSignature,
          newMethodSignature,
          newMethodSignatures,
          pendingNewMethodSignatureUpdates);
    }

    private void fixupMethodMap(DexMethod oldMethodSignature, DexMethod newMethodSignature) {
      Set<DexMethod> originalMethodSignatures = methodMap.getKeys(oldMethodSignature);
      if (originalMethodSignatures.isEmpty()) {
        pendingMethodMapUpdates.put(oldMethodSignature, newMethodSignature);
      } else {
        for (DexMethod originalMethodSignature : originalMethodSignatures) {
          pendingMethodMapUpdates.put(originalMethodSignature, newMethodSignature);
        }
      }
    }

    private <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
        void fixupOriginalMemberSignatures(
            R oldMemberSignature,
            R newMemberSignature,
            MutableBidirectionalManyToOneRepresentativeMap<R, R> newMemberSignatures,
            MutableBidirectionalManyToOneRepresentativeMap<R, R> pendingNewMemberSignatureUpdates) {
      Set<R> oldMemberSignatures = newMemberSignatures.getKeys(oldMemberSignature);
      if (oldMemberSignatures.isEmpty()) {
        pendingNewMemberSignatureUpdates.put(oldMemberSignature, newMemberSignature);
      } else {
        for (R originalMethodSignature : oldMemberSignatures) {
          pendingNewMemberSignatureUpdates.put(originalMethodSignature, newMemberSignature);
        }
        R representative = newMemberSignatures.getRepresentativeKey(oldMemberSignature);
        if (representative != null) {
          pendingNewMemberSignatureUpdates.setRepresentative(newMemberSignature, representative);
        }
      }
    }

    void commitPendingUpdates() {
      // Commit pending method map updates.
      methodMap.removeAll(pendingMethodMapUpdates.keySet());
      pendingMethodMapUpdates.forEachManyToOneMapping(methodMap::put);
      pendingMethodMapUpdates.clear();

      // Commit pending original field and method signatures updates.
      commitPendingNewMemberSignatureUpdates(newFieldSignatures, pendingNewFieldSignatureUpdates);
      commitPendingNewMemberSignatureUpdates(newMethodSignatures, pendingNewMethodSignatureUpdates);
    }

    private <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
        void commitPendingNewMemberSignatureUpdates(
            MutableBidirectionalManyToOneRepresentativeMap<R, R> newMemberSignatures,
            MutableBidirectionalManyToOneRepresentativeMap<R, R> pendingNewMemberSignatureUpdates) {
      newMemberSignatures.removeAll(pendingNewMemberSignatureUpdates.keySet());
      newMemberSignatures.putAll(pendingNewMemberSignatureUpdates);
      pendingNewMemberSignatureUpdates.clear();
    }

    /**
     * One way mapping from one constructor to another. This is used for synthesized constructors,
     * where many constructors are merged into a single constructor. The synthesized constructor
     * therefore does not have a unique reverse constructor.
     */
    void mapMergedConstructor(DexMethod from, DexMethod to, List<ExtraParameter> extraParameters) {
      mapMethod(from, to);
      if (extraParameters.size() > 0) {
        methodExtraParameters.put(from, extraParameters);
      }
    }

    void addExtraParameters(
        DexMethod methodSignature, List<? extends ExtraParameter> extraParameters) {
      Set<DexMethod> originalMethodSignatures = methodMap.getKeys(methodSignature);
      if (originalMethodSignatures.isEmpty()) {
        methodExtraParameters
            .computeIfAbsent(methodSignature, ignore -> new ArrayList<>(extraParameters.size()))
            .addAll(extraParameters);
      } else {
        for (DexMethod originalMethodSignature : originalMethodSignatures) {
          methodExtraParameters
              .computeIfAbsent(
                  originalMethodSignature, ignore -> new ArrayList<>(extraParameters.size()))
              .addAll(extraParameters);
        }
      }
    }
  }
}
