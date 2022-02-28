// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.DerivedMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineRewritingFlags;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.collect.Sets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class HumanToMachineRetargetConverter {

  private final AppInfoWithClassHierarchy appInfo;
  private final Set<DexMethod> missingMethods = Sets.newIdentityHashSet();

  public HumanToMachineRetargetConverter(AppInfoWithClassHierarchy appInfo) {
    this.appInfo = appInfo;
  }

  public void convertRetargetFlags(
      HumanRewritingFlags rewritingFlags,
      MachineRewritingFlags.Builder builder,
      BiConsumer<String, Set<? extends DexReference>> warnConsumer) {
    rewritingFlags
        .getRetargetMethod()
        .forEach(
            (method, type) ->
                convertRetargetCoreLibMemberFlag(builder, rewritingFlags, method, type));
    warnConsumer.accept("Cannot retarget missing methods: ", missingMethods);
  }

  private void convertRetargetCoreLibMemberFlag(
      MachineRewritingFlags.Builder builder,
      HumanRewritingFlags rewritingFlags,
      DexMethod method,
      DexType type) {
    DexClass holder = appInfo.definitionFor(method.holder);
    DexEncodedMethod foundMethod = holder.lookupMethod(method);
    if (foundMethod == null) {
      missingMethods.add(method);
      return;
    }
    if (foundMethod.isStatic()) {
      convertStaticRetarget(builder, foundMethod, type);
      return;
    }
    if (holder.isFinal() || foundMethod.isFinal()) {
      convertNonEmulatedVirtualRetarget(builder, foundMethod, type);
      return;
    }
    convertEmulatedVirtualRetarget(builder, rewritingFlags, foundMethod, type);
  }

  private void convertEmulatedVirtualRetarget(
      MachineRewritingFlags.Builder builder,
      HumanRewritingFlags rewritingFlags,
      DexEncodedMethod src,
      DexType type) {
    DexProto newProto = appInfo.dexItemFactory().prependHolderToProto(src.getReference());
    DexMethod forwardingDexMethod =
        appInfo.dexItemFactory().createMethod(type, newProto, src.getName());
    if (isEmulatedInterfaceDispatch(src, appInfo, rewritingFlags)) {
      // Handled by emulated interface dispatch.
      builder.putEmulatedVirtualRetargetThroughEmulatedInterface(
          src.getReference(), forwardingDexMethod);
      return;
    }
    // TODO(b/184026720): Implement library boundaries.
    DerivedMethod forwardingMethod = new DerivedMethod(forwardingDexMethod);
    DerivedMethod interfaceMethod =
        new DerivedMethod(src.getReference(), SyntheticKind.RETARGET_INTERFACE);
    DerivedMethod dispatchMethod =
        new DerivedMethod(src.getReference(), SyntheticKind.RETARGET_CLASS);
    LinkedHashMap<DexType, DerivedMethod> dispatchCases = new LinkedHashMap<>();
    builder.putEmulatedVirtualRetarget(
        src.getReference(),
        new EmulatedDispatchMethodDescriptor(
            interfaceMethod, dispatchMethod, forwardingMethod, dispatchCases));
  }

  private boolean isEmulatedInterfaceDispatch(
      DexEncodedMethod method,
      AppInfoWithClassHierarchy appInfo,
      HumanRewritingFlags humanRewritingFlags) {
    // Answers true if this method is already managed through emulated interface dispatch.
    Map<DexType, DexType> emulateLibraryInterface = humanRewritingFlags.getEmulatedInterfaces();
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
