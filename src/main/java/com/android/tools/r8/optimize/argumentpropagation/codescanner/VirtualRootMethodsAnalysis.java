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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Computes the set of virtual methods for which we can use a monomorphic method state as well as
 * the mapping from virtual methods to their representative root methods.
 *
 * <p>The analysis can be used to easily mark effectively final classes and methods as final, and
 * therefore does this as a side effect.
 */
public class VirtualRootMethodsAnalysis extends DepthFirstTopDownClassHierarchyTraversal {

  static class VirtualRootMethod {

    private final VirtualRootMethod parent;
    private final ProgramMethod root;
    private final ProgramMethodSet overrides = ProgramMethodSet.create();

    VirtualRootMethod(ProgramMethod root) {
      this(root, null);
    }

    VirtualRootMethod(ProgramMethod root, VirtualRootMethod parent) {
      this.parent = parent;
      this.root = root;
    }

    @SuppressWarnings("ReferenceEquality")
    void addOverride(ProgramMethod override) {
      assert override != root;
      assert override.getMethodSignature().equals(root.getMethodSignature());
      overrides.add(override);
      if (hasParent()) {
        getParent().addOverride(override);
      }
    }

    boolean hasParent() {
      return parent != null;
    }

    VirtualRootMethod getParent() {
      return parent;
    }

    ProgramMethod getRoot() {
      return root;
    }

    ProgramMethod getSingleNonAbstractMethod() {
      ProgramMethod singleNonAbstractMethod = root.getAccessFlags().isAbstract() ? null : root;
      for (ProgramMethod override : overrides) {
        if (!override.getAccessFlags().isAbstract()) {
          if (singleNonAbstractMethod != null) {
            // Not a single non-abstract method.
            return null;
          }
          singleNonAbstractMethod = override;
        }
      }
      assert singleNonAbstractMethod == null
          || !singleNonAbstractMethod.getAccessFlags().isAbstract();
      return singleNonAbstractMethod;
    }

    void forEach(Consumer<ProgramMethod> consumer) {
      consumer.accept(root);
      overrides.forEach(consumer);
    }

    boolean hasOverrides() {
      return !overrides.isEmpty();
    }

    boolean isInterfaceMethodWithSiblings() {
      // TODO(b/190154391): Conservatively returns true for all interface methods, but should only
      //  return true for those with siblings.
      return root.getHolder().isInterface();
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

  public void initializeVirtualRootMethods(
      Collection<DexProgramClass> stronglyConnectedComponent,
      ArgumentPropagatorCodeScanner codeScanner) {
    // Find all the virtual root methods in the strongly connected component.
    run(stronglyConnectedComponent);

    // Commit the result to the code scanner.
    codeScanner.addMonomorphicVirtualMethods(monomorphicVirtualMethods);
    codeScanner.addVirtualRootMethods(virtualRootMethods);
  }

  @Override
  public void forEachSubClass(DexProgramClass clazz, Consumer<DexProgramClass> consumer) {
    List<DexProgramClass> subclasses = immediateSubtypingInfo.getSubclasses(clazz);
    if (subclasses.isEmpty()) {
      promoteToFinalIfPossible(clazz);
    } else {
      subclasses.forEach(consumer);
    }
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
                  virtualRootMethodsForClass.computeIfAbsent(
                      signature, ignoreKey(() -> new VirtualRootMethod(info.getRoot(), info))));
        });
    clazz.forEachProgramVirtualMethod(
        method -> {
          DexMethodSignature signature = method.getMethodSignature();
          if (virtualRootMethodsForClass.containsKey(signature)) {
            virtualRootMethodsForClass.get(signature).getParent().addOverride(method);
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
          promoteToFinalIfPossible(rootCandidate, virtualRootMethod);
          if (!rootCandidate.isStructurallyEqualTo(virtualRootMethod.getRoot())) {
            return;
          }
          boolean isMonomorphicVirtualMethod =
              !clazz.isInterface() && !virtualRootMethod.hasOverrides();
          if (isMonomorphicVirtualMethod) {
            monomorphicVirtualMethods.add(rootCandidate.getReference());
          } else {
            ProgramMethod singleNonAbstractMethod = virtualRootMethod.getSingleNonAbstractMethod();
            if (singleNonAbstractMethod != null
                && !virtualRootMethod.isInterfaceMethodWithSiblings()) {
              virtualRootMethod.forEach(
                  method -> {
                    // Interface methods can have siblings and can therefore not be mapped to their
                    // unique non-abstract implementation, unless the interface method does not have
                    // any siblings.
                    virtualRootMethods.put(
                        method.getReference(), singleNonAbstractMethod.getReference());
                  });
              if (!singleNonAbstractMethod.getHolder().isInterface()) {
                monomorphicVirtualMethods.add(singleNonAbstractMethod.getReference());
              }
            } else {
              virtualRootMethod.forEach(
                  method ->
                      virtualRootMethods.put(method.getReference(), rootCandidate.getReference()));
            }
          }
        });
  }

  private void promoteToFinalIfPossible(DexProgramClass clazz) {
    if (!appView.testing().disableMarkingClassesFinal
        && !clazz.isAbstract()
        && !clazz.isInterface()
        && appView.getKeepInfo(clazz).isOptimizationAllowed(appView.options())) {
      clazz.getAccessFlags().promoteToFinal();
    }
  }

  private void promoteToFinalIfPossible(ProgramMethod method, VirtualRootMethod virtualRootMethod) {
    if (!appView.testing().disableMarkingMethodsFinal
        && !method.getHolder().isInterface()
        && !method.getAccessFlags().isAbstract()
        && !virtualRootMethod.hasOverrides()
        && appView.getKeepInfo(method).isOptimizationAllowed(appView.options())) {
      method.getAccessFlags().promoteToFinal();
    }
  }
}
