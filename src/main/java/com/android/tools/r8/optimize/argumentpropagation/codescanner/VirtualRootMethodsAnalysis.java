// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagatorCodeScanner;
import com.android.tools.r8.optimize.argumentpropagation.utils.DepthFirstTopDownClassHierarchyTraversal;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class VirtualRootMethodsAnalysis extends DepthFirstTopDownClassHierarchyTraversal {

  static class VirtualRootMethod {

    private final ProgramMethod root;
    private final ProgramMethodSet overrides = ProgramMethodSet.create();

    VirtualRootMethod(ProgramMethod root) {
      this.root = root;
    }

    void addOverride(ProgramMethod override) {
      assert override != root;
      assert override.getMethodSignature().equals(root.getMethodSignature());
      overrides.add(override);
    }

    ProgramMethod getRoot() {
      return root;
    }

    void forEach(Consumer<ProgramMethod> consumer) {
      consumer.accept(root);
      overrides.forEach(consumer);
    }

    boolean hasOverrides() {
      return !overrides.isEmpty();
    }
  }

  private final Map<DexProgramClass, Map<DexMethodSignature, VirtualRootMethod>>
      virtualRootMethodsPerClass = new IdentityHashMap<>();

  private final Set<DexMethod> monomorphicVirtualMethods = Sets.newIdentityHashSet();

  private final Map<DexMethod, DexMethod> virtualRootMethods = new IdentityHashMap<>();

  public VirtualRootMethodsAnalysis(
      AppView<AppInfoWithLiveness> appView, ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    super(appView, immediateSubtypingInfo);
  }

  public void extendVirtualRootMethods(
      Collection<DexProgramClass> stronglyConnectedComponent,
      ArgumentPropagatorCodeScanner codeScanner) {
    // Find all the virtual root methods in the strongly connected component.
    run(stronglyConnectedComponent);

    // Commit the result to the code scanner.
    codeScanner.addMonomorphicVirtualMethods(monomorphicVirtualMethods);
    codeScanner.addVirtualRootMethods(virtualRootMethods);
  }

  @Override
  public void visit(DexProgramClass clazz) {
    Map<DexMethodSignature, VirtualRootMethod> state = computeVirtualRootMethodsState(clazz);
    virtualRootMethodsPerClass.put(clazz, state);
  }

  private Map<DexMethodSignature, VirtualRootMethod> computeVirtualRootMethodsState(
      DexProgramClass clazz) {
    Map<DexMethodSignature, VirtualRootMethod> virtualRootMethodsForClass = new HashMap<>();
    immediateSubtypingInfo.forEachImmediateProgramSuperClass(
        clazz,
        superclass -> {
          Map<DexMethodSignature, VirtualRootMethod> virtualRootMethodsForSuperclass =
              virtualRootMethodsPerClass.get(superclass);
          virtualRootMethodsForSuperclass.forEach(
              (signature, info) ->
                  virtualRootMethodsForClass.computeIfAbsent(signature, ignoreKey(() -> info)));
        });
    clazz.forEachProgramVirtualMethod(
        method -> {
          DexMethodSignature signature = method.getMethodSignature();
          if (virtualRootMethodsForClass.containsKey(signature)) {
            virtualRootMethodsForClass.get(signature).addOverride(method);
          } else {
            virtualRootMethodsForClass.put(signature, new VirtualRootMethod(method));
          }
        });
    return virtualRootMethodsForClass;
  }

  @Override
  public void prune(DexProgramClass clazz) {
    // Record the overrides for each virtual method that is rooted at this class.
    Map<DexMethodSignature, VirtualRootMethod> virtualRootMethodsForClass =
        virtualRootMethodsPerClass.remove(clazz);
    clazz.forEachProgramVirtualMethod(
        rootCandidate -> {
          VirtualRootMethod virtualRootMethod =
              virtualRootMethodsForClass.remove(rootCandidate.getMethodSignature());
          if (!rootCandidate.isStructurallyEqualTo(virtualRootMethod.getRoot())) {
            return;
          }
          boolean isMonomorphicVirtualMethod =
              !clazz.isInterface() && !virtualRootMethod.hasOverrides();
          if (isMonomorphicVirtualMethod) {
            monomorphicVirtualMethods.add(rootCandidate.getReference());
          } else {
            virtualRootMethod.forEach(
                method ->
                    virtualRootMethods.put(method.getReference(), rootCandidate.getReference()));
          }
        });
  }
}
