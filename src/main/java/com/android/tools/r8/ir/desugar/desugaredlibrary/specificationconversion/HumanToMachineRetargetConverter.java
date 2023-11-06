// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags.HumanEmulatedInterfaceDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.DerivedMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSyntheticKind;
import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.collect.Sets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class HumanToMachineRetargetConverter {

  private final AppInfoWithClassHierarchy appInfo;
  private final Set<DexReference> missingReferences = Sets.newIdentityHashSet();

  public HumanToMachineRetargetConverter(AppInfoWithClassHierarchy appInfo) {
    this.appInfo = appInfo;
  }

  public void convertRetargetFlags(
      HumanRewritingFlags rewritingFlags,
      MachineRewritingFlags.Builder builder,
      BiConsumer<String, Set<? extends DexReference>> warnConsumer) {
    rewritingFlags
        .getRetargetStaticField()
        .forEach((field, rewrittenField) -> convertRetargetField(builder, field, rewrittenField));
    rewritingFlags
        .getCovariantRetarget()
        .forEach((method, type) -> convertCovariantRetarget(builder, method, type));
    rewritingFlags
        .getRetargetMethodToType()
        .forEach((method, type) -> convertRetargetMethodToType(builder, method, type));
    rewritingFlags
        .getRetargetMethodEmulatedDispatchToType()
        .forEach(
            (method, type) ->
                convertRetargetMethodEmulatedDispatchToType(builder, rewritingFlags, method, type));
    rewritingFlags
        .getRetargetMethodToMethod()
        .forEach((method, retarget) -> convertRetargetMethodToMethod(builder, method, retarget));
    rewritingFlags
        .getRetargetMethodEmulatedDispatchToMethod()
        .forEach(
            (method, retarget) ->
                convertRetargetMethodEmulatedDispatchToMethod(
                    builder, rewritingFlags, method, retarget));
    warnConsumer.accept("Cannot retarget missing references: ", missingReferences);
  }

  private void convertRetargetField(
      MachineRewritingFlags.Builder builder, DexField field, DexField rewrittenField) {
    DexClass holder = appInfo.definitionFor(field.holder);
    DexEncodedField foundField = holder.lookupField(field);
    if (foundField == null) {
      missingReferences.add(field);
      return;
    }
    builder.putStaticFieldRetarget(field, rewrittenField);
  }

  private void convertCovariantRetarget(
      MachineRewritingFlags.Builder builder, DexMethod method, DexType type) {
    DexClass holder = appInfo.definitionFor(method.holder);
    DexProto proto = method.getProto();
    DexProto newProto = appInfo.dexItemFactory().createProto(type, proto.parameters);
    DexMethod covariant = method.withProto(newProto, appInfo.dexItemFactory());
    DexEncodedMethod foundMethod = holder.lookupMethod(covariant);
    if (foundMethod == null) {
      missingReferences.add(covariant);
      return;
    }
    if (foundMethod.isStatic()) {
      appInfo
          .app()
          .options
          .reporter
          .warning(
              "Static method "
                  + foundMethod
                  + " is flagged as having a covariant return type, which is not possible.");
      return;
    }
    if (seemsToNeedEmulatedDispatch(holder, foundMethod)) {
      appInfo
          .app()
          .options
          .reporter
          .warning(
              "Covariant retargeting of non final method "
                  + foundMethod
                  + " which could lead to invalid runtime execution in overrides.");
    }
    builder.putCovariantRetarget(method, covariant);
  }

  private void convertRetargetMethod(
      DexMethod method,
      Consumer<DexEncodedMethod> staticRetarget,
      Consumer<DexEncodedMethod> nonEmulatedVirtualRetarget) {
    DexClass holder = appInfo.definitionFor(method.holder);
    DexEncodedMethod foundMethod = holder.lookupMethod(method);
    if (foundMethod == null) {
      missingReferences.add(method);
      return;
    }
    if (foundMethod.isStatic()) {
      staticRetarget.accept(foundMethod);
      return;
    }
    if (seemsToNeedEmulatedDispatch(holder, foundMethod)) {
      appInfo
          .app()
          .options
          .reporter
          .warning(
              "Retargeting non final method "
                  + foundMethod
                  + " which could lead to invalid runtime execution in overrides.");
    }
    nonEmulatedVirtualRetarget.accept(foundMethod);
  }

  private void convertRetargetMethodToType(
      MachineRewritingFlags.Builder builder, DexMethod method, DexType type) {
    convertRetargetMethod(
        method,
        foundMethod -> convertStaticRetarget(builder, foundMethod, type),
        foundMethod -> convertNonEmulatedVirtualRetarget(builder, foundMethod, type));
  }

  private void convertRetargetMethodToMethod(
      MachineRewritingFlags.Builder builder, DexMethod method, DexMethod retarget) {
    convertRetargetMethod(
        method,
        foundMethod -> builder.putStaticRetarget(method, retarget),
        foundMethod -> builder.putNonEmulatedVirtualRetarget(method, retarget));
  }

  private void convertRetargetMethodEmulatedDispatch(
      DexMethod method, Consumer<DexEncodedMethod> emulatedRetarget) {
    DexClass holder = appInfo.definitionFor(method.holder);
    DexEncodedMethod foundMethod = holder.lookupMethod(method);
    if (foundMethod == null) {
      missingReferences.add(method);
      return;
    }
    if (foundMethod.isStatic()) {
      appInfo
          .app()
          .options
          .reporter
          .error("Cannot generate emulated dispatch for static method " + foundMethod);
      return;
    }
    if (!seemsToNeedEmulatedDispatch(holder, foundMethod)) {
      appInfo
          .app()
          .options
          .reporter
          .warning(
              "Generating (seemingly unnecessary) emulated dispatch for final method "
                  + foundMethod);
    }
    emulatedRetarget.accept(foundMethod);
  }

  private void convertRetargetMethodEmulatedDispatchToType(
      MachineRewritingFlags.Builder builder,
      HumanRewritingFlags rewritingFlags,
      DexMethod method,
      DexType type) {
    convertRetargetMethodEmulatedDispatch(
        method,
        foundMethod -> convertEmulatedVirtualRetarget(builder, rewritingFlags, foundMethod, type));
  }

  private void convertRetargetMethodEmulatedDispatchToMethod(
      MachineRewritingFlags.Builder builder,
      HumanRewritingFlags rewritingFlags,
      DexMethod method,
      DexMethod retarget) {
    convertRetargetMethodEmulatedDispatch(
        method,
        foundMethod ->
            convertEmulatedVirtualRetarget(builder, rewritingFlags, foundMethod, retarget));
  }

  private boolean seemsToNeedEmulatedDispatch(DexClass holder, DexEncodedMethod method) {
    assert !method.isStatic();
    return !(holder.isFinal() || method.isFinal());
  }

  private void convertEmulatedVirtualRetarget(
      MachineRewritingFlags.Builder builder,
      HumanRewritingFlags rewritingFlags,
      DexEncodedMethod src,
      DexMethod forwardingDexMethod) {
    if (isEmulatedInterfaceDispatch(src, appInfo, rewritingFlags)) {
      // Handled by emulated interface dispatch.
      builder.putEmulatedVirtualRetargetThroughEmulatedInterface(
          src.getReference(), forwardingDexMethod);
      return;
    }
    // TODO(b/184026720): Implement library boundaries.
    DerivedMethod forwardingMethod = new DerivedMethod(forwardingDexMethod);
    DerivedMethod interfaceMethod =
        new DerivedMethod(src.getReference(), MachineSyntheticKind.Kind.RETARGET_INTERFACE);
    DerivedMethod dispatchMethod =
        new DerivedMethod(src.getReference(), MachineSyntheticKind.Kind.RETARGET_CLASS);
    LinkedHashMap<DexType, DerivedMethod> dispatchCases = new LinkedHashMap<>();
    builder.putEmulatedVirtualRetarget(
        src.getReference(),
        new EmulatedDispatchMethodDescriptor(
            interfaceMethod, dispatchMethod, forwardingMethod, dispatchCases));
  }

  private void convertEmulatedVirtualRetarget(
      MachineRewritingFlags.Builder builder,
      HumanRewritingFlags rewritingFlags,
      DexEncodedMethod src,
      DexType type) {
    DexProto newProto = appInfo.dexItemFactory().prependHolderToProto(src.getReference());
    DexMethod forwardingDexMethod =
        appInfo.dexItemFactory().createMethod(type, newProto, src.getName());
    convertEmulatedVirtualRetarget(builder, rewritingFlags, src, forwardingDexMethod);
  }

  private boolean isEmulatedInterfaceDispatch(
      DexEncodedMethod method,
      AppInfoWithClassHierarchy appInfo,
      HumanRewritingFlags humanRewritingFlags) {
    // Answers true if this method is already managed through emulated interface dispatch.
    Map<DexType, HumanEmulatedInterfaceDescriptor> emulateLibraryInterface =
        humanRewritingFlags.getEmulatedInterfaces();
    if (emulateLibraryInterface.isEmpty()) {
      return false;
    }
    DexMethod methodToFind = method.getReference();
    // Look-up all superclass and interfaces, if an emulated interface is found,
    // and it implements the method, answers true.
    DexClass dexClass = appInfo.definitionFor(method.getHolderType());
    // Cannot retarget a method on a virtual method on an emulated interface.
    assert !emulateLibraryInterface.containsKey(dexClass.getType());
    return appInfo
        .traverseSuperTypes(
            dexClass,
            (supertype, subclass, isSupertypeAnInterface) ->
                TraversalContinuation.breakIf(
                    subclass.isInterface()
                        && emulateLibraryInterface.containsKey(subclass.getType())
                        && subclass.lookupMethod(methodToFind) != null))
        .shouldBreak();
  }

  private void convertNonEmulatedRetarget(
      DexEncodedMethod foundMethod,
      DexType type,
      AppInfoWithClassHierarchy appInfo,
      BiConsumer<DexMethod, DexMethod> consumer) {
    DexMethod src = foundMethod.getReference();
    DexMethod dest = src.withHolder(type, appInfo.dexItemFactory());
    consumer.accept(src, dest);
  }

  private void convertNonEmulatedVirtualRetarget(
      MachineRewritingFlags.Builder builder, DexEncodedMethod foundMethod, DexType type) {
    convertNonEmulatedRetarget(
        foundMethod,
        type,
        appInfo,
        (src, dest) ->
            builder.putNonEmulatedVirtualRetarget(
                src,
                dest.withExtraArgumentPrepended(
                    foundMethod.getHolderType(), appInfo.dexItemFactory())));
  }

  private void convertStaticRetarget(
      MachineRewritingFlags.Builder builder, DexEncodedMethod foundMethod, DexType type) {
    convertNonEmulatedRetarget(foundMethod, type, appInfo, builder::putStaticRetarget);
  }
}
