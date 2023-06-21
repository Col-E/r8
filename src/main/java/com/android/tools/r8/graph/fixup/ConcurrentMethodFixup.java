// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.fixup;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.argumentpropagation.utils.ProgramClassesBidirectedGraph;
import com.android.tools.r8.optimize.utils.ConcurrentNonProgramMethodsCollection;
import com.android.tools.r8.optimize.utils.NonProgramMethodsCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.DexMethodSignatureBiMap;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class ConcurrentMethodFixup {

  private final AppView<AppInfoWithLiveness> appView;
  private final NonProgramMethodsCollection nonProgramVirtualMethods;
  private final ProgramClassFixer programClassFixer;

  public ConcurrentMethodFixup(
      AppView<AppInfoWithLiveness> appView, ProgramClassFixer programClassFixer) {
    this.appView = appView;
    this.nonProgramVirtualMethods =
        ConcurrentNonProgramMethodsCollection.createVirtualMethodsCollection(appView);
    this.programClassFixer = programClassFixer;
  }

  public void fixupClassesConcurrentlyByConnectedProgramComponents(
      Timing timing, ExecutorService executorService) throws ExecutionException {
    timing.begin("Concurrent method fixup");
    timing.begin("Compute strongly connected components");
    ImmediateProgramSubtypingInfo immediateSubtypingInfo =
        ImmediateProgramSubtypingInfo.create(appView);
    List<Set<DexProgramClass>> connectedComponents =
        new ProgramClassesBidirectedGraph(appView, immediateSubtypingInfo)
            .computeStronglyConnectedComponents();
    timing.end();

    timing.begin("Process strongly connected components");
    ThreadUtils.processItems(
        connectedComponents, this::processConnectedProgramComponents, executorService);
    timing.end();
    timing.end();
  }

  public interface ProgramClassFixer {
    // When a class is fixed-up, it is guaranteed that its supertype and interfaces were processed
    // before. In addition, all interfaces are processed before any class is processed.
    void fixupProgramClass(DexProgramClass clazz, MethodNamingUtility namingUtility);

    // Answers true if the method should be reserved as itself.
    boolean shouldReserveAsIfPinned(ProgramMethod method);
  }

  private void processConnectedProgramComponents(Set<DexProgramClass> classes) {
    List<DexProgramClass> sorted = new ArrayList<>(classes);
    sorted.sort(Comparator.comparing(DexClass::getType));
    DexMethodSignatureBiMap<DexMethodSignature> componentSignatures =
        new DexMethodSignatureBiMap<>();

    // 1) Reserve all library overrides and pinned virtual methods.
    reserveComponentPinnedAndInterfaceMethodSignatures(sorted, componentSignatures);

    // 2) Map all interfaces top-down updating the componentSignatures.
    Set<DexProgramClass> processedInterfaces = Sets.newIdentityHashSet();
    for (DexProgramClass clazz : sorted) {
      if (clazz.isInterface()) {
        processInterface(clazz, processedInterfaces, componentSignatures);
      }
    }

    // 3) Map all classes top-down propagating the inherited signatures.
    // The componentSignatures are already fully computed and should not be updated anymore.
    // TODO(b/279707790): Consider changing the processing to have a different componentSignatures
    //  per subtree.
    Set<DexProgramClass> processedClasses = Sets.newIdentityHashSet();
    for (DexProgramClass clazz : sorted) {
      if (!clazz.isInterface()) {
        processClass(clazz, processedClasses, componentSignatures);
      }
    }
  }

  private void processClass(
      DexProgramClass clazz,
      Set<DexProgramClass> processedClasses,
      DexMethodSignatureBiMap<DexMethodSignature> componentSignatures) {
    assert !clazz.isInterface();
    if (!processedClasses.add(clazz)) {
      return;
    }
    // We need to process first the super-type for the top-down propagation of inherited signatures.
    DexProgramClass superClass = asProgramClassOrNull(appView.definitionFor(clazz.superType));
    if (superClass != null) {
      processClass(superClass, processedClasses, componentSignatures);
    }
    MethodNamingUtility utility = createMethodNamingUtility(componentSignatures, clazz);
    programClassFixer.fixupProgramClass(clazz, utility);
  }

  private void processInterface(
      DexProgramClass clazz,
      Set<DexProgramClass> processedInterfaces,
      DexMethodSignatureBiMap<DexMethodSignature> componentSignatures) {
    assert clazz.isInterface();
    if (!processedInterfaces.add(clazz)) {
      return;
    }
    // We need to process first all super-interfaces to avoid generating collisions by renaming
    // private or static methods into inherited virtual method signatures.
    for (DexType superInterface : clazz.getInterfaces()) {
      DexProgramClass superInterfaceClass =
          asProgramClassOrNull(appView.definitionFor(superInterface));
      if (superInterfaceClass != null) {
        processInterface(superInterfaceClass, processedInterfaces, componentSignatures);
      }
    }
    MethodNamingUtility utility = createMethodNamingUtility(componentSignatures, clazz);
    programClassFixer.fixupProgramClass(clazz, utility);
  }

  private boolean shouldReserveAsPinned(ProgramMethod method) {
    KeepMethodInfo keepInfo = appView.getKeepInfo(method);
    return !keepInfo.isOptimizationAllowed(appView.options())
        || !keepInfo.isShrinkingAllowed(appView.options())
        || programClassFixer.shouldReserveAsIfPinned(method);
  }

  private MethodNamingUtility createMethodNamingUtility(
      DexMethodSignatureBiMap<DexMethodSignature> componentSignatures, DexProgramClass clazz) {
    BiMap<DexMethod, DexMethod> localSignatures = HashBiMap.create();
    clazz.forEachProgramInstanceInitializer(
        method -> {
          if (shouldReserveAsPinned(method)) {
            localSignatures.put(method.getReference(), method.getReference());
          }
        });
    return new MethodNamingUtility(appView.dexItemFactory(), componentSignatures, localSignatures);
  }

  private void reserveComponentPinnedAndInterfaceMethodSignatures(
      List<DexProgramClass> stronglyConnectedProgramClasses,
      DexMethodSignatureBiMap<DexMethodSignature> componentSignatures) {
    Set<ClasspathOrLibraryClass> seenNonProgramClasses = Sets.newIdentityHashSet();
    for (DexProgramClass clazz : stronglyConnectedProgramClasses) {
      // If a private or static method is pinned, we need to reserve the mapping to avoid creating
      // a collision with a changed virtual method.
      clazz.forEachProgramMethodMatching(
          m -> !m.isInstanceInitializer(),
          method -> {
            if (shouldReserveAsPinned(method)) {
              componentSignatures.put(method.getMethodSignature(), method.getMethodSignature());
            }
          });
      clazz.forEachImmediateSuperClassMatching(
          appView,
          (supertype, superclass) ->
              superclass != null
                  && !superclass.isProgramClass()
                  && seenNonProgramClasses.add(superclass.asClasspathOrLibraryClass()),
          (supertype, superclass) ->
              componentSignatures.putAllToIdentity(
                  nonProgramVirtualMethods.getOrComputeNonProgramMethods(
                      superclass.asClasspathOrLibraryClass())));
    }
  }
}
