// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.DexClassAndMethodSet;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class RetargetingInfo {

  private final Map<DexMethod, DexMethod> retargetLibraryMember;
  // Map nonFinalRewrite hold a methodName -> method mapping for methods which are rewritten while
  // the holder is non final. In this case d8 needs to force resolution of given methods to see if
  // the invoke needs to be rewritten.
  private final Map<DexString, List<DexMethod>> nonFinalHolderRewrites;
  // Non final virtual library methods requiring generation of emulated dispatch.
  private final DexClassAndMethodSet emulatedDispatchMethods;

  RetargetingInfo(
      Map<DexMethod, DexMethod> retargetLibraryMember,
      Map<DexString, List<DexMethod>> nonFinalHolderRewrites,
      DexClassAndMethodSet emulatedDispatchMethods) {
    this.retargetLibraryMember = retargetLibraryMember;
    this.nonFinalHolderRewrites = nonFinalHolderRewrites;
    this.emulatedDispatchMethods = emulatedDispatchMethods;
  }

  public static synchronized RetargetingInfo get(AppView<?> appView) {
    return new RetargetingInfoBuilder(appView).computeRetargetingInfo();
  }

  public Map<DexMethod, DexMethod> getRetargetLibraryMember() {
    return retargetLibraryMember;
  }

  public Map<DexString, List<DexMethod>> getNonFinalHolderRewrites() {
    return nonFinalHolderRewrites;
  }

  public DexClassAndMethodSet getEmulatedDispatchMethods() {
    return emulatedDispatchMethods;
  }

  private static class RetargetingInfoBuilder {

    private final AppView<?> appView;
    private final Map<DexMethod, DexMethod> retargetLibraryMember = new IdentityHashMap<>();
    private final Map<DexString, List<DexMethod>> nonFinalHolderRewrites = new IdentityHashMap<>();
    private final DexClassAndMethodSet emulatedDispatchMethods = DexClassAndMethodSet.create();

    public RetargetingInfoBuilder(AppView<?> appView) {
      this.appView = appView;
    }

    private RetargetingInfo computeRetargetingInfo() {
      DesugaredLibraryConfiguration desugaredLibraryConfiguration =
          appView.options().desugaredLibraryConfiguration;
      Map<DexString, Map<DexType, DexType>> retargetCoreLibMember =
          desugaredLibraryConfiguration.getRetargetCoreLibMember();
      if (retargetCoreLibMember.isEmpty()) {
        return new RetargetingInfo(
            ImmutableMap.of(), ImmutableMap.of(), DexClassAndMethodSet.empty());
      }
      for (DexString methodName : retargetCoreLibMember.keySet()) {
        for (DexType inType : retargetCoreLibMember.get(methodName).keySet()) {
          DexClass typeClass = appView.definitionFor(inType);
          if (typeClass != null) {
            DexType newHolder = retargetCoreLibMember.get(methodName).get(inType);
            List<DexClassAndMethod> found = findMethodsWithName(methodName, typeClass);
            for (DexClassAndMethod method : found) {
              boolean emulatedDispatch = false;
              DexMethod methodReference = method.getReference();
              if (!typeClass.isFinal()) {
                nonFinalHolderRewrites.putIfAbsent(method.getName(), new ArrayList<>());
                nonFinalHolderRewrites.get(method.getName()).add(methodReference);
                if (!method.getAccessFlags().isStatic()) {
                  if (isEmulatedInterfaceDispatch(method)) {
                    // In this case interface method rewriter takes care of it.
                    continue;
                  } else if (!method.getAccessFlags().isFinal()) {
                    // Virtual rewrites require emulated dispatch for inheritance.
                    // The call is rewritten to the dispatch holder class instead.
                    emulatedDispatchMethods.add(method);
                    emulatedDispatch = true;
                  }
                }
              }
              if (!emulatedDispatch) {
                retargetLibraryMember.put(
                    methodReference,
                    computeRetargetMethod(
                        methodReference, method.getAccessFlags().isStatic(), newHolder));
              }
            }
          }
        }
      }
      if (desugaredLibraryConfiguration.isLibraryCompilation()) {
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
        retargetLibraryMember.put(source, target);
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
        retargetLibraryMember.put(source, target);
      }
      return new RetargetingInfo(
          ImmutableMap.copyOf(retargetLibraryMember),
          ImmutableMap.copyOf(nonFinalHolderRewrites),
          emulatedDispatchMethods);
    }

    DexMethod computeRetargetMethod(DexMethod method, boolean isStatic, DexType newHolder) {
      DexItemFactory factory = appView.dexItemFactory();
      DexProto newProto = isStatic ? method.getProto() : factory.prependHolderToProto(method);
      return factory.createMethod(newHolder, newProto, method.getName());
    }

    private boolean isEmulatedInterfaceDispatch(DexClassAndMethod method) {
      // Answers true if this method is already managed through emulated interface dispatch.
      Map<DexType, DexType> emulateLibraryInterface =
          appView.options().desugaredLibraryConfiguration.getEmulateLibraryInterface();
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
