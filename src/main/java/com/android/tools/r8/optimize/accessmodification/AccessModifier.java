// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.accessmodification;

import static com.android.tools.r8.dex.Constants.ACC_PRIVATE;
import static com.android.tools.r8.dex.Constants.ACC_PROTECTED;
import static com.android.tools.r8.dex.Constants.ACC_PUBLIC;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.accessmodification.AccessModifierTraversal.BottomUpTraversalState;
import com.android.tools.r8.optimize.argumentpropagation.utils.ProgramClassesBidirectedGraph;
import com.android.tools.r8.optimize.utils.ConcurrentNonProgramMethodsCollection;
import com.android.tools.r8.optimize.utils.NonProgramMethodsCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class AccessModifier {

  private final AppView<AppInfoWithLiveness> appView;
  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;
  private final AccessModifierLens.Builder lensBuilder = AccessModifierLens.builder();
  private final NonProgramMethodsCollection nonProgramMethodsCollection;
  private final InternalOptions options;

  private AccessModifier(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.immediateSubtypingInfo =
        ImmediateProgramSubtypingInfo.createWithDeterministicOrder(appView);
    this.nonProgramMethodsCollection =
        ConcurrentNonProgramMethodsCollection.createVirtualMethodsCollection(appView);
    this.options = appView.options();
  }

  public static void run(
      AppView<AppInfoWithLiveness> appView, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    timing.begin("Access modification");
    if (appView.options().getAccessModifierOptions().isAccessModificationEnabled()) {
      new AccessModifier(appView)
          .processStronglyConnectedComponents(executorService)
          .installLens(executorService, timing);
    }
    timing.end();
  }

  private AccessModifier processStronglyConnectedComponents(ExecutorService executorService)
      throws ExecutionException {
    // Compute the connected program classes and process the components in parallel.
    List<Set<DexProgramClass>> stronglyConnectedComponents =
        new ProgramClassesBidirectedGraph(appView, immediateSubtypingInfo)
            .computeStronglyConnectedComponents();
    ThreadUtils.processItems(
        stronglyConnectedComponents, this::processStronglyConnectedComponent, executorService);
    return this;
  }

  private void processStronglyConnectedComponent(Set<DexProgramClass> stronglyConnectedComponent) {
    // Perform a top-down traversal over the class hierarchy.
    new AccessModifierTraversal(
            appView,
            immediateSubtypingInfo,
            this,
            AccessModifierNamingState.createInitialNamingState(
                appView, stronglyConnectedComponent, nonProgramMethodsCollection))
        .run(ListUtils.sort(stronglyConnectedComponent, Comparator.comparing(DexClass::getType)));
  }

  private void installLens(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    if (!lensBuilder.isEmpty()) {
      appView.rewriteWithLens(lensBuilder.build(appView), executorService, timing);
    }
  }

  // Publicizing of classes and members.

  void processClass(
      DexProgramClass clazz,
      AccessModifierNamingState namingState,
      BottomUpTraversalState traversalState) {
    publicizeClass(clazz);
    publicizeFields(clazz);
    publicizeMethods(clazz, namingState, traversalState);
    // TODO(b/278736230): Also finalize classes and methods here.
    finalizeFields(clazz);
  }

  private void publicizeClass(DexProgramClass clazz) {
    if (isAccessModificationAllowed(clazz) && !clazz.getAccessFlags().isPublic()) {
      clazz.getAccessFlags().promoteToPublic();
    }

    // Update inner class attribute.
    // TODO(b/285494837): Carry-over from the legacy access modifier. We should never publicize
    //  items unconditionally, but account for keep info.
    InnerClassAttribute attr = clazz.getInnerClassAttributeForThisClass();
    if (attr != null) {
      int accessFlags = ((attr.getAccess() | ACC_PUBLIC) & ~ACC_PRIVATE) & ~ACC_PROTECTED;
      clazz.replaceInnerClassAttributeForThisClass(
          new InnerClassAttribute(
              accessFlags, attr.getInner(), attr.getOuter(), attr.getInnerName()));
    }
  }

  private void publicizeFields(DexProgramClass clazz) {
    clazz.forEachProgramField(this::publicizeField);
  }

  private void publicizeField(ProgramField field) {
    if (isAccessModificationAllowed(field) && !field.getAccessFlags().isPublic()) {
      field.getAccessFlags().promoteToPublic();
    }
  }

  private void publicizeMethods(
      DexProgramClass clazz,
      AccessModifierNamingState namingState,
      BottomUpTraversalState traversalState) {
    // Create a local naming state to keep track of the methods present on the current class.
    // Start by reserving the pinned method signatures on the current class.
    BiMap<DexMethod, DexMethod> localNamingState = HashBiMap.create();
    clazz.forEachProgramMethod(
        method -> {
          if (!method.getDefinition().isInitializer() && !isRenamingAllowed(method)) {
            localNamingState.put(method.getReference(), method.getReference());
          }
        });
    clazz
        .getMethodCollection()
        .<ProgramMethod>replaceClassAndMethods(
            method -> publicizeMethod(method, localNamingState, namingState, traversalState));
  }

  private DexEncodedMethod publicizeMethod(
      ProgramMethod method,
      BiMap<DexMethod, DexMethod> localNamingState,
      AccessModifierNamingState namingState,
      BottomUpTraversalState traversalState) {
    MethodAccessFlags accessFlags = method.getAccessFlags();
    if (accessFlags.isPublic() || !isAccessModificationAllowed(method)) {
      return commitMethod(method, localNamingState, namingState);
    }

    if (method.getDefinition().isInstanceInitializer()
        || (accessFlags.isPackagePrivate()
            && !traversalState.hasIllegalOverrideOfPackagePrivateMethod(method))
        || accessFlags.isProtected()) {
      method.getAccessFlags().promoteToPublic();
      return commitMethod(method, localNamingState, namingState);
    }

    if (accessFlags.isPrivate()) {
      if (isRenamingAllowed(method)) {
        method.getAccessFlags().promoteToPublic();
        return commitMethod(method, localNamingState, namingState);
      }
      assert localNamingState.containsKey(method.getReference());
      assert localNamingState.get(method.getReference()) == method.getReference();
      if (namingState.isFree(method.getMethodSignature())) {
        method.getAccessFlags().promoteToPublic();
        namingState.addBlockedMethodSignature(method.getMethodSignature());
      }
      return commitMethod(method, method.getReference());
    }

    // TODO(b/279126633): Add support for publicizing package-private methods by renaming.
    assert accessFlags.isPackagePrivate();
    assert traversalState.hasIllegalOverrideOfPackagePrivateMethod(method);
    return commitMethod(method, localNamingState, namingState);
  }

  private DexMethod getAndReserveNewMethodReference(
      ProgramMethod method,
      BiMap<DexMethod, DexMethod> localNamingState,
      AccessModifierNamingState namingState) {
    if (method.getDefinition().isInitializer()) {
      return method.getReference();
    }
    if (!isRenamingAllowed(method)) {
      assert localNamingState.containsKey(method.getReference());
      assert localNamingState.get(method.getReference()) == method.getReference();
      assert method.getAccessFlags().isPrivate()
          || method
              .getMethodSignature()
              .equals(namingState.getReservedSignature(method.getMethodSignature()));
      return method.getReference();
    }
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    if (method.getAccessFlags().isPrivate()) {
      // Find a fresh method name and reserve it for the current class.
      DexMethod newMethodReference =
          dexItemFactory.createFreshMethodNameWithoutHolder(
              method.getName().toString(),
              method.getProto(),
              method.getHolderType(),
              candidate ->
                  !localNamingState.containsValue(candidate)
                      && namingState.isFree(candidate.getSignature()));
      localNamingState.put(method.getReference(), newMethodReference);
      return newMethodReference;
    }
    // Check if a mapping already exists for this method signature.
    if (!method.getAccessFlags().isPromotedFromPrivateToPublic()) {
      DexMethodSignature reservedSignature =
          namingState.getReservedSignature(method.getMethodSignature());
      if (reservedSignature != null) {
        return reservedSignature.withHolder(method, appView.dexItemFactory());
      }
    }
    // Find a fresh method name and block/reserve it globally.
    DexMethod newMethodReference =
        dexItemFactory.createFreshMethodNameWithoutHolder(
            method.getName().toString(),
            method.getProto(),
            method.getHolderType(),
            candidate ->
                !localNamingState.containsValue(candidate)
                    && namingState.isFree(candidate.getSignature()));
    if (method.getAccessFlags().belongsToVirtualPool()) {
      if (method.getAccessFlags().isPromotedFromPrivateToPublic()) {
        namingState.addBlockedMethodSignature(newMethodReference.getSignature());
      } else {
        namingState.addRenaming(method.getMethodSignature(), newMethodReference.getSignature());
      }
    }
    return newMethodReference;
  }

  private boolean isAccessModificationAllowed(ProgramDefinition definition) {
    // TODO(b/278687711): Also check that the definition does not have any illegal accesses to it.
    return appView.getKeepInfo(definition).isAccessModificationAllowed(options);
  }

  private boolean isRenamingAllowed(ProgramMethod method) {
    KeepMethodInfo keepInfo = appView.getKeepInfo(method);
    return keepInfo.isOptimizationAllowed(options) && keepInfo.isShrinkingAllowed(options);
  }

  private DexEncodedMethod commitMethod(
      ProgramMethod method,
      BiMap<DexMethod, DexMethod> localNamingState,
      AccessModifierNamingState namingState) {
    return commitMethod(
        method, getAndReserveNewMethodReference(method, localNamingState, namingState));
  }

  private DexEncodedMethod commitMethod(ProgramMethod method, DexMethod newMethodReference) {
    DexProgramClass holder = method.getHolder();
    if (newMethodReference != method.getReference()) {
      lensBuilder.recordMove(method.getReference(), newMethodReference);
      method =
          new ProgramMethod(
              holder, method.getDefinition().toTypeSubstitutedMethod(newMethodReference));
    }
    if (method.getAccessFlags().isPromotedFromPrivateToPublic()
        && method.getAccessFlags().belongsToVirtualPool()) {
      lensBuilder.addPublicizedPrivateVirtualMethod(method.getHolder(), newMethodReference);
      method.getDefinition().setLibraryMethodOverride(OptionalBool.FALSE);
    }
    return method.getDefinition();
  }

  // Finalization of classes and members.

  private void finalizeFields(DexProgramClass clazz) {
    clazz.forEachProgramField(this::finalizeField);
  }

  private void finalizeField(ProgramField field) {
    FieldAccessFlags flags = field.getAccessFlags();
    FieldAccessInfo accessInfo =
        appView.appInfo().getFieldAccessInfoCollection().get(field.getReference());
    if (!appView.getKeepInfo(field).isPinned(options)
        && !accessInfo.hasReflectiveWrite()
        && !accessInfo.isWrittenFromMethodHandle()
        && accessInfo.isWrittenOnlyInMethodSatisfying(
            method ->
                method.getDefinition().isInitializer()
                    && method.getAccessFlags().isStatic() == flags.isStatic()
                    && method.getHolder() == field.getHolder())
        && !flags.isFinal()
        && !flags.isVolatile()) {
      flags.promoteToFinal();
    }
  }
}
