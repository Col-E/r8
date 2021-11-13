// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import static com.android.tools.r8.graph.DexClassAndMethod.asProgramMethodOrNull;
import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.code.CfOrDexInstruction;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicyWithPreprocessing;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions.HorizontalClassMergerOptions;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

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

  public NoClassInitializerCycles(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public Collection<MergeGroup> apply(MergeGroup group, Void nothing) {
    Tracer tracer = new Tracer(group);
    removeClassesWithPossibleClassInitializerDeadlock(group, tracer);

    List<MergeGroup> newGroups = new LinkedList<>();
    for (DexProgramClass clazz : group) {
      MergeGroup newGroup = getOrCreateGroupFor(clazz, newGroups, tracer);
      if (newGroup != null) {
        newGroup.add(clazz);
      } else {
        // Ineligible for merging.
      }
    }
    return removeTrivialGroups(newGroups);
  }

  private MergeGroup getOrCreateGroupFor(
      DexProgramClass clazz, List<MergeGroup> groups, Tracer tracer) {
    assert !tracer.hasPossibleClassInitializerDeadlock(clazz);

    ProgramMethod classInitializer = clazz.getProgramClassInitializer();
    if (classInitializer != null) {
      assert tracer.verifySeenSetIsEmpty();
      assert tracer.verifyWorklistIsEmpty();
      tracer.setTracingRoot(clazz);
      tracer.enqueueMethod(classInitializer);
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
  private void removeClassesWithPossibleClassInitializerDeadlock(MergeGroup group, Tracer tracer) {
    tracer.setTracingRoots(group);
    tracer.enqueueParentClassInitializers(group);
    tracer.trace();
    group.removeIf(tracer::hasPossibleClassInitializerDeadlock);
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
  public Void preprocess(Collection<MergeGroup> groups) {
    for (MergeGroup group : groups) {
      for (DexProgramClass clazz : group) {
        allGroups.put(clazz, group);
      }
    }
    return null;
  }

  @Override
  public boolean shouldSkipPolicy() {
    HorizontalClassMergerOptions options = appView.options().horizontalClassMergerOptions();
    return !options.isClassInitializerDeadlockDetectionEnabled();
  }

  private class Tracer {

    final Set<DexProgramClass> group;

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
      this.group = SetUtils.newIdentityHashSet(group);
    }

    void clearSeen() {
      seenClassInitializers.clear();
      seenMethods.clear();
    }

    boolean markClassInitializerAsSeen(DexProgramClass clazz) {
      return seenClassInitializers.add(clazz);
    }

    boolean enqueueMethod(ProgramMethod method) {
      if (seenMethods.add(method)) {
        worklist.add(method);
        return true;
      }
      return false;
    }

    void enqueueParentClassInitializers(MergeGroup group) {
      DexProgramClass member = group.iterator().next();
      enqueueParentClassInitializers(member);
    }

    void enqueueParentClassInitializers(DexProgramClass clazz) {
      DexProgramClass superClass =
          asProgramClassOrNull(appView.definitionFor(clazz.getSuperType()));
      if (superClass == null) {
        return;
      }
      ProgramMethod classInitializer = superClass.getProgramClassInitializer();
      if (classInitializer != null) {
        enqueueMethod(classInitializer);
      }
      enqueueParentClassInitializers(superClass);
    }

    void recordClassInitializerReachableFromTracingRoots(DexProgramClass clazz) {
      assert group.contains(clazz);
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

    void setTracingRoot(DexProgramClass tracingRoot) {
      setTracingRoots(ImmutableList.of(tracingRoot));
    }

    void setTracingRoots(Collection<DexProgramClass> tracingRoots) {
      this.tracingRoots = tracingRoots;
    }

    void trace() {
      // TODO(b/205611444): Avoid redundant tracing of the same methods.
      while (!worklist.isEmpty()) {
        ProgramMethod method = worklist.removeLast();
        method.registerCodeReferences(new TracerUseRegistry(method));
      }
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

    class TracerUseRegistry extends UseRegistry<ProgramMethod> {

      TracerUseRegistry(ProgramMethod context) {
        super(appView, context);
      }

      private void fail() {
        // Ensures that hasPossibleClassInitializerDeadlock() returns true for each tracing root.
        recordTracingRootsIneligibleForClassMerging();
        doBreak();
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
        // TODO(b/205611444): There is only a risk of a deadlock if the execution path comes from
        //  outside the merge group. We could address this by updating this check.
        return appView.appInfo().isSubtype(getContext().getHolder(), clazz);
      }

      private void triggerClassInitializer(DexType type) {
        DexProgramClass clazz = type.asProgramClass(appView);
        if (clazz != null) {
          triggerClassInitializer(clazz);
        }
      }

      // TODO(b/205611444): This needs to account for pending merging. If the given class is in a
      //  merge group, then this should trigger the class initializers of all of the classes in the
      //  merge group.
      private void triggerClassInitializer(DexProgramClass clazz) {
        if (!markClassInitializerAsSeen(clazz)) {
          return;
        }

        if (group.contains(clazz)) {
          if (hasSingleTracingRoot(clazz)) {
            // We found an execution path from the class initializer of the given class back to its
            // own class initializer. Therefore this class is not eligible for merging.
            fail();
          } else {
            // Record that this class initializer is reachable from the tracing roots.
            recordClassInitializerReachableFromTracingRoots(clazz);
          }
        }

        ProgramMethod classInitializer = clazz.getProgramClassInitializer();
        if (classInitializer != null) {
          if (!enqueueMethod(classInitializer)) {
            // This class initializer is already seen in the current context, thus all of the parent
            // class initializers are also seen in the current context.
            return;
          }
        }

        triggerClassInitializer(clazz.getSuperType());
      }

      @Override
      public void registerInitClass(DexType type) {
        triggerClassInitializerIfNotAlreadyTriggeredInContext(type);
      }

      @Override
      public void registerInvokeDirect(DexMethod method) {
        DexMethod rewrittenMethod =
            appView.graphLens().lookupInvokeDirect(method, getContext()).getReference();
        MethodResolutionResult resolutionResult =
            appView.appInfo().resolveMethodOnClass(rewrittenMethod);
        if (resolutionResult.isSingleResolution()
            && resolutionResult.getResolvedHolder().isProgramClass()) {
          enqueueMethod(resolutionResult.getResolvedProgramMethod());
        }
      }

      @Override
      public void registerInvokeInterface(DexMethod method) {
        fail();
      }

      @Override
      public void registerInvokeStatic(DexMethod method) {
        DexMethod rewrittenMethod =
            appView.graphLens().lookupInvokeStatic(method, getContext()).getReference();
        ProgramMethod resolvedMethod =
            appView
                .appInfo()
                .unsafeResolveMethodDueToDexFormat(rewrittenMethod)
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
                appView.appInfo().lookupSuperTarget(rewrittenMethod, getContext()));
        if (superTarget != null) {
          enqueueMethod(superTarget);
        }
      }

      @Override
      public void registerInvokeVirtual(DexMethod method) {
        fail();
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
      public void registerTypeReference(DexType type) {
        // Intentionally empty, new-array etc. does not trigger any class initialization.
      }

      @Override
      public void registerCallSite(DexCallSite callSite) {
        LambdaDescriptor descriptor =
            LambdaDescriptor.tryInfer(callSite, appView.appInfo(), getContext());
        if (descriptor != null) {
          // Use of lambda metafactory does not trigger any class initialization.
        } else {
          fail();
        }
      }

      @Override
      public void registerCheckCast(DexType type, boolean ignoreCompatRules) {
        // Intentionally empty, does not trigger any class initialization.
      }

      @Override
      public void registerConstClass(
          DexType type,
          ListIterator<? extends CfOrDexInstruction> iterator,
          boolean ignoreCompatRules) {
        // Intentionally empty, does not trigger any class initialization.
      }

      @Override
      public void registerInstanceFieldRead(DexField field) {
        // Intentionally empty, does not trigger any class initialization.
      }

      @Override
      public void registerInstanceFieldWrite(DexField field) {
        // Intentionally empty, does not trigger any class initialization.
      }

      @Override
      public void registerInstanceOf(DexType type) {
        // Intentionally empty, does not trigger any class initialization.
      }

      @Override
      public void registerExceptionGuard(DexType guard) {
        // Intentionally empty, does not trigger any class initialization.
      }
    }
  }
}
