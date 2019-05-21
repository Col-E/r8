// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.naming.MethodNameMinifier.shuffleMethods;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.MethodNameMinifier.FrontierState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Sets;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class InterfaceMethodNameMinifier {

  /**
   * Capture a (name, proto)-pair for a {@link MethodNamingState}. Each method
   * methodState.METHOD(...) simply defers to parent.METHOD(name, proto, ...). This allows R8 to
   * assign the same name to methods with different prototypes, which is needed for multi-interface
   * lambdas.
   */
  static class InterfaceMethodNamingState {

    private final MethodNamingState<?> parent;
    private final DexString name;
    private final DexProto proto;
    private final DexMethod method;

    InterfaceMethodNamingState(
        MethodNamingState<?> parent, DexMethod method, DexString name, DexProto proto) {
      this.method = method;
      assert parent != null;
      this.parent = parent;
      this.name = name;
      this.proto = proto;
    }

    DexString assignNewName() {
      return parent.assignNewNameFor(method, name, proto);
    }

    boolean isReserved() {
      return parent.isReserved(name, proto);
    }

    boolean isAvailable(DexString candidate) {
      return parent.isAvailable(proto, candidate);
    }

    void addRenaming(DexString newName) {
      parent.addRenaming(name, proto, newName);
    }

    DexString getName() {
      return name;
    }

    DexProto getProto() {
      return proto;
    }

    void print(
        String indentation,
        Function<MethodNamingState<?>, DexType> stateKeyGetter,
        PrintStream out) {
      DexType stateKey = stateKeyGetter.apply(parent);
      out.print(indentation);
      out.print(stateKey != null ? stateKey.toSourceString() : "<?>");
      out.print(".");
      out.print(name.toSourceString());
      out.println(proto.toSmaliString());
      parent.printState(proto, stateKeyGetter, indentation + "  ", out);
    }
  }

  private final AppView<AppInfoWithLiveness> appView;
  private final Set<DexCallSite> desugaredCallSites;
  private final Equivalence<DexMethod> equivalence;
  private final FrontierState frontierState;
  private final MethodNameMinifier.State minifierState;

  private final Map<DexCallSite, DexString> callSiteRenamings = new IdentityHashMap<>();

  /** A map from DexMethods to all the states linked to interfaces they appear in. */
  private final Map<Wrapper<DexMethod>, Set<MethodNamingState<?>>> globalStateMap = new HashMap<>();

  /** A map from DexMethods to the first interface state it was seen in. Used to pick good names. */
  private final Map<Wrapper<DexMethod>, MethodNamingState<?>> originStates = new HashMap<>();

  /**
   * A map from DexMethods to all the definitions seen. Needed as the Wrapper equalizes them all.
   */
  private final Map<Wrapper<DexMethod>, Set<DexMethod>> sourceMethodsMap = new HashMap<>();

  InterfaceMethodNameMinifier(
      AppView<AppInfoWithLiveness> appView,
      Set<DexCallSite> desugaredCallSites,
      Equivalence<DexMethod> equivalence,
      FrontierState frontierState,
      MethodNameMinifier.State minifierState) {
    this.appView = appView;
    this.desugaredCallSites = desugaredCallSites;
    this.equivalence = equivalence;
    this.frontierState = frontierState;
    this.minifierState = minifierState;
  }

  public Comparator<Wrapper<DexMethod>> createDefaultInterfaceMethodOrdering() {
    return (a, b) -> globalStateMap.get(b).size() - globalStateMap.get(a).size();
  }

  Map<DexCallSite, DexString> getCallSiteRenamings() {
    return callSiteRenamings;
  }

  private void reserveNamesInInterfaces(Collection<DexClass> interfaces) {
    for (DexClass iface : interfaces) {
      assert iface.isInterface();
      frontierState.allocateNamingStateAndReserve(iface.type, iface.type, null);
    }
  }

  void assignNamesToInterfaceMethods(Timing timing, Collection<DexClass> interfaces) {
    // Reserve all the names that are required for interfaces.
    reserveNamesInInterfaces(interfaces);

    // First compute a map from method signatures to a set of naming states for interfaces and
    // frontier states of classes that implement them. We add the frontier states so that we can
    // reserve the names for later method naming.
    timing.begin("Compute map");
    Set<DexType> allInterfaceTypes = new HashSet<>(interfaces.size());
    for (DexClass iface : interfaces) {
      allInterfaceTypes.add(iface.type);
    }
    for (DexClass iface : interfaces) {
      Set<MethodNamingState<?>> collectedStates = getReachableStates(iface.type, allInterfaceTypes);
      for (DexEncodedMethod method : shuffleMethods(iface.methods(), appView.options())) {
        addStatesToGlobalMapForMethod(method.method, collectedStates, iface.type);
      }
    }

    // Collect the live call sites for multi-interface lambda expression renaming. For code with
    // desugared lambdas this is a conservative estimate, as we don't track if the generated
    // lambda classes survive into the output. As multi-interface lambda expressions are rare
    // this is not a big deal.
    Set<DexCallSite> liveCallSites = Sets.union(desugaredCallSites, appView.appInfo().callSites);
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
              appView.appInfo().lookupLambdaImplementedMethods(callSite);
          if (implementedMethods.isEmpty()) {
            return;
          }
          callSites.put(callSite, implementedMethods.iterator().next().method);
          for (DexEncodedMethod method : implementedMethods) {
            DexType iface = method.method.holder;
            Set<MethodNamingState<?>> collectedStates =
                getReachableStates(iface, allInterfaceTypes);
            addStatesToGlobalMapForMethod(method.method, collectedStates, iface);
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

    timing.begin("sort");
    // Sort the methods by the number of dependent states, so that we use short names for methods
    // that are referenced in many places.
    List<Wrapper<DexMethod>> interfaceMethods =
        globalStateMap.keySet().stream()
            .filter(wrapper -> unificationParent.getOrDefault(wrapper, wrapper).equals(wrapper))
            .sorted(appView.options().testing.minifier.createInterfaceMethodOrdering(this))
            .collect(Collectors.toList());
    timing.end();

    timing.begin("propogate");
    // Propagate reserved names to all states.
    List<Wrapper<DexMethod>> reservedInterfaceMethods =
        interfaceMethods.stream()
            .filter(wrapper -> anyIsReserved(wrapper, unification))
            .collect(Collectors.toList());
    for (Wrapper<DexMethod> key : reservedInterfaceMethods) {
      propagateReservedNames(key, unification);
    }
    timing.end();

    timing.begin("assert");
    // Verify that there is no more to propagate.
    assert reservedInterfaceMethods.stream()
        .noneMatch(key -> propagateReservedNames(key, unification));
    timing.end();

    timing.begin("assing interface");
    // Assign names to unreserved interface methods.
    for (Wrapper<DexMethod> key : interfaceMethods) {
      if (!reservedInterfaceMethods.contains(key)) {
        assignNameToInterfaceMethod(key, unification);
      }
    }
    timing.end();

    timing.begin("callsites");
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
    timing.end(); // end compute map timing
  }

  private boolean propagateReservedNames(
      Wrapper<DexMethod> key, Map<Wrapper<DexMethod>, Set<Wrapper<DexMethod>>> unification) {
    Set<Wrapper<DexMethod>> unifiedMethods =
        unification.getOrDefault(key, Collections.singleton(key));
    boolean changed = false;
    for (Wrapper<DexMethod> wrapper : unifiedMethods) {
      DexMethod unifiedMethod = wrapper.get();
      assert unifiedMethod != null;
      assert globalStateMap.containsKey(wrapper)
          : "Expected globalStateMap to contain " + unifiedMethod.toSourceStringWithoutHolder();
      assert globalStateMap.get(wrapper) != null
          : "Expected globalStateMap to map "
              + unifiedMethod.toSourceStringWithoutHolder()
              + " to a non-null MethodNamingState.";

      for (MethodNamingState<?> namingState : globalStateMap.get(wrapper)) {
        if (!namingState.isReserved(unifiedMethod.name, unifiedMethod.proto)) {
          namingState.reserveName(unifiedMethod.name, unifiedMethod.proto, unifiedMethod.name);
          changed = true;
        }
      }
    }
    return changed;
  }

  private void assignNameToInterfaceMethod(
      Wrapper<DexMethod> key, Map<Wrapper<DexMethod>, Set<Wrapper<DexMethod>>> unification) {
    List<InterfaceMethodNamingState> collectedStates = new ArrayList<>();
    Set<DexMethod> sourceMethods = Sets.newIdentityHashSet();
    for (Wrapper<DexMethod> k : unification.getOrDefault(key, Collections.singleton(key))) {
      DexMethod unifiedMethod = k.get();
      assert unifiedMethod != null;
      sourceMethods.addAll(sourceMethodsMap.get(k));
      for (MethodNamingState<?> namingState : globalStateMap.get(k)) {
        collectedStates.add(
            new InterfaceMethodNamingState(
                namingState, unifiedMethod, unifiedMethod.name, unifiedMethod.proto));
      }
    }

    DexMethod method = key.get();
    assert method != null;

    Set<String> loggingFilter = appView.options().extensiveInterfaceMethodMinifierLoggingFilter;
    if (!loggingFilter.isEmpty()) {
      if (sourceMethods.stream().map(DexMethod::toSourceString).anyMatch(loggingFilter::contains)) {
        print(method, sourceMethods, collectedStates, System.out);
      }
    }

    InterfaceMethodNamingState originState =
        new InterfaceMethodNamingState(originStates.get(key), method, method.name, method.proto);
    assignNameForInterfaceMethodInAllStates(collectedStates, sourceMethods, originState);
  }

  private void assignNameForInterfaceMethodInAllStates(
      List<InterfaceMethodNamingState> collectedStates,
      Set<DexMethod> sourceMethods,
      InterfaceMethodNamingState originState) {
    assert !anyIsReserved(collectedStates);

    // We use the origin state to allocate a name here so that we can reuse names between different
    // unrelated interfaces. This saves some space. The alternative would be to use a global state
    // for allocating names, which would save the work to search here.
    DexString candidate;
    do {
      candidate = originState.assignNewName();
      for (InterfaceMethodNamingState state : collectedStates) {
        if (!state.isAvailable(candidate)) {
          candidate = null;
          break;
        }
      }
    } while (candidate == null);
    for (InterfaceMethodNamingState state : collectedStates) {
      state.addRenaming(candidate);
    }
    // Rename all methods in interfaces that gave rise to this renaming.
    for (DexMethod sourceMethod : sourceMethods) {
      minifierState.putRenaming(sourceMethod, candidate);
    }
  }

  private void addStatesToGlobalMapForMethod(
      DexMethod method, Set<MethodNamingState<?>> collectedStates, DexType originInterface) {
    assert collectedStates.stream().noneMatch(Objects::isNull);
    Wrapper<DexMethod> key = equivalence.wrap(method);
    globalStateMap.computeIfAbsent(key, k -> new HashSet<>()).addAll(collectedStates);
    sourceMethodsMap.computeIfAbsent(key, k -> new HashSet<>()).add(method);
    originStates.putIfAbsent(key, minifierState.getState(originInterface));
  }

  private boolean anyIsReserved(
      Wrapper<DexMethod> key, Map<Wrapper<DexMethod>, Set<Wrapper<DexMethod>>> unification) {
    Set<Wrapper<DexMethod>> unifiedMethods =
        unification.getOrDefault(key, Collections.singleton(key));
    for (Wrapper<DexMethod> wrapper : unifiedMethods) {
      DexMethod unifiedMethod = wrapper.get();
      assert unifiedMethod != null;
      assert globalStateMap.containsKey(wrapper)
          : "Expected globalStateMap to contain " + unifiedMethod.toSourceStringWithoutHolder();
      assert globalStateMap.get(wrapper) != null
          : "Expected globalStateMap to map "
              + unifiedMethod.toSourceStringWithoutHolder()
              + " to a non-null MethodNamingState.";

      for (MethodNamingState<?> namingState : globalStateMap.get(wrapper)) {
        if (namingState.isReserved(unifiedMethod.name, unifiedMethod.proto)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean anyIsReserved(List<InterfaceMethodNamingState> collectedStates) {
    DexString name = collectedStates.get(0).getName();
    Map<DexProto, Boolean> globalStateCache = new IdentityHashMap<>();
    for (InterfaceMethodNamingState state : collectedStates) {
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

  private Set<MethodNamingState<?>> getReachableStates(DexType type, Set<DexType> allInterfaces) {
    Set<DexType> reachableInterfaces = getReachableInterfaces(type, allInterfaces);
    Set<MethodNamingState<?>> reachableStates = new HashSet<>();
    for (DexType reachableInterface : reachableInterfaces) {
      // Add the interface itself.
      MethodNamingState<?> ifaceState = minifierState.getState(reachableInterface);
      assert ifaceState != null;
      reachableStates.add(ifaceState);

      // And the frontiers that correspond to the classes that implement the interface.
      for (DexType frontier : appView.appInfo().allImplementsSubtypes(reachableInterface)) {
        MethodNamingState<?> state = minifierState.getState(frontierState.get(frontier));
        assert state != null;
        reachableStates.add(state);
      }
    }
    return reachableStates;
  }

  private Set<DexType> getReachableInterfaces(DexType type, Set<DexType> allInterfaces) {
    if (!allInterfaces.contains(type)) {
      return Collections.emptySet();
    }
    Set<DexType> reachableInterfaces = Sets.newIdentityHashSet();
    reachableInterfaces.add(type);
    collectSuperInterfaces(type, reachableInterfaces, allInterfaces);
    collectSubInterfaces(type, reachableInterfaces, allInterfaces);
    return reachableInterfaces;
  }

  private void collectSuperInterfaces(
      DexType iface, Set<DexType> reachable, Set<DexType> allInterfaces) {
    DexClass clazz = appView.definitionFor(iface);
    if (clazz != null) {
      for (DexType type : clazz.interfaces.values) {
        if (allInterfaces.contains(type) && reachable.add(type)) {
          collectSuperInterfaces(type, reachable, allInterfaces);
        }
      }
    }
  }

  private void collectSubInterfaces(
      DexType iface, Set<DexType> reachableInterfaces, Set<DexType> allInterfaces) {
    // In cases where we lack the interface's definition, we can at least look at subtypes and
    // tie those up to get proper naming.
    for (DexType subtype : appView.appInfo().allExtendsSubtypes(iface)) {
      if (allInterfaces.contains(subtype) && reachableInterfaces.add(subtype)) {
        collectSubInterfaces(subtype, reachableInterfaces, allInterfaces);
      }
    }
  }

  private void print(
      DexMethod method,
      Set<DexMethod> sourceMethods,
      List<InterfaceMethodNamingState> collectedStates,
      PrintStream out) {
    out.println("-----------------------------------------------------------------------");
    out.println("assignNameToInterfaceMethod(`" + method.toSourceString() + "`)");
    out.println("-----------------------------------------------------------------------");
    out.println("Source methods:");
    for (DexMethod sourceMethod : sourceMethods) {
      out.println("  " + sourceMethod.toSourceString());
    }
    out.println("States:");
    collectedStates.forEach(state -> state.print("  ", minifierState::getStateKey, out));
    out.println();
  }
}
