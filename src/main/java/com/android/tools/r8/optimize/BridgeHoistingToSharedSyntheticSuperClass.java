// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import static com.android.tools.r8.ir.optimize.info.OptimizationFeedback.getSimpleFeedback;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.contexts.CompilationContext.MainThreadContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeAnalyzer;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.ir.optimize.info.bridge.VirtualBridgeInfo;
import com.android.tools.r8.optimize.bridgehoisting.BridgeHoisting;
import com.android.tools.r8.profile.rewriting.ConcreteProfileCollectionAdditions;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.TestingOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.DexMethodSignatureMap;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

public class BridgeHoistingToSharedSyntheticSuperClass {

  private final AppView<AppInfoWithLiveness> appView;

  BridgeHoistingToSharedSyntheticSuperClass(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public static void run(
      AppView<AppInfoWithLiveness> appView, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    InternalOptions options = appView.options();
    if (!options.isOptimizing() || !options.isShrinking()) {
      return;
    }
    if (!appView.options().canHaveNonReboundConstructorInvoke()) {
      // TODO(b/309575527): Extend to all runtimes.
      return;
    }
    TestingOptions testingOptions = options.getTestingOptions();
    if (!testingOptions.enableBridgeHoistingToSharedSyntheticSuperclass) {
      return;
    }
    timing.time(
        "BridgeHoistingToSharedSyntheticSuperClass",
        () ->
            new BridgeHoistingToSharedSyntheticSuperClass(appView)
                .internalRun(executorService, timing));
  }

  private void internalRun(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    Collection<Group> groups = createInitialGroups(appView);
    groups = refineGroups(groups);
    if (!groups.isEmpty()) {
      rewriteApplication(groups);
      commitPendingSyntheticClasses();
      updateArtProfiles(groups);
      new BridgeHoisting(appView).run(executorService, timing);
    }
  }

  /** Returns the set of (non-singleton) groups that have the same superclass. */
  private Collection<Group> createInitialGroups(AppView<AppInfoWithLiveness> appView) {
    Map<DexClass, Group> groups = new LinkedHashMap<>();
    for (DexProgramClass clazz : appView.appInfo().classesWithDeterministicOrder()) {
      if (!clazz.hasSuperType()) {
        continue;
      }
      DexClass superclass = appView.definitionFor(clazz.getSuperType());
      if (superclass != null) {
        groups.computeIfAbsent(superclass, ignoreKey(Group::new)).addClass(clazz);
      }
    }
    groups.values().removeIf(Group::isSingleton);
    return groups.values();
  }

  private Collection<Group> refineGroups(Collection<Group> groups) {
    Collection<Group> newGroups = new ArrayList<>();
    for (Group group : groups) {
      Iterables.addAll(newGroups, refineGroup(group));
    }
    return newGroups;
  }

  /**
   * Splits the group into a collection of smaller groups that should receive a shared superclass.
   *
   * <p>For each class, this creates a specification of the bridges (a mapping from bridge method
   * signatures to their bridge implementation). Two classes are selected for getting a shared
   * synthetic super class if the bridge specification of one is a subset of the other (i.e., a
   * subset of the bridges can be shared and there are no bridges with the same signature that have
   * different behavior).
   */
  private Iterable<Group> refineGroup(Group group) {
    List<Group> newGroups = new ArrayList<>();
    for (DexProgramClass clazz : group) {
      BridgeSpecification bridgeSpecification = getBridgeSpecification(clazz);
      if (bridgeSpecification.isEmpty()) {
        continue;
      }
      Group targetGroup = getGroupForClass(newGroups, clazz, bridgeSpecification);
      if (targetGroup == null) {
        newGroups.add(new Group(clazz, bridgeSpecification));
      }
    }
    // Only introduce a shared super class for non-singleton groups that do not already have a
    // shared superclass in the first place.
    return Iterables.filter(
        newGroups, newGroup -> !newGroup.isSingleton() && newGroup.size() < group.size());
  }

  // TODO(b/309575527): Avoid building IR for all methods.
  private BridgeSpecification getBridgeSpecification(DexProgramClass clazz) {
    BridgeSpecification bridgeSpecification = new BridgeSpecification();
    clazz.forEachProgramVirtualMethodMatching(
        DexEncodedMethod::hasCode,
        method -> {
          IRCode code = method.buildIR(appView, MethodConversionOptions.nonConverting());
          BridgeInfo bridgeInfo = BridgeAnalyzer.analyzeMethod(method.getDefinition(), code);
          if (bridgeInfo != null) {
            getSimpleFeedback().setBridgeInfo(method, bridgeInfo);
            if (bridgeInfo.isVirtualBridgeInfo()) {
              bridgeSpecification.addBridge(
                  method.getMethodSignature(), bridgeInfo.asVirtualBridgeInfo());
            }
          }
        });
    return bridgeSpecification;
  }

  private Group getGroupForClass(
      Collection<Group> groups, DexProgramClass clazz, BridgeSpecification bridgeSpecification) {
    for (Group group : groups) {
      if (bridgeSpecification.lessThanOrEquals(group.getBridgeSpecification())) {
        group.addClass(clazz);
        return group;
      } else if (group.getBridgeSpecification().lessThanOrEquals(bridgeSpecification)) {
        group.addClass(clazz);
        group.setBridgeSpecification(bridgeSpecification);
        return group;
      }
    }
    return null;
  }

  private void rewriteApplication(Collection<Group> groups) {
    MainThreadContext mainThreadContext =
        appView.createProcessorContext().createMainThreadContext();
    for (Group group : groups) {
      DexProgramClass representative = ListUtils.first(group.getClasses());
      Set<DexType> interfaces = SetUtils.newIdentityHashSet(representative.getInterfaces());
      for (DexProgramClass clazz : Iterables.skip(group.getClasses(), 1)) {
        interfaces.removeIf(type -> !clazz.getInterfaces().contains(type));
      }
      DexProgramClass syntheticSuperclass =
          appView
              .getSyntheticItems()
              .createClass(
                  kinds -> kinds.SHARED_SUPER_CLASS,
                  mainThreadContext.createUniqueContext(representative),
                  appView,
                  classBuilder -> {
                    classBuilder
                        .setAbstract()
                        .setSuperType(representative.getSuperType())
                        .setInterfaces(ListUtils.sort(interfaces, Comparator.naturalOrder()));
                    group
                        .getBridgeSpecification()
                        .forEach(
                            (bridge, target) ->
                                classBuilder.addMethod(
                                    methodBuilder ->
                                        methodBuilder
                                            .setAccessFlags(
                                                MethodAccessFlags.builder()
                                                    .setAbstract()
                                                    .setPublic()
                                                    .build())
                                            // TODO(b/309575527): Set correct api level.
                                            .setApiLevelForDefinition(appView.computedMinApiLevel())
                                            // TODO(b/309575527): Set correct library override info.
                                            .setIsLibraryMethodOverride(OptionalBool.FALSE)
                                            .setName(target.getName())
                                            .setProto(target.getProto())));
                  });
      for (DexProgramClass clazz : group) {
        clazz.setSuperType(syntheticSuperclass.getType());
      }
    }
  }

  private void commitPendingSyntheticClasses() {
    assert appView.getSyntheticItems().hasPendingSyntheticClasses();
    appView.setAppInfo(
        appView.appInfo().rebuildWithLiveness(appView.getSyntheticItems().commit(appView.app())));
  }

  private void updateArtProfiles(Collection<Group> groups) {
    ConcreteProfileCollectionAdditions profileCollectionAdditions =
        ProfileCollectionAdditions.create(appView).asConcrete();
    if (profileCollectionAdditions == null) {
      return;
    }
    for (Group group : groups) {
      for (DexProgramClass clazz : group) {
        profileCollectionAdditions.applyIfContextIsInProfile(
            clazz, additionsBuilder -> additionsBuilder.addClassRule(clazz.getSuperType()));
        group
            .getBridgeSpecification()
            .forEach(
                (bridge, target) -> {
                  DexEncodedMethod targetMethod = clazz.getMethodCollection().getMethod(target);
                  if (targetMethod != null) {
                    profileCollectionAdditions.applyIfContextIsInProfile(
                        targetMethod.getReference(),
                        additionsBuilder ->
                            additionsBuilder.addMethodRule(
                                target.withHolder(clazz.getSuperType(), appView.dexItemFactory())));
                  }
                });
      }
    }
    profileCollectionAdditions.commit(appView);
  }

  private static class Group implements Iterable<DexProgramClass> {

    private final List<DexProgramClass> classes;
    private BridgeSpecification bridgeSpecification;

    public Group() {
      this.classes = new ArrayList<>();
      this.bridgeSpecification = null;
    }

    public Group(DexProgramClass clazz, BridgeSpecification bridgeSpecification) {
      this.classes = ListUtils.newArrayList(clazz);
      this.bridgeSpecification = bridgeSpecification;
    }

    void addClass(DexProgramClass clazz) {
      classes.add(clazz);
    }

    BridgeSpecification getBridgeSpecification() {
      return bridgeSpecification;
    }

    List<DexProgramClass> getClasses() {
      return classes;
    }

    void setBridgeSpecification(BridgeSpecification bridgeSpecification) {
      this.bridgeSpecification = bridgeSpecification;
    }

    boolean isSingleton() {
      return size() == 1;
    }

    @Override
    public Iterator<DexProgramClass> iterator() {
      return classes.iterator();
    }

    public int size() {
      return classes.size();
    }
  }

  private static class BridgeSpecification {

    private final DexMethodSignatureMap<DexMethodSignature> bridges =
        DexMethodSignatureMap.create();

    void addBridge(DexMethodSignature method, VirtualBridgeInfo bridgeInfo) {
      bridges.put(method, bridgeInfo.getInvokedMethod().getSignature());
    }

    boolean containsBridgeWithTarget(DexMethodSignature method, DexMethodSignature target) {
      return target.equals(bridges.get(method));
    }

    void forEach(BiConsumer<? super DexMethodSignature, ? super DexMethodSignature> consumer) {
      bridges.forEach(consumer);
    }

    boolean isEmpty() {
      return bridges.isEmpty();
    }

    boolean lessThanOrEquals(BridgeSpecification bridgeSpecification) {
      if (size() > bridgeSpecification.size()) {
        return false;
      }
      for (Entry<DexMethodSignature, DexMethodSignature> entry : bridges.entrySet()) {
        DexMethodSignature method = entry.getKey();
        DexMethodSignature target = entry.getValue();
        if (!bridgeSpecification.containsBridgeWithTarget(method, target)) {
          return false;
        }
      }
      return true;
    }

    int size() {
      return bridges.size();
    }
  }
}
