// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.FieldResolutionResult.SuccessfulFieldResolutionResult;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.MethodAccessInfoCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BiForEachable;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.function.BiFunction;
import java.util.function.Function;

public class MemberRebindingAnalysis {

  private final AppView<AppInfoWithLiveness> appView;
  private final GraphLens lens;
  private final InternalOptions options;

  private final MemberRebindingLens.Builder builder;

  public MemberRebindingAnalysis(AppView<AppInfoWithLiveness> appView) {
    assert appView.graphLens().isContextFreeForMethods();
    this.appView = appView;
    this.lens = appView.graphLens();
    this.options = appView.options();
    this.builder = MemberRebindingLens.builder(appView);
  }

  private DexMethod validTargetFor(DexMethod target, DexMethod original) {
    DexClass clazz = appView.definitionFor(target.holder);
    assert clazz != null;
    if (clazz.isProgramClass()) {
      return target;
    }
    DexType newHolder;
    if (clazz.isInterface()) {
      newHolder =
          firstLibraryClassForInterfaceTarget(target, original.holder, DexClass::lookupMethod);
    } else {
      newHolder = firstLibraryClass(target.holder, original.holder);
    }
    return newHolder == null
        ? original
        : appView.dexItemFactory().createMethod(newHolder, original.proto, original.name);
  }

  private DexField validTargetFor(DexField target, DexField original,
      BiFunction<DexClass, DexField, DexEncodedField> lookup) {
    DexClass clazz = appView.definitionFor(target.holder);
    assert clazz != null;
    if (clazz.isProgramClass()) {
      return target;
    }
    DexType newHolder;
    if (clazz.isInterface()) {
      newHolder = firstLibraryClassForInterfaceTarget(target, original.holder, lookup);
    } else {
      newHolder = firstLibraryClass(target.holder, original.holder);
    }
    return newHolder == null
        ? original
        : appView.dexItemFactory().createField(newHolder, original.type, original.name);
  }

  private <T> DexType firstLibraryClassForInterfaceTarget(T target, DexType current,
      BiFunction<DexClass, T, ?> lookup) {
    DexClass clazz = appView.definitionFor(current);
    if (clazz == null) {
      return null;
    }
    Object potential = lookup.apply(clazz, target);
    if (potential != null) {
      // Found, return type.
      return current;
    }
    if (clazz.superType != null) {
      DexType matchingSuper = firstLibraryClassForInterfaceTarget(target, clazz.superType, lookup);
      if (matchingSuper != null) {
        // Found in supertype, return first library class.
        return clazz.isNotProgramClass() ? current : matchingSuper;
      }
    }
    for (DexType iface : clazz.interfaces.values) {
      DexType matchingIface = firstLibraryClassForInterfaceTarget(target, iface, lookup);
      if (matchingIface != null) {
        // Found in interface, return first library class.
        return clazz.isNotProgramClass() ? current : matchingIface;
      }
    }
    return null;
  }

  private DexType firstLibraryClass(DexType top, DexType bottom) {
    assert appView.definitionFor(top).isNotProgramClass();
    DexClass searchClass = appView.definitionFor(bottom);
    while (searchClass.isProgramClass()) {
      searchClass = appView.definitionFor(searchClass.superType);
    }
    return searchClass.type;
  }

  private DexEncodedMethod classLookup(DexMethod method) {
    return appView.appInfo().resolveMethodOnClass(method, method.holder).getSingleTarget();
  }

  private DexEncodedMethod interfaceLookup(DexMethod method) {
    return appView.appInfo().resolveMethodOnInterface(method.holder, method).getSingleTarget();
  }

  private DexEncodedMethod anyLookup(DexMethod method) {
    return appView.appInfo().unsafeResolveMethodDueToDexFormat(method).getSingleTarget();
  }

  private void computeMethodRebinding(MethodAccessInfoCollection methodAccessInfoCollection) {
    // Virtual invokes are on classes, so use class resolution.
    computeMethodRebinding(
        methodAccessInfoCollection::forEachVirtualInvoke, this::classLookup, Type.VIRTUAL);
    // Interface invokes are always on interfaces, so use interface resolution.
    computeMethodRebinding(
        methodAccessInfoCollection::forEachInterfaceInvoke, this::interfaceLookup, Type.INTERFACE);
    // Super invokes can be on both kinds, decide using the holder class.
    computeMethodRebinding(
        methodAccessInfoCollection::forEachSuperInvoke, this::anyLookup, Type.SUPER);
    // Direct invokes (private/constructor) can also be on both kinds.
    computeMethodRebinding(
        methodAccessInfoCollection::forEachDirectInvoke, this::anyLookup, Type.DIRECT);
    // Likewise static invokes.
    computeMethodRebinding(
        methodAccessInfoCollection::forEachStaticInvoke, this::anyLookup, Type.STATIC);
  }

  private void computeMethodRebinding(
      BiForEachable<DexMethod, ProgramMethodSet> methodsWithContexts,
      Function<DexMethod, DexEncodedMethod> lookupTarget,
      Type invokeType) {
    methodsWithContexts.forEach(
        (method, contexts) -> {
          // We can safely ignore array types, as the corresponding methods are defined in a
          // library.
          if (!method.holder.isClassType()) {
            return;
          }
          DexClass originalClass = appView.definitionFor(method.holder);
          if (originalClass == null || originalClass.isNotProgramClass()) {
            return;
          }
          DexEncodedMethod target = lookupTarget.apply(method);
          // TODO(b/128404854) Rebind to the lowest library class or program class. For now we allow
          //  searching in library for methods, but this should be done on classpath instead.
          if (target == null || target.method == method) {
            return;
          }
          DexClass targetClass = appView.definitionFor(target.holder());
          if (originalClass.isProgramClass()) {
            // In Java bytecode, it is only possible to target interface methods that are in one of
            // the immediate super-interfaces via a super-invocation (see
            // IndirectSuperInterfaceTest).
            // To avoid introducing an IncompatibleClassChangeError at runtime we therefore insert a
            // bridge method when we are about to rebind to an interface method that is not the
            // original target.
            if (needsBridgeForInterfaceMethod(originalClass, targetClass, invokeType)) {
              target =
                  insertBridgeForInterfaceMethod(
                      method, target, originalClass.asProgramClass(), targetClass, lookupTarget);
            }

            // If the target class is not public but the targeted method is, we might run into
            // visibility problems when rebinding.
            final DexEncodedMethod finalTarget = target;
            if (contexts.stream()
                .anyMatch(context -> mayNeedBridgeForVisibility(context, finalTarget))) {
              target =
                  insertBridgeForVisibilityIfNeeded(
                      method, target, originalClass, targetClass, lookupTarget);
            }
          }
          builder.map(method, lens.lookupMethod(validTargetFor(target.method, method)), invokeType);
        });
  }

  private boolean needsBridgeForInterfaceMethod(
      DexClass originalClass, DexClass targetClass, Type invokeType) {
    return options.isGeneratingClassFiles()
        && invokeType == Type.SUPER
        && targetClass != originalClass
        && targetClass.accessFlags.isInterface();
  }

  private DexEncodedMethod insertBridgeForInterfaceMethod(
      DexMethod method,
      DexEncodedMethod target,
      DexProgramClass originalClass,
      DexClass targetClass,
      Function<DexMethod, DexEncodedMethod> lookupTarget) {
    // If `targetClass` is a class, then insert the bridge method on the upper-most super class that
    // implements the interface. Otherwise, if it is an interface, then insert the bridge method
    // directly on the interface (because that interface must be the immediate super type, assuming
    // that the super-invocation is not broken in advance).
    //
    // Note that, to support compiling from DEX to CF, we would need to rewrite the targets of
    // invoke-super instructions that hit indirect interface methods such that they always target
    // a method in an immediate super-interface, since this works on Art but not on the JVM.
    DexProgramClass bridgeHolder =
        findHolderForInterfaceMethodBridge(originalClass, targetClass.type);
    assert bridgeHolder != null;
    assert bridgeHolder != targetClass;
    DexEncodedMethod bridgeMethod = target.toForwardingMethod(bridgeHolder, appView);
    bridgeHolder.addMethod(bridgeMethod);
    assert lookupTarget.apply(method) == bridgeMethod;
    return bridgeMethod;
  }

  private DexProgramClass findHolderForInterfaceMethodBridge(DexProgramClass clazz, DexType iface) {
    if (clazz.accessFlags.isInterface()) {
      return clazz;
    }
    DexClass superClass = appView.definitionFor(clazz.superType);
    if (superClass == null
        || superClass.isNotProgramClass()
        || !appView.appInfo().isSubtype(superClass.type, iface)) {
      return clazz;
    }
    return findHolderForInterfaceMethodBridge(superClass.asProgramClass(), iface);
  }

  private boolean mayNeedBridgeForVisibility(ProgramMethod context, DexEncodedMethod method) {
    DexType holderType = method.holder();
    DexClass holder = appView.definitionFor(holderType);
    if (holder == null) {
      return false;
    }
    ConstraintWithTarget classVisibility =
        ConstraintWithTarget.deriveConstraint(
            context.getHolder(), holderType, holder.accessFlags, appView);
    ConstraintWithTarget methodVisibility =
        ConstraintWithTarget.deriveConstraint(
            context.getHolder(), holderType, method.accessFlags, appView);
    // We may need bridge for visibility if the target class is not visible while the target method
    // is visible from the calling context.
    return classVisibility == ConstraintWithTarget.NEVER
        && methodVisibility != ConstraintWithTarget.NEVER;
  }

  private DexEncodedMethod insertBridgeForVisibilityIfNeeded(
      DexMethod method,
      DexEncodedMethod target,
      DexClass originalClass,
      DexClass targetClass,
      Function<DexMethod, DexEncodedMethod> lookupTarget) {
    // If the original class is public and this method is public, it might have been called
    // from anywhere, so we need a bridge. Likewise, if the original is in a different
    // package, we might need a bridge, too.
    String packageDescriptor =
        originalClass.accessFlags.isPublic() ? null : method.holder.getPackageDescriptor();
    if (packageDescriptor == null
        || !packageDescriptor.equals(targetClass.type.getPackageDescriptor())) {
      DexProgramClass bridgeHolder =
          findHolderForVisibilityBridge(originalClass, targetClass, packageDescriptor);
      assert bridgeHolder != null;
      DexEncodedMethod bridgeMethod = target.toForwardingMethod(bridgeHolder, appView);
      bridgeHolder.addMethod(bridgeMethod);
      assert lookupTarget.apply(method) == bridgeMethod;
      return bridgeMethod;
    }
    return target;
  }

  private DexProgramClass findHolderForVisibilityBridge(
      DexClass originalClass, DexClass targetClass, String packageDescriptor) {
    if (originalClass == targetClass || originalClass.isNotProgramClass()) {
      return null;
    }
    DexProgramClass newHolder = null;
    // Recurse through supertype chain.
    if (appView.appInfo().isSubtype(originalClass.superType, targetClass.type)) {
      DexClass superClass = appView.definitionFor(originalClass.superType);
      newHolder = findHolderForVisibilityBridge(superClass, targetClass, packageDescriptor);
    } else {
      for (DexType iface : originalClass.interfaces.values) {
        if (appView.appInfo().isSubtype(iface, targetClass.type)) {
          DexClass interfaceClass = appView.definitionFor(iface);
          newHolder = findHolderForVisibilityBridge(interfaceClass, targetClass, packageDescriptor);
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

  private void computeFieldRebinding() {
    FieldAccessInfoCollection<?> fieldAccessInfoCollection =
        appView.appInfo().getFieldAccessInfoCollection();
    fieldAccessInfoCollection.forEach(this::computeFieldRebindingForIndirectAccesses);
  }

  private void computeFieldRebindingForIndirectAccesses(FieldAccessInfo fieldAccessInfo) {
    fieldAccessInfo.forEachIndirectAccessWithContexts(
        this::computeFieldRebindingForIndirectAccessWithContexts);
  }

  private void computeFieldRebindingForIndirectAccessWithContexts(
      DexField field, ProgramMethodSet contexts) {
    SuccessfulFieldResolutionResult resolutionResult =
        appView.appInfo().resolveField(field).asSuccessfulResolution();
    if (resolutionResult == null) {
      return;
    }

    DexClassAndField resolvedField = resolutionResult.getResolutionPair();
    if (resolvedField.getReference() == field) {
      assert false;
      return;
    }

    // Rebind to the lowest library class or program class. Do not rebind accesses to fields that
    // are not visible from the access context.
    boolean accessibleInAllContexts = true;
    for (ProgramMethod context : contexts) {
      boolean inaccessibleInContext =
          AccessControl.isMemberAccessible(
                  resolvedField, resolutionResult.getResolvedHolder(), context, appView)
              .isPossiblyFalse();
      if (inaccessibleInContext) {
        accessibleInAllContexts = false;
        break;
      }
    }

    if (accessibleInAllContexts) {
      builder.map(
          field,
          lens.lookupField(
              validTargetFor(resolvedField.getReference(), field, DexClass::lookupField)));
    }
  }

  public GraphLens run() {
    AppInfoWithLiveness appInfo = appView.appInfo();
    computeMethodRebinding(appInfo.getMethodAccessInfoCollection());
    computeFieldRebinding();
    GraphLens lens = builder.build(this.lens);
    appInfo.getFieldAccessInfoCollection().flattenAccessContexts();
    return lens;
  }
}
