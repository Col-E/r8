// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.DerivedMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineRewritingFlags;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RetargetingInfo {

  private final Map<DexMethod, DexMethod> staticRetarget;
  private final Map<DexMethod, DexMethod> nonEmulatedVirtualRetarget;
  private final Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedVirtualRetarget;

  RetargetingInfo(
      Map<DexMethod, DexMethod> staticRetarget,
      Map<DexMethod, DexMethod> nonEmulatedVirtualRetarget,
      Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedVirtualRetarget) {
    this.staticRetarget = staticRetarget;
    this.nonEmulatedVirtualRetarget = nonEmulatedVirtualRetarget;
    this.emulatedVirtualRetarget = emulatedVirtualRetarget;
  }

  public static RetargetingInfo get(AppView<?> appView) {
    if (appView.options().testing.machineDesugaredLibrarySpecification != null) {
      MachineRewritingFlags rewritingFlags =
          appView.options().testing.machineDesugaredLibrarySpecification.getRewritingFlags();
      return new RetargetingInfo(
          rewritingFlags.getStaticRetarget(),
          rewritingFlags.getNonEmulatedVirtualRetarget(),
          rewritingFlags.getEmulatedVirtualRetarget());
    }
    return new RetargetingInfoBuilder(appView).computeRetargetingInfo();
  }

  public Map<DexMethod, DexMethod> getStaticRetarget() {
    return staticRetarget;
  }

  public Map<DexMethod, DexMethod> getNonEmulatedVirtualRetarget() {
    return nonEmulatedVirtualRetarget;
  }

  public Map<DexMethod, EmulatedDispatchMethodDescriptor> getEmulatedVirtualRetarget() {
    return emulatedVirtualRetarget;
  }

  private static class RetargetingInfoBuilder {

    private final AppView<?> appView;
    private final Map<DexMethod, DexMethod> staticRetarget = new IdentityHashMap<>();
    private final Map<DexMethod, DexMethod> nonEmulatedVirtualRetarget = new IdentityHashMap<>();
    private final Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedVirtualRetarget =
        new IdentityHashMap<>();

    public RetargetingInfoBuilder(AppView<?> appView) {
      this.appView = appView;
    }

    private RetargetingInfo computeRetargetingInfo() {
      LegacyDesugaredLibrarySpecification desugaredLibrarySpecification =
          appView.options().desugaredLibrarySpecification;
      Map<DexString, Map<DexType, DexType>> retargetCoreLibMember =
          desugaredLibrarySpecification.getRetargetCoreLibMember();
      if (retargetCoreLibMember.isEmpty()) {
        return new RetargetingInfo(ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of());
      }
      for (DexString methodName : retargetCoreLibMember.keySet()) {
        for (DexType inType : retargetCoreLibMember.get(methodName).keySet()) {
          DexClass typeClass = appView.definitionFor(inType);
          if (typeClass != null) {
            DexType newHolder = retargetCoreLibMember.get(methodName).get(inType);
            List<DexClassAndMethod> found = findMethodsWithName(methodName, typeClass);
            for (DexClassAndMethod method : found) {
              DexMethod methodReference = method.getReference();
              if (method.getAccessFlags().isStatic()) {
                staticRetarget.put(
                    methodReference,
                    computeRetargetMethod(
                        methodReference, method.getAccessFlags().isStatic(), newHolder));
                continue;
              }
              if (isEmulatedInterfaceDispatch(method)) {
                continue;
              }
              if (typeClass.isFinal() || method.getAccessFlags().isFinal()) {
                nonEmulatedVirtualRetarget.put(
                    methodReference,
                    computeRetargetMethod(
                        methodReference, method.getAccessFlags().isStatic(), newHolder));
              } else {
                // Virtual rewrites require emulated dispatch for inheritance.
                // The call is rewritten to the dispatch holder class instead.
                DexProto newProto = appView.dexItemFactory().prependHolderToProto(methodReference);
                DexMethod forwardingDexMethod =
                    appView.dexItemFactory().createMethod(newHolder, newProto, methodName);
                DerivedMethod forwardingMethod = new DerivedMethod(forwardingDexMethod);
                DerivedMethod interfaceMethod =
                    new DerivedMethod(methodReference, SyntheticKind.RETARGET_INTERFACE);
                DerivedMethod dispatchMethod =
                    new DerivedMethod(methodReference, SyntheticKind.RETARGET_CLASS);
                emulatedVirtualRetarget.put(
                    methodReference,
                    new EmulatedDispatchMethodDescriptor(
                        interfaceMethod, dispatchMethod, forwardingMethod, new LinkedHashMap<>()));
              }
            }
          }
        }
      }
      if (desugaredLibrarySpecification.isLibraryCompilation()) {
        // TODO(b/177977763): This is only a workaround rewriting invokes of j.u.Arrays.deepEquals0
        // to j.u.DesugarArrays.deepEquals0.
        DexItemFactory itemFactory = appView.options().dexItemFactory();
        DexString name = itemFactory.createString("deepEquals0");
        DexProto proto =
            itemFactory.createProto(
                itemFactory.booleanType, itemFactory.objectType, itemFactory.objectType);
        DexMethod source =
            itemFactory.createMethod(
                itemFactory.createType(itemFactory.arraysDescriptor), proto, name);
        DexMethod target =
            computeRetargetMethod(
                source, true, itemFactory.createType("Ljava/util/DesugarArrays;"));
        staticRetarget.put(source, target);
        // TODO(b/181629049): This is only a workaround rewriting invokes of
        //  j.u.TimeZone.getTimeZone taking a java.time.ZoneId.
        name = itemFactory.createString("getTimeZone");
        proto =
            itemFactory.createProto(
                itemFactory.createType("Ljava/util/TimeZone;"),
                itemFactory.createType("Ljava/time/ZoneId;"));
        source =
            itemFactory.createMethod(itemFactory.createType("Ljava/util/TimeZone;"), proto, name);
        target =
            computeRetargetMethod(
                source, true, itemFactory.createType("Ljava/util/DesugarTimeZone;"));
        staticRetarget.put(source, target);
      }
      return new RetargetingInfo(
          ImmutableMap.copyOf(staticRetarget),
          ImmutableMap.copyOf(nonEmulatedVirtualRetarget),
          emulatedVirtualRetarget);
    }

    DexMethod computeRetargetMethod(DexMethod method, boolean isStatic, DexType newHolder) {
      DexItemFactory factory = appView.dexItemFactory();
      DexProto newProto = isStatic ? method.getProto() : factory.prependHolderToProto(method);
      return factory.createMethod(newHolder, newProto, method.getName());
    }

    private boolean isEmulatedInterfaceDispatch(DexClassAndMethod method) {
      // Answers true if this method is already managed through emulated interface dispatch.
      Map<DexType, DexType> emulateLibraryInterface =
          appView.options().desugaredLibrarySpecification.getEmulateLibraryInterface();
      if (emulateLibraryInterface.isEmpty()) {
        return false;
      }
      DexMethod methodToFind = method.getReference();
      // Look-up all superclass and interfaces, if an emulated interface is found, and it implements
      // the method, answers true.
      WorkList<DexClass> worklist = WorkList.newIdentityWorkList(method.getHolder());
      while (worklist.hasNext()) {
        DexClass clazz = worklist.next();
        if (clazz.isInterface()
            && emulateLibraryInterface.containsKey(clazz.getType())
            && clazz.lookupMethod(methodToFind) != null) {
          return true;
        }
        // All super types are library class, or we are doing L8 compilation.
        clazz.forEachImmediateSupertype(
            superType -> {
              DexClass superClass = appView.definitionFor(superType);
              if (superClass != null) {
                worklist.addIfNotSeen(superClass);
              }
            });
      }
      return false;
    }

    private List<DexClassAndMethod> findMethodsWithName(DexString methodName, DexClass clazz) {
      List<DexClassAndMethod> found = new ArrayList<>();
      clazz.forEachClassMethodMatching(
          definition -> definition.getName() == methodName, found::add);
      assert !found.isEmpty()
          : "Should have found a method (library specifications) for "
              + clazz.toSourceString()
              + "."
              + methodName
              + ". Maybe the library used for the compilation should be newer.";
      return found;
    }
  }
}
