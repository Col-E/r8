// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.naming.MethodNameMinifier.shuffleMethods;

import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.MethodNameMinifier.FrontierState;
import com.android.tools.r8.naming.MethodNameMinifier.MethodNamingState;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

class InterfaceMethodNameMinifier {

  private final AppInfoWithLiveness appInfo;
  private final Set<DexCallSite> desugaredCallSites;
  private final Equivalence<DexMethod> equivalence;
  private final FrontierState frontierState;
  private final MemberNameMinifier<DexMethod, DexProto>.State minifierState;
  private final InternalOptions options;

  private final Map<DexCallSite, DexString> callSiteRenamings = new IdentityHashMap<>();

  /** A map from DexMethods to all the states linked to interfaces they appear in. */
  private final Map<Wrapper<DexMethod>, Set<NamingState<DexProto, ?>>> globalStateMap =
      new HashMap<>();

  /** A map from DexMethods to the first interface state it was seen in. Used to pick good names. */
  private final Map<Wrapper<DexMethod>, NamingState<DexProto, ?>> originStates = new HashMap<>();

  /**
   * A map from DexMethods to all the definitions seen. Needed as the Wrapper equalizes them all.
   */
  private final Map<Wrapper<DexMethod>, Set<DexMethod>> sourceMethodsMap = new HashMap<>();

  InterfaceMethodNameMinifier(
      AppInfoWithLiveness appInfo,
      Set<DexCallSite> desugaredCallSites,
      Equivalence<DexMethod> equivalence,
      FrontierState frontierState,
      MemberNameMinifier<DexMethod, DexProto>.State minifierState,
      InternalOptions options) {
    this.appInfo = appInfo;
    this.desugaredCallSites = desugaredCallSites;
    this.equivalence = equivalence;
    this.frontierState = frontierState;
    this.minifierState = minifierState;
    this.options = options;
  }

  Map<DexCallSite, DexString> getCallSiteRenamings() {
    return callSiteRenamings;
  }

  private void reserveNamesInInterfaces() {
    for (DexType type : DexType.allInterfaces(appInfo.dexItemFactory)) {
      assert type.isInterface();
      frontierState.put(type, type);
      frontierState.allocateNamingStateAndReserve(type, type, null);
    }
  }

  void assignNamesToInterfaceMethods(Timing timing) {
    // Reserve all the names that are required for interfaces.
    reserveNamesInInterfaces();

    // First compute a map from method signatures to a set of naming states for interfaces and
    // frontier states of classes that implement them. We add the frontier states so that we can
    // reserve the names for later method naming.
    timing.begin("Compute map");
    for (DexType type : DexType.allInterfaces(appInfo.dexItemFactory)) {
      assert type.isInterface();
      DexClass clazz = appInfo.definitionFor(type);
      if (clazz != null) {
        assert clazz.isInterface();
        Set<NamingState<DexProto, ?>> collectedStates = getReachableStates(type);
        for (DexEncodedMethod method : shuffleMethods(clazz.methods(), options)) {
          addStatesToGlobalMapForMethod(method, collectedStates, type);
        }
      }
    }

    // Collect the live call sites for multi-interface lambda expression renaming. For code with
    // desugared lambdas this is a conservative estimate, as we don't track if the generated
    // lambda classes survive into the output. As multi-interface lambda expressions are rare
    // this is not a big deal.
    Set<DexCallSite> liveCallSites = Sets.union(desugaredCallSites, appInfo.callSites);
    // If the input program contains a multi-interface lambda expression that implements
    // interface methods with different protos, we need to make sure that
    // the implemented lambda methods are renamed to the same name.
    // To achieve this, we map each DexCallSite that corresponds to a lambda expression to one of
    // the DexMethods it implements, and we unify the DexMethods that need to be renamed together.
    Map<DexCallSite, DexMethod> callSites = new IdentityHashMap<>();
    // Union-find structure to keep track of methods that must be renamed together.
    // Note that if the input does not use multi-interface lambdas,
    // unificationParent will remain empty.
    Map<Wrapper<DexMethod>, Wrapper<DexMethod>> unificationParent = new HashMap<>();
    liveCallSites.forEach(
        callSite -> {
          Set<Wrapper<DexMethod>> callSiteMethods = new HashSet<>();
          // Don't report errors, as the set of call sites is a conservative estimate, and can
          // refer to interfaces which has been removed.
          Set<DexEncodedMethod> implementedMethods =
              appInfo.lookupLambdaImplementedMethods(callSite);
          if (implementedMethods.isEmpty()) {
            return;
          }
          callSites.put(callSite, implementedMethods.iterator().next().method);
          for (DexEncodedMethod method : implementedMethods) {
            DexType iface = method.method.holder;
            assert iface.isInterface();
            Set<NamingState<DexProto, ?>> collectedStates = getReachableStates(iface);
            addStatesToGlobalMapForMethod(method, collectedStates, iface);
            callSiteMethods.add(equivalence.wrap(method.method));
          }
          if (callSiteMethods.size() > 1) {
            // Implemented interfaces have different return types. Unify them.
            Wrapper<DexMethod> mainKey = callSiteMethods.iterator().next();
            mainKey = unificationParent.getOrDefault(mainKey, mainKey);
            for (Wrapper<DexMethod> key : callSiteMethods) {
              unificationParent.put(key, mainKey);
            }
          }
        });
    Map<Wrapper<DexMethod>, Set<Wrapper<DexMethod>>> unification = new HashMap<>();
    for (Wrapper<DexMethod> key : unificationParent.keySet()) {
      // Find root with path-compression.
      Wrapper<DexMethod> root = unificationParent.get(key);
      while (unificationParent.get(root) != root) {
        Wrapper<DexMethod> k = unificationParent.get(unificationParent.get(root));
        unificationParent.put(root, k);
        root = k;
      }
      unification.computeIfAbsent(root, k -> new HashSet<>()).add(key);
    }
    timing.end();
    // Go over every method and assign a name.
    timing.begin("Allocate names");
    // Sort the methods by the number of dependent states, so that we use short names for methods
    // that are referenced in many places.
    List<Wrapper<DexMethod>> methods = new ArrayList<>(globalStateMap.keySet());
    methods.sort((a, b) -> globalStateMap.get(b).size() - globalStateMap.get(a).size());
    for (Wrapper<DexMethod> key : methods) {
      if (!unificationParent.getOrDefault(key, key).equals(key)) {
        continue;
      }
      List<MethodNamingState> collectedStates = new ArrayList<>();
      Set<DexMethod> sourceMethods = Sets.newIdentityHashSet();
      for (Wrapper<DexMethod> k : unification.getOrDefault(key, Collections.singleton(key))) {
        DexMethod unifiedMethod = k.get();
        assert unifiedMethod != null;
        sourceMethods.addAll(sourceMethodsMap.get(k));
        for (NamingState<DexProto, ?> namingState : globalStateMap.get(k)) {
          collectedStates.add(
              new MethodNamingState(namingState, unifiedMethod.name, unifiedMethod.proto));
        }
      }
      DexMethod method = key.get();
      assert method != null;
      MethodNamingState originState =
          new MethodNamingState(originStates.get(key), method.name, method.proto);
      assignNameForInterfaceMethodInAllStates(collectedStates, sourceMethods, originState);
    }
    for (Entry<DexCallSite, DexMethod> entry : callSites.entrySet()) {
      DexMethod method = entry.getValue();
      DexString renamed = minifierState.getRenaming(method);
      if (originStates.get(equivalence.wrap(method)).isReserved(method.name, method.proto)) {
        assert renamed == null;
        callSiteRenamings.put(entry.getKey(), method.name);
      } else {
        assert renamed != null;
        callSiteRenamings.put(entry.getKey(), renamed);
      }
    }
    timing.end();
  }

  private void assignNameForInterfaceMethodInAllStates(
      List<MethodNamingState> collectedStates,
      Set<DexMethod> sourceMethods,
      MethodNamingState originState) {
    if (anyIsReserved(collectedStates)) {
      // This method's name is reserved in at least one naming state, so reserve it everywhere.
      for (MethodNamingState state : collectedStates) {
        state.reserveName();
      }
      return;
    }
    // We use the origin state to allocate a name here so that we can reuse names between different
    // unrelated interfaces. This saves some space. The alternative would be to use a global state
    // for allocating names, which would save the work to search here.
    DexString previousCandidate = null;
    DexString candidate;
    do {
      candidate = originState.assignNewName();
      // If the state returns the same candidate for two consecutive trials, it should be this case:
      //   1) an interface method with the same signature (name, param) but different return type
      //   has been already renamed; and 2) -useuniqueclassmembernames is set.
      // The option forces the naming state to return the same renamed name for the same signature.
      // So, here we break the loop in an ad-hoc manner.
      if (candidate != null && candidate == previousCandidate) {
        assert minifierState.useUniqueMemberNames();
        break;
      }
      for (MethodNamingState state : collectedStates) {
        if (!state.isAvailable(candidate)) {
          previousCandidate = candidate;
          candidate = null;
          break;
        }
      }
    } while (candidate == null);
    for (MethodNamingState state : collectedStates) {
      state.addRenaming(candidate);
    }
    // Rename all methods in interfaces that gave rise to this renaming.
    for (DexMethod sourceMethod : sourceMethods) {
      minifierState.putRenaming(sourceMethod, candidate);
    }
  }

  private void addStatesToGlobalMapForMethod(
      DexEncodedMethod method,
      Set<NamingState<DexProto, ?>> collectedStates,
      DexType originInterface) {
    Wrapper<DexMethod> key = equivalence.wrap(method.method);
    Set<NamingState<DexProto, ?>> stateSet =
        globalStateMap.computeIfAbsent(key, k -> new HashSet<>());
    stateSet.addAll(collectedStates);
    sourceMethodsMap.computeIfAbsent(key, k -> new HashSet<>()).add(method.method);
    originStates.putIfAbsent(key, minifierState.getState(originInterface));
  }

  private boolean anyIsReserved(List<MethodNamingState> collectedStates) {
    DexString name = collectedStates.get(0).getName();
    Map<DexProto, Boolean> globalStateCache = new IdentityHashMap<>();
    for (MethodNamingState state : collectedStates) {
      assert state.getName() == name;
      boolean isReservedInGlobalState =
          globalStateCache.computeIfAbsent(
              state.getProto(), proto -> minifierState.isReservedInGlobalState(name, proto));
      // TODO(christofferqa): Should this be using logical OR instead?
      if (isReservedInGlobalState && state.isReserved()) {
        return true;
      }
    }
    return false;
  }

  private Set<NamingState<DexProto, ?>> getReachableStates(DexType type) {
    Set<DexType> interfaces = Sets.newIdentityHashSet();
    interfaces.add(type);
    collectSuperInterfaces(type, interfaces);
    collectSubInterfaces(type, interfaces);
    Set<NamingState<DexProto, ?>> reachableStates = new HashSet<>();
    for (DexType iface : interfaces) {
      // Add the interface itself
      reachableStates.add(minifierState.getState(iface));
      // And the frontiers that correspond to the classes that implement the interface.
      for (DexType t : iface.allImplementsSubtypes()) {
        NamingState<DexProto, ?> state = minifierState.getState(frontierState.get(t));
        assert state != null;
        reachableStates.add(state);
      }
    }
    return reachableStates;
  }

  private void collectSuperInterfaces(DexType iface, Set<DexType> interfaces) {
    DexClass clazz = appInfo.definitionFor(iface);
    // In cases where we lack the interface's definition, we can at least look at subtypes and
    // tie those up to get proper naming.
    if (clazz != null) {
      for (DexType type : clazz.interfaces.values) {
        if (interfaces.add(type)) {
          collectSuperInterfaces(type, interfaces);
        }
      }
    }
  }

  private void collectSubInterfaces(DexType iface, Set<DexType> interfaces) {
    for (DexType subtype : iface.allExtendsSubtypes()) {
      assert subtype.isInterface();
      if (interfaces.add(subtype)) {
        collectSubInterfaces(subtype, interfaces);
      }
    }
  }
}
