// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.horizontalclassmerging.SubtypingForrestForClasses;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Prevent merging of classes where subclasses contain interface with default methods and the merged
 * class would contain a method with the same signature. Consider the following example: <code>
 *   class A {}
 *   class B {
 *     public void m() {
 *       // ...
 *     }
 *   }
 *
 *   interface {
 *     default void m() {
 *       // ...
 *     }
 *   }
 *
 *   class C extends A implements I {
 *   }
 * </code>
 *
 * <p>If A and B are merged, then the resulting class contains the method {@code void m()}. When
 * resolving m on C, the method would point to the method on the merged class rather than the
 * default interface method, changing runtime behaviour.
 *
 * <p>See: https://docs.oracle.com/javase/specs/jvms/se15/html/jvms-5.html#jvms-5.4.3.3)
 */
public class PreventMethodImplementation extends MultiClassPolicy {
  private final AppView<AppInfoWithLiveness> appView;
  private final SubtypingForrestForClasses subtypingForrestForClasses;

  private final InterfaceDefaultSignaturesCache interfaceDefaultMethodsCache =
      new InterfaceDefaultSignaturesCache();
  private final ParentClassSignaturesCache parentClassMethodsCache =
      new ParentClassSignaturesCache();
  private final ReservedInterfaceSignaturesFor reservedInterfaceSignaturesFor =
      new ReservedInterfaceSignaturesFor();

  private abstract static class SignaturesCache<C extends DexClass> {
    private final Map<DexClass, DexMethodSignatureSet> memoizedSignatures = new IdentityHashMap<>();

    public DexMethodSignatureSet getOrComputeSignatures(C clazz) {
      return memoizedSignatures.computeIfAbsent(
          clazz,
          ignore -> {
            DexMethodSignatureSet signatures = DexMethodSignatureSet.createLinked();
            process(clazz, signatures);
            return signatures;
          });
    }

    abstract void process(C clazz, DexMethodSignatureSet signatures);
  }

  private abstract class DexClassSignaturesCache extends SignaturesCache<DexClass> {

    DexMethodSignatureSet getOrComputeSignatures(DexType type) {
      DexClass clazz = appView.definitionFor(type);
      return clazz != null ? getOrComputeSignatures(clazz) : null;
    }
  }

  private class InterfaceDefaultSignaturesCache extends DexClassSignaturesCache {

    @Override
    void process(DexClass clazz, DexMethodSignatureSet signatures) {
      signatures.addAllMethods(clazz.virtualMethods(DexEncodedMethod::isDefaultMethod));
      signatures.addAll(clazz.getInterfaces(), this::getOrComputeSignatures);
    }
  }

  private class ParentClassSignaturesCache extends DexClassSignaturesCache {

    @Override
    void process(DexClass clazz, DexMethodSignatureSet signatures) {
      signatures.addAllMethods(clazz.methods());
      if (clazz.getSuperType() != null) {
        DexClass superClass = appView.definitionFor(clazz.getSuperType());
        if (superClass != null) {
          signatures.addAll(getOrComputeSignatures(superClass));
        }
      }
    }
  }

  private class ReservedInterfaceSignaturesFor extends SignaturesCache<DexProgramClass> {

    @Override
    void process(DexProgramClass clazz, DexMethodSignatureSet signatures) {
      signatures.addAll(
          clazz.getInterfaces(), interfaceDefaultMethodsCache::getOrComputeSignatures);
      signatures.addAll(
          subtypingForrestForClasses.getSubtypesFor(clazz), this::getOrComputeSignatures);
      signatures.removeAllMethods(clazz.methods());
    }
  }

  public PreventMethodImplementation(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.subtypingForrestForClasses = new SubtypingForrestForClasses(appView);
  }

  enum MethodCategory {
    CLASS_HIERARCHY_SAFE,
    KEEP_ABSENT,
  }

  static class DispatchSignature extends LinkedHashMap<DexMethodSignature, MethodCategory> {
    void addSignature(DexMethodSignature signature, MethodCategory category) {
      MethodCategory old = put(signature, category);
      assert old == null;
    }
  }

  DexMethodSignatureSet computeReservedSignaturesForClass(DexProgramClass clazz) {
    DexMethodSignatureSet reservedSignatures =
        DexMethodSignatureSet.create(reservedInterfaceSignaturesFor.getOrComputeSignatures(clazz));
    reservedSignatures.removeAll(parentClassMethodsCache.getOrComputeSignatures(clazz));
    return reservedSignatures;
  }

  @Override
  public Collection<List<DexProgramClass>> apply(List<DexProgramClass> group) {
    DexMethodSignatureSet signatures = DexMethodSignatureSet.createLinked();
    for (DexProgramClass clazz : group) {
      signatures.addAllMethods(clazz.methods());
    }

    Map<DispatchSignature, List<DexProgramClass>> newGroups = new LinkedHashMap<>();
    for (DexProgramClass clazz : group) {
      DexMethodSignatureSet clazzReserved = computeReservedSignaturesForClass(clazz);
      DispatchSignature dispatchSignature = new DispatchSignature();
      for (DexMethodSignature signature : signatures) {
        MethodCategory category = MethodCategory.CLASS_HIERARCHY_SAFE;
        if (clazzReserved.contains(signature)) {
          DexMethod template = signature.withHolder(clazz, appView.dexItemFactory());
          SingleResolutionResult result =
              appView.appInfo().resolveMethodOnClass(template, clazz).asSingleResolution();
          if (result == null || result.getResolvedHolder().isInterface()) {
            category = MethodCategory.KEEP_ABSENT;
          }
        }
        dispatchSignature.addSignature(signature, category);
      }
      newGroups.computeIfAbsent(dispatchSignature, ignore -> new LinkedList<>()).add(clazz);
    }
    return removeTrivialGroups(newGroups.values());
  }
}
