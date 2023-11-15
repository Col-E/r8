// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import static com.android.tools.r8.graph.DexClassAndMethod.asProgramMethodOrNull;
import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.desugar.LambdaDescriptor.isLambdaMetafactoryMethod;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultUseRegistry;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicyWithPreprocessing;
import com.android.tools.r8.horizontalclassmerging.policies.deadlock.SingleCallerInformation;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions.HorizontalClassMergerOptions;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Disallows merging of classes when the merging could introduce class initialization deadlocks.
 *
 * <p>Example: In the below example, if thread t0 triggers the class initialization of A, and thread
 * t1 triggers the class initialization of C, then the program will never deadlock. However, if
 * classes B and C are merged, then the program may all of a sudden deadlock, since thread t0 may
 * hold the lock for A and wait for BC's lock, meanwhile thread t1 holds the lock for BC while
 * waiting for A's lock.
 *
 * <pre>
 *   class A {
 *     static {
 *       new B();
 *     }
 *   }
 *   class B extends A {}
 *   class C extends A {}
 * </pre>
 *
 * <p>To identify the above situation, we perform a tracing from {@code A.<clinit>} to check if
 * there is an execution path that triggers the class initialization of B or C. In that case, the
 * reached subclass is ineligible for class merging.
 *
 * <p>Example: In the below example, if thread t0 triggers the class initialization of A, and thread
 * t1 triggers the class initialization of B, then the program will never deadlock. However, if
 * classes B and C are merged, then the program may all of a sudden deadlock, since thread 0 may
 * hold the lock for A and wait for BC's lock, meanwhile thread t1 holds the lock for BC while
 * waiting for A's lock.
 *
 * <pre>
 *   class A {
 *     static {
 *       new C();
 *     }
 *   }
 *   class B {
 *     static {
 *       new A();
 *     }
 *   }
 *   class C {}
 * </pre>
 *
 * <p>To identify the above situation, we perform a tracing for each {@code <clinit>} in the merge
 * group. If we find an execution path from the class initializer of one class in the merge group to
 * the class initializer of another class in the merge group, then after merging there is a cycle in
 * the class initialization that could lead to a deadlock.
 */
public class NoClassInitializerCycles extends MultiClassPolicyWithPreprocessing<Void> {

  final AppView<AppInfoWithLiveness> appView;

  // Mapping from each merge candidate to its merge group.
  final Map<DexProgramClass, MergeGroup> allGroups = new IdentityHashMap<>();

  private SingleCallerInformation singleCallerInformation;

  public NoClassInitializerCycles(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  private AppView<AppInfoWithLiveness> appView() {
    return appView;
  }

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  @Override
  public Collection<MergeGroup> apply(MergeGroup group, Void nothing) {
    // Partition the merge group into smaller groups that may be merged. If the class initialization
    // of a parent class may initialize a member of the merge group, then this member is not
    // eligible for class merging, unless the only way to class initialize this member is from the
    // class initialization of the parent class. In this case, the member may be merged with other
    // group members that are also guaranteed to only be class initialized from the class
    // initialization of the parent class.
    List<MergeGroup> partitioning = partitionClassesWithPossibleClassInitializerDeadlock(group);
    List<MergeGroup> newGroups = new LinkedList<>();

    // Revisit each partition. If the class initialization of a group member may initialize another
    // class (not necessarily a group member), and vice versa, then class initialization could
    // deadlock if the group member is merged with another class that is initialized concurrently.
    for (MergeGroup partition : partitioning) {
      List<MergeGroup> newGroupsFromPartition = new LinkedList<>();
      Tracer tracer = new Tracer(partition);
      for (DexProgramClass clazz : partition) {
        MergeGroup newGroup = getOrCreateGroupFor(clazz, newGroupsFromPartition, tracer);
        if (newGroup != null) {
          newGroup.add(clazz);
        } else {
          // Ineligible for merging.
        }
      }
      newGroups.addAll(newGroupsFromPartition);
    }
    removeTrivialGroups(newGroups);
    commit(group, newGroups);
    return newGroups;
  }

  private void commit(MergeGroup oldGroup, List<MergeGroup> newGroups) {
    for (MergeGroup newGroup : newGroups) {
      for (DexProgramClass member : newGroup) {
        allGroups.put(member, newGroup);
      }
    }
    for (DexProgramClass member : oldGroup) {
      MergeGroup newGroup = allGroups.get(member);
      if (newGroup == oldGroup) {
        allGroups.remove(member);
      }
    }
  }

  private MergeGroup getOrCreateGroupFor(
      DexProgramClass clazz, List<MergeGroup> groups, Tracer tracer) {
    assert !tracer.hasPossibleClassInitializerDeadlock(clazz);

    if (clazz.hasClassInitializer()) {
      // Trace from the class initializer of this group member. If an execution path is found that
      // leads back to the class initializer then this class may be involved in a deadlock, and we
      // should not merge any other classes into it.
      tracer.setTracingRoot(clazz);
      tracer.enqueueTracingRoot(clazz.getProgramClassInitializer());
      tracer.trace();
      if (tracer.hasPossibleClassInitializerDeadlock(clazz)) {
        // Ineligible for merging.
        return null;
      }
    }

    for (MergeGroup group : groups) {
      if (canMerge(clazz, group, tracer)) {
        return group;
      }
    }

    MergeGroup newGroup = new MergeGroup();
    groups.add(newGroup);
    return newGroup;
  }

  private boolean canMerge(DexProgramClass clazz, MergeGroup group, Tracer tracer) {
    for (DexProgramClass member : group) {
      // Check that the class initialization of the given class cannot reach the class initializer
      // of the current group member.
      if (tracer.isClassInitializedByClassInitializationOf(member, clazz)) {
        return false;
      }
      // Check that the class initialization of the current group member cannot reach the class
      // initializer of the given class.
      if (tracer.isClassInitializedByClassInitializationOf(clazz, member)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Runs the tracer from the parent class initializers, using the entire group as tracing context.
   * If the class initializer of one of the classes in the merge group is reached, then that class
   * is not eligible for merging.
   */
  private List<MergeGroup> partitionClassesWithPossibleClassInitializerDeadlock(MergeGroup group) {
    Set<DexProgramClass> superclasses = Sets.newIdentityHashSet();
    appView
        .appInfo()
        .traverseSuperClasses(
            group.iterator().next(),
            (supertype, superclass, immediateSubclass) -> {
              if (superclass != null && superclass.isProgramClass()) {
                superclasses.add(superclass.asProgramClass());
                return TraversalContinuation.doContinue();
              }
              return TraversalContinuation.doBreak();
            });

    // Run the tracer from the class initializers of the superclasses.
    Tracer tracer = new Tracer(group);
    tracer.setTracingRoots(group);
    for (DexProgramClass superclass : superclasses) {
      if (superclass.hasClassInitializer()) {
        tracer.enqueueTracingRoot(superclass.getProgramClassInitializer());
      }
    }
    tracer.trace();

    MergeGroup notInitializedByInitializationOfParent = new MergeGroup();
    Map<DexProgramClass, MergeGroup> partitioning = new LinkedHashMap<>();
    for (DexProgramClass member : group) {
      if (tracer.hasPossibleClassInitializerDeadlock(member)) {
        DexProgramClass nearestLock = getNearestLock(member, superclasses);
        if (nearestLock != null) {
          partitioning.computeIfAbsent(nearestLock, ignoreKey(MergeGroup::new)).add(member);
        } else {
          // Ineligible for merging.
        }
      } else {
        notInitializedByInitializationOfParent.add(member);
      }
    }

    return ImmutableList.<MergeGroup>builder()
        .add(notInitializedByInitializationOfParent)
        .addAll(partitioning.values())
        .build();
  }

  private DexProgramClass getNearestLock(
      DexProgramClass clazz, Set<DexProgramClass> candidateOwners) {
    ProgramMethodSet seen = ProgramMethodSet.create();
    ProgramMethod singleCaller = singleCallerInformation.getSingleClassInitializerCaller(clazz);
    while (singleCaller != null && seen.add(singleCaller)) {
      if (singleCaller.getDefinition().isClassInitializer()
          && candidateOwners.contains(singleCaller.getHolder())) {
        return singleCaller.getHolder();
      }
      singleCaller = singleCallerInformation.getSingleCaller(singleCaller);
    }
    return null;
  }

  @Override
  public void clear() {
    allGroups.clear();
  }

  @Override
  public String getName() {
    return "NoClassInitializerCycles";
  }

  @Override
  public Void preprocess(Collection<MergeGroup> groups, ExecutorService executorService)
      throws ExecutionException {
    for (MergeGroup group : groups) {
      for (DexProgramClass clazz : group) {
        allGroups.put(clazz, group);
      }
    }
    singleCallerInformation =
        SingleCallerInformation.builder(appView).analyze(executorService).build();
    return null;
  }

  @Override
  public boolean shouldSkipPolicy() {
    HorizontalClassMergerOptions options = appView.options().horizontalClassMergerOptions();
    return !options.isClassInitializerDeadlockDetectionEnabled();
  }

  private class Tracer {

    final MergeGroup group;

    // The members of the existing merge group, for efficient membership querying.
    final Set<DexProgramClass> groupMembers;

    private final Set<DexProgramClass> seenClassInitializers = Sets.newIdentityHashSet();
    private final ProgramMethodSet seenMethods = ProgramMethodSet.create();
    private final Deque<ProgramMethod> worklist = new ArrayDeque<>();

    // Mapping from each merge grop member to the set of merge group members whose class
    // initializers may trigger the class initialization of the group member.
    private final Map<DexProgramClass, Set<DexProgramClass>> classInitializerReachableFromClasses =
        new IdentityHashMap<>();

    // The current tracing roots (either the entire merge group or one of the classes in the merge
    // group).
    private Collection<DexProgramClass> tracingRoots;

    Tracer(MergeGroup group) {
      this.group = group;
      this.groupMembers = SetUtils.newIdentityHashSet(group);
    }

    void clearSeen() {
      seenClassInitializers.clear();
      seenMethods.clear();
    }

    void clearWorklist() {
      worklist.clear();
    }

    boolean enqueueMethod(ProgramMethod method) {
      if (seenMethods.add(method)) {
        worklist.addLast(method);
        return true;
      }
      return false;
    }

    void enqueueTracingRoot(ProgramMethod tracingRoot) {
      boolean added = seenMethods.add(tracingRoot);
      assert added;
      worklist.add(tracingRoot);
    }

    void recordClassInitializerReachableFromTracingRoots(DexProgramClass clazz) {
      assert groupMembers.contains(clazz);
      classInitializerReachableFromClasses
          .computeIfAbsent(clazz, ignoreKey(Sets::newIdentityHashSet))
          .addAll(tracingRoots);
    }

    void recordTracingRootsIneligibleForClassMerging() {
      for (DexProgramClass tracingRoot : tracingRoots) {
        classInitializerReachableFromClasses
            .computeIfAbsent(tracingRoot, ignoreKey(Sets::newIdentityHashSet))
            .add(tracingRoot);
      }
    }

    boolean hasSingleTracingRoot(DexProgramClass clazz) {
      return tracingRoots.size() == 1 && tracingRoots.contains(clazz);
    }

    boolean hasPossibleClassInitializerDeadlock(DexProgramClass clazz) {
      return classInitializerReachableFromClasses
          .getOrDefault(clazz, Collections.emptySet())
          .contains(clazz);
    }

    boolean isClassInitializedByClassInitializationOf(
        DexProgramClass classToBeInitialized, DexProgramClass classBeingInitialized) {
      return classInitializerReachableFromClasses
          .getOrDefault(classToBeInitialized, Collections.emptySet())
          .contains(classBeingInitialized);
    }

    private void processWorklist() {
      while (!worklist.isEmpty()) {
        ProgramMethod method = worklist.removeLast();
        method.registerCodeReferences(new TracerUseRegistry(method));
      }
    }

    void setTracingRoot(DexProgramClass tracingRoot) {
      setTracingRoots(ImmutableList.of(tracingRoot));
    }

    void setTracingRoots(Collection<DexProgramClass> tracingRoots) {
      assert verifySeenSetIsEmpty();
      assert verifyWorklistIsEmpty();
      this.tracingRoots = tracingRoots;
    }

    void trace() {
      processWorklist();
      clearSeen();
    }

    boolean verifySeenSetIsEmpty() {
      assert seenClassInitializers.isEmpty();
      assert seenMethods.isEmpty();
      return true;
    }

    boolean verifyWorklistIsEmpty() {
      assert worklist.isEmpty();
      return true;
    }

    class TracerUseRegistry extends DefaultUseRegistry<ProgramMethod> {

      TracerUseRegistry(ProgramMethod context) {
        super(appView(), context);
      }

      private void fail() {
        // Ensures that hasPossibleClassInitializerDeadlock() returns true for each tracing root.
        recordTracingRootsIneligibleForClassMerging();
        doBreak();
        clearWorklist();
      }

      private void triggerClassInitializerIfNotAlreadyTriggeredInContext(DexType type) {
        DexProgramClass clazz = type.asProgramClass(appView);
        if (clazz != null) {
          triggerClassInitializerIfNotAlreadyTriggeredInContext(clazz);
        }
      }

      private void triggerClassInitializerIfNotAlreadyTriggeredInContext(DexProgramClass clazz) {
        if (!isClassAlreadyInitializedInCurrentContext(clazz)) {
          triggerClassInitializer(clazz);
        }
      }

      private boolean isClassAlreadyInitializedInCurrentContext(DexProgramClass clazz) {
        return appView().appInfo().isSubtype(getContext().getHolder(), clazz);
      }

      private void triggerClassInitializer(DexProgramClass root) {
        WorkList<DexProgramClass> worklist = WorkList.newWorkList(seenClassInitializers);
        worklist.addIfNotSeen(root);
        while (worklist.hasNext()) {
          DexProgramClass clazz = worklist.next();
          if (groupMembers.contains(clazz)) {
            if (hasSingleTracingRoot(clazz)) {
              // We found an execution path from the class initializer of the given class back to
              // its own class initializer. Therefore this class is not eligible for merging.
              fail();
            } else {
              // Record that this class initializer is reachable from the tracing roots.
              recordClassInitializerReachableFromTracingRoots(clazz);
            }
          }

          ProgramMethod classInitializer = clazz.getProgramClassInitializer();
          if (classInitializer != null && !enqueueMethod(classInitializer)) {
            // This class initializer is already seen in the current context, thus all of the parent
            // class initializers are also seen in the current context.
            return;
          }

          DexProgramClass superClass =
              asProgramClassOrNull(appView.definitionFor(clazz.getSuperType()));
          if (superClass != null) {
            worklist.addIfNotSeen(superClass);
          }

          MergeGroup other = allGroups.get(clazz);
          if (other != null && other != group) {
            worklist.addIfNotSeen(other);
          }
        }
      }

      @Override
      public void registerInitClass(DexType type) {
        DexType rewrittenType = appView.graphLens().lookupType(type);
        triggerClassInitializerIfNotAlreadyTriggeredInContext(rewrittenType);
      }

      @Override
      public void registerInvokeDirect(DexMethod method) {
        DexMethod rewrittenMethod =
            appView.graphLens().lookupInvokeDirect(method, getContext()).getReference();
        MethodResolutionResult resolutionResult =
            appView().appInfo().resolveMethodOnClassHolderLegacy(rewrittenMethod);
        if (resolutionResult.isSingleResolution()
            && resolutionResult.getResolvedHolder().isProgramClass()) {
          enqueueMethod(resolutionResult.getResolvedProgramMethod());
        }
      }

      @Override
      public void registerInvokeInterface(DexMethod method) {
        DexMethod rewrittenMethod =
            appView.graphLens().lookupInvokeInterface(method, getContext()).getReference();
        DexClassAndMethod resolvedMethod =
            appView()
                .appInfo()
                .resolveMethodOnInterfaceHolderLegacy(rewrittenMethod)
                .getResolutionPair();
        if (resolvedMethod != null) {
          fail();
        }
      }

      @Override
      public void registerInvokeStatic(DexMethod method) {
        DexMethod rewrittenMethod =
            appView.graphLens().lookupInvokeStatic(method, getContext()).getReference();
        ProgramMethod resolvedMethod =
            appView()
                .appInfo()
                .unsafeResolveMethodDueToDexFormatLegacy(rewrittenMethod)
                .getResolvedProgramMethod();
        if (resolvedMethod != null) {
          triggerClassInitializerIfNotAlreadyTriggeredInContext(resolvedMethod.getHolder());
          enqueueMethod(resolvedMethod);
        }
      }

      @Override
      public void registerInvokeSuper(DexMethod method) {
        DexMethod rewrittenMethod =
            appView.graphLens().lookupInvokeSuper(method, getContext()).getReference();
        ProgramMethod superTarget =
            asProgramMethodOrNull(
                appView().appInfo().lookupSuperTarget(rewrittenMethod, getContext(), appView()));
        if (superTarget != null) {
          enqueueMethod(superTarget);
        }
      }

      @Override
      public void registerInvokeVirtual(DexMethod method) {
        DexMethod rewrittenMethod =
            appView.graphLens().lookupInvokeVirtual(method, getContext()).getReference();
        DexClassAndMethod resolvedMethod =
            appView()
                .appInfo()
                .resolveMethodOnClassHolderLegacy(rewrittenMethod)
                .getResolutionPair();
        if (resolvedMethod != null) {
          if (!resolvedMethod.getHolder().isEffectivelyFinal(appView)) {
            fail();
          } else if (resolvedMethod.isProgramMethod()) {
            enqueueMethod(resolvedMethod.asProgramMethod());
          }
        }
      }

      @Override
      public void registerNewInstance(DexType type) {
        DexType rewrittenType = appView.graphLens().lookupType(type);
        triggerClassInitializerIfNotAlreadyTriggeredInContext(rewrittenType);
      }

      @Override
      public void registerStaticFieldRead(DexField field) {
        DexField rewrittenField = appView.graphLens().lookupField(field);
        triggerClassInitializerIfNotAlreadyTriggeredInContext(rewrittenField.getHolderType());
      }

      @Override
      public void registerStaticFieldWrite(DexField field) {
        DexField rewrittenField = appView.graphLens().lookupField(field);
        triggerClassInitializerIfNotAlreadyTriggeredInContext(rewrittenField.getHolderType());
      }

      @Override
      public void registerCallSite(DexCallSite callSite) {
        if (isLambdaMetafactoryMethod(callSite, appView().appInfo())) {
          // Use of lambda metafactory does not trigger any class initialization.
        } else {
          fail();
        }
      }
    }
  }
}
