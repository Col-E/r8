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
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedInterfaceDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSyntheticKind;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class HumanToMachineEmulatedInterfaceConverter {

  private final AppInfoWithClassHierarchy appInfo;
  private final Map<DexType, List<DexType>> emulatedInterfaceHierarchy = new IdentityHashMap<>();
  private final Set<DexType> missingEmulatedInterface = Sets.newIdentityHashSet();

  public HumanToMachineEmulatedInterfaceConverter(AppInfoWithClassHierarchy appInfo) {
    this.appInfo = appInfo;
  }

  public void convertEmulatedInterfaces(
      HumanRewritingFlags rewritingFlags,
      AppInfoWithClassHierarchy appInfo,
      MachineRewritingFlags.Builder builder,
      BiConsumer<String, Set<? extends DexReference>> warnConsumer) {
    Map<DexType, DexType> emulateInterfaces = rewritingFlags.getEmulatedInterfaces();
    Set<DexMethod> dontRewriteInvocation = rewritingFlags.getDontRewriteInvocation();
    processEmulatedInterfaceHierarchy(appInfo, emulateInterfaces);
    for (DexType itf : emulateInterfaces.keySet()) {
      DexClass itfClass = appInfo.contextIndependentDefinitionFor(itf);
      if (itfClass == null) {
        missingEmulatedInterface.add(itf);
        continue;
      }
      Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedMethods = new IdentityHashMap<>();
      itfClass.forEachClassMethodMatching(
          m -> m.isDefaultMethod() && !dontRewriteInvocation.contains(m.getReference()),
          method ->
              emulatedMethods.put(
                  method.getReference(),
                  computeEmulatedDispatchDescriptor(
                      method.getReference(), rewritingFlags, appInfo)));
      builder.putEmulatedInterface(
          itf, new EmulatedInterfaceDescriptor(emulateInterfaces.get(itf), emulatedMethods));
    }
    warnConsumer.accept("Missing emulated interfaces: ", missingEmulatedInterface);
  }

  private EmulatedDispatchMethodDescriptor computeEmulatedDispatchDescriptor(
      DexMethod method, HumanRewritingFlags rewritingFlags, AppInfoWithClassHierarchy appInfo) {
    DerivedMethod forwardingMethod =
        new DerivedMethod(method, MachineSyntheticKind.Kind.COMPANION_CLASS);
    DexMethod itfDexMethod =
        appInfo
            .dexItemFactory()
            .createMethod(
                rewritingFlags.getEmulatedInterfaces().get(method.getHolderType()),
                method.getProto(),
                method.getName());
    DerivedMethod interfaceMethod = new DerivedMethod(itfDexMethod);
    DerivedMethod dispatchMethod =
        new DerivedMethod(method, MachineSyntheticKind.Kind.EMULATED_INTERFACE_CLASS);
    LinkedHashMap<DexType, DerivedMethod> dispatchCases = getDispatchCases(rewritingFlags, method);
    return new EmulatedDispatchMethodDescriptor(
        interfaceMethod, dispatchMethod, forwardingMethod, dispatchCases);
  }

  private LinkedHashMap<DexType, DerivedMethod> getDispatchCases(
      HumanRewritingFlags rewritingFlags, DexMethod method) {
    // To properly emulate the library interface call, we need to compute the interfaces
    // inheriting from the interface and manually implement the dispatch with instance of.
    // The list guarantees that an interface is always after interfaces it extends,
    // hence reverse iteration.
    List<DexType> subInterfaces = emulatedInterfaceHierarchy.get(method.getHolderType());
    LinkedHashMap<DexType, DerivedMethod> extraDispatchCases = new LinkedHashMap<>();
    // Retarget core lib emulated dispatch handled as part of emulated interface dispatch.
    Map<DexMethod, DexType> retargetCoreLibMember =
        rewritingFlags.getRetargetMethodEmulatedDispatchToType();
    for (DexMethod retarget : retargetCoreLibMember.keySet()) {
      if (retarget.match(method)) {
        DexClass inClass = appInfo.definitionFor(retarget.getHolderType());
        if (inClass != null && implementsInterface(inClass, method.getHolderType())) {
          DexProto newProto = appInfo.dexItemFactory().prependHolderToProto(retarget);
          DexMethod forwardingDexMethod =
              appInfo
                  .dexItemFactory()
                  .createMethod(retargetCoreLibMember.get(retarget), newProto, retarget.getName());
          extraDispatchCases.put(retarget.getHolderType(), new DerivedMethod(forwardingDexMethod));
        }
      }
    }
    if (subInterfaces != null) {
      for (int i = subInterfaces.size() - 1; i >= 0; i--) {
        DexClass subInterfaceClass = appInfo.definitionFor(subInterfaces.get(i));
        assert subInterfaceClass != null;
        // Else computation of subInterface would have failed.
        // if the method is implemented, extra dispatch is required.
        DexEncodedMethod result = subInterfaceClass.lookupVirtualMethod(method);
        if (result != null && !result.isAbstract()) {
          assert result.isDefaultMethod();
          DexMethod reference = result.getReference();
          extraDispatchCases.put(
              subInterfaceClass.type,
              new DerivedMethod(reference, MachineSyntheticKind.Kind.COMPANION_CLASS));
        }
      }
    } else {
      assert extraDispatchCases.size() <= 1;
    }
    return extraDispatchCases;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean implementsInterface(DexClass clazz, DexType interfaceType) {
    WorkList<DexType> workList =
        WorkList.newIdentityWorkList(Arrays.asList(clazz.interfaces.values));
    while (!workList.isEmpty()) {
      DexType next = workList.next();
      if (interfaceType == next) {
        return true;
      }
      DexClass nextClass = appInfo.definitionFor(next);
      if (nextClass != null) {
        workList.addIfNotSeen(nextClass.interfaces.values);
      }
    }
    return false;
  }

  private void processEmulatedInterfaceHierarchy(
      AppInfoWithClassHierarchy appInfo, Map<DexType, DexType> emulateInterfaces) {
    Set<DexType> processed = Sets.newIdentityHashSet();
    ArrayList<DexType> emulatedInterfacesSorted = new ArrayList<>(emulateInterfaces.keySet());
    emulatedInterfacesSorted.sort(DexType::compareTo);
    for (DexType interfaceType : emulatedInterfacesSorted) {
      processEmulatedInterfaceHierarchy(appInfo, emulateInterfaces, interfaceType, processed);
    }
  }

  private void processEmulatedInterfaceHierarchy(
      AppInfoWithClassHierarchy appInfo,
      Map<DexType, DexType> emulateInterfaces,
      DexType interfaceType,
      Set<DexType> processed) {
    if (processed.contains(interfaceType)) {
      return;
    }
    emulatedInterfaceHierarchy.put(interfaceType, new ArrayList<>());
    processed.add(interfaceType);
    DexClass theInterface = appInfo.definitionFor(interfaceType);
    if (theInterface == null) {
      return;
    }
    WorkList<DexType> workList =
        WorkList.newIdentityWorkList(Arrays.asList(theInterface.interfaces.values));
    while (!workList.isEmpty()) {
      DexType next = workList.next();
      if (emulateInterfaces.containsKey(next)) {
        processEmulatedInterfaceHierarchy(appInfo, emulateInterfaces, next, processed);
        emulatedInterfaceHierarchy.get(next).add(interfaceType);
        DexClass nextClass = appInfo.definitionFor(next);
        if (nextClass != null) {
          workList.addIfNotSeen(nextClass.interfaces.values);
        }
      }
    }
  }
}
