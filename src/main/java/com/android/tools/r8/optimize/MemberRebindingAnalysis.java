// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public class MemberRebindingAnalysis {

  private final AppInfoWithLiveness appInfo;
  private final GraphLense lense;
  private final GraphLense.Builder builder = GraphLense.builder();

  public MemberRebindingAnalysis(AppInfoWithLiveness appInfo, GraphLense lense) {
    assert lense.isContextFree();
    this.appInfo = appInfo;
    this.lense = lense;
  }

  private DexMethod validTargetFor(DexMethod target, DexMethod original) {
    DexClass clazz = appInfo.definitionFor(target.getHolder());
    assert clazz != null;
    if (!clazz.isLibraryClass()) {
      return target;
    }
    DexType newHolder;
    if (clazz.isInterface()) {
      newHolder =
          firstLibraryClassForInterfaceTarget(target, original.getHolder(), DexClass::lookupMethod);
    } else {
      newHolder = firstLibraryClass(target.getHolder(), original.getHolder());
    }
    return appInfo.dexItemFactory.createMethod(newHolder, original.proto, original.name);
  }

  private DexField validTargetFor(DexField target, DexField original,
      BiFunction<DexClass, DexField, DexEncodedField> lookup) {
    DexClass clazz = appInfo.definitionFor(target.getHolder());
    assert clazz != null;
    if (!clazz.isLibraryClass()) {
      return target;
    }
    DexType newHolder;
    if (clazz.isInterface()) {
      newHolder = firstLibraryClassForInterfaceTarget(target, original.getHolder(), lookup);
    } else {
      newHolder = firstLibraryClass(target.getHolder(), original.getHolder());
    }
    return appInfo.dexItemFactory.createField(newHolder, original.type, original.name);
  }

  private <T> DexType firstLibraryClassForInterfaceTarget(T target, DexType current,
      BiFunction<DexClass, T, ?> lookup) {
    DexClass clazz = appInfo.definitionFor(current);
    Object potential = lookup.apply(clazz, target);
    if (potential != null) {
      // Found, return type.
      return current;
    }
    if (clazz.superType != null) {
      DexType matchingSuper = firstLibraryClassForInterfaceTarget(target, clazz.superType, lookup);
      if (matchingSuper != null) {
        // Found in supertype, return first library class.
        return clazz.isLibraryClass() ? current : matchingSuper;
      }
    }
    for (DexType iface : clazz.interfaces.values) {
      DexType matchingIface = firstLibraryClassForInterfaceTarget(target, iface, lookup);
      if (matchingIface != null) {
        // Found in interface, return first library class.
        return clazz.isLibraryClass() ? current : matchingIface;
      }
    }
    return null;
  }

  private DexType firstLibraryClass(DexType top, DexType bottom) {
    assert appInfo.definitionFor(top).isLibraryClass();
    DexClass searchClass = appInfo.definitionFor(bottom);
    while (!searchClass.isLibraryClass()) {
      searchClass = appInfo.definitionFor(searchClass.superType);
    }
    return searchClass.type;
  }

  private DexEncodedMethod classLookup(DexMethod method) {
    return appInfo.resolveMethodOnClass(method.getHolder(), method).asResultOfResolve();
  }

  private DexEncodedMethod interfaceLookup(DexMethod method) {
    return appInfo.resolveMethodOnInterface(method.getHolder(), method).asResultOfResolve();
  }

  private DexEncodedMethod anyLookup(DexMethod method) {
    return appInfo.resolveMethod(method.getHolder(), method).asResultOfResolve();
  }

  private void computeMethodRebinding(Set<DexMethod> methods,
      Function<DexMethod, DexEncodedMethod> lookupTarget) {
    for (DexMethod method : methods) {
      method = lense.lookupMethod(method, null);
      // We can safely ignore array types, as the corresponding methods are defined in a library.
      if (!method.getHolder().isClassType()) {
        continue;
      }
      DexClass originalClass = appInfo.definitionFor(method.holder);
      // We can safely ignore calls to library classes, as those cannot be rebound.
      if (originalClass == null || originalClass.isLibraryClass()) {
        continue;
      }
      DexEncodedMethod target = lookupTarget.apply(method);
      // Rebind to the lowest library class or program class.
      if (target != null && target.method != method) {
        DexClass targetClass = appInfo.definitionFor(target.method.holder);
        // If the target class is not public but the targeted method is, we might run into
        // visibility problems when rebinding.
        if (!targetClass.accessFlags.isPublic() && target.accessFlags.isPublic()) {
          // If the original class is public and this method is public, it might have been called
          // from anywhere, so we need a bridge. Likewise, if the original is in a different
          // package, we might need a bridge, too.
          String packageDescriptor =
              originalClass.accessFlags.isPublic() ? null : method.holder.getPackageDescriptor();
          if (packageDescriptor == null
              || !packageDescriptor.equals(targetClass.type.getPackageDescriptor())) {
            DexProgramClass bridgeHolder = findBridgeMethodHolder(originalClass, targetClass,
                packageDescriptor);
            assert bridgeHolder != null;
            DexEncodedMethod bridgeMethod =
                target.toForwardingMethod(bridgeHolder, appInfo.dexItemFactory);
            bridgeHolder.addMethod(bridgeMethod);
            assert lookupTarget.apply(method) == bridgeMethod;
            target = bridgeMethod;
          }
        }
        builder.map(method, validTargetFor(target.method, method));
      }
    }
  }

  private DexProgramClass findBridgeMethodHolder(DexClass originalClass, DexClass targetClass,
      String packageDescriptor) {
    if (originalClass == targetClass || originalClass.isLibraryClass()) {
      return null;
    }
    DexProgramClass newHolder = null;
    // Recurse through supertype chain.
    if (originalClass.superType.isSubtypeOf(targetClass.type, appInfo)) {
      DexClass superClass = appInfo.definitionFor(originalClass.superType);
      newHolder = findBridgeMethodHolder(superClass, targetClass, packageDescriptor);
    } else {
      for (DexType iface : originalClass.interfaces.values) {
        if (iface.isSubtypeOf(targetClass.type, appInfo)) {
          DexClass interfaceClass = appInfo.definitionFor(iface);
          newHolder = findBridgeMethodHolder(interfaceClass, targetClass, packageDescriptor);
        }
      }
    }
    if (newHolder != null) {
      // A supertype fulfills the visibility requirements.
      return newHolder;
    } else if (originalClass.accessFlags.isPublic()
        || originalClass.type.getPackageDescriptor().equals(packageDescriptor)) {
      // This class is visible. Return it if it is a program class, otherwise null.
      return originalClass.asProgramClass();
    }
    return null;
  }

  private void computeFieldRebinding(Map<DexField, Set<DexEncodedMethod>> fields,
      BiFunction<DexType, DexField, DexEncodedField> lookup,
      BiFunction<DexClass, DexField, DexEncodedField> lookupTargetOnClass) {
    for (Map.Entry<DexField, Set<DexEncodedMethod>> entry : fields.entrySet()) {
      DexField field = entry.getKey();
      field = lense.lookupField(field, null);
      DexEncodedField target = lookup.apply(field.getHolder(), field);
      // Rebind to the lowest library class or program class. Do not rebind accesses to fields that
      // are not visible from the access context.
      Set<DexEncodedMethod> contexts = entry.getValue();
      if (target != null && target.field != field
          && contexts.stream().allMatch(context ->
              isVisibleFromOriginalContext(context.method.getHolder(), target))) {
        builder.map(field, validTargetFor(target.field, field, lookupTargetOnClass));
      }
    }
  }

  private boolean isVisibleFromOriginalContext(DexType context, DexEncodedField field) {
    DexType holderType = field.field.getHolder();
    DexClass holder = appInfo.definitionFor(holderType);
    if (holder == null) {
      return false;
    }
    Constraint classVisibility =
        Constraint.deriveConstraint(context, holderType, holder.accessFlags, appInfo);
    if (classVisibility == Constraint.NEVER) {
      return false;
    }
    Constraint fieldVisibility =
        Constraint.deriveConstraint(context, holderType, field.accessFlags, appInfo);
    return fieldVisibility != Constraint.NEVER;
  }

  private Map<DexField, Set<DexEncodedMethod>> mergeFieldAccessContexts(
      Map<DexField, Set<DexEncodedMethod>> reads,
      Map<DexField, Set<DexEncodedMethod>> writes) {
    Map<DexField, Set<DexEncodedMethod>> result = new IdentityHashMap<>();
    Set<DexField> fields = Sets.union(reads.keySet(), writes.keySet());
    for (DexField field : fields) {
      Set<DexEncodedMethod> contexts = Sets.newIdentityHashSet();
      contexts.addAll(reads.getOrDefault(field, ImmutableSet.of()));
      contexts.addAll(writes.getOrDefault(field, ImmutableSet.of()));
      result.put(field, contexts);
    }
    return Collections.unmodifiableMap(result);
  }

  public GraphLense run() {
    // Virtual invokes are on classes, so use class resolution.
    computeMethodRebinding(appInfo.virtualInvokes, this::classLookup);
    // Interface invokes are always on interfaces, so use interface resolution.
    computeMethodRebinding(appInfo.interfaceInvokes, this::interfaceLookup);
    // Super invokes can be on both kinds, decide using the holder class.
    computeMethodRebinding(appInfo.superInvokes, this::anyLookup);
    // Direct invokes (private/constructor) can also be on both kinds.
    computeMethodRebinding(appInfo.directInvokes, this::anyLookup);
    // Likewise static invokes.
    computeMethodRebinding(appInfo.staticInvokes, this::anyLookup);

    computeFieldRebinding(
        mergeFieldAccessContexts(appInfo.staticFieldReads, appInfo.staticFieldWrites),
        appInfo::resolveFieldOn, DexClass::lookupField);
    computeFieldRebinding(
        mergeFieldAccessContexts(appInfo.instanceFieldReads, appInfo.instanceFieldWrites),
        appInfo::resolveFieldOn, DexClass::lookupField);

    return builder.build(appInfo.dexItemFactory, lense);
  }
}
