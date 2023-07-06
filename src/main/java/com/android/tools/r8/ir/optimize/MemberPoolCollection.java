// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.graph.TopDownClassHierarchyTraversal;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.WorkList;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Predicate;

// Per-class collection of member signatures.
public abstract class MemberPoolCollection<R extends DexMember<?, R>> {

  final Equivalence<R> equivalence;
  final AppView<AppInfoWithLiveness> appView;
  final SubtypingInfo subtypingInfo;
  final Map<DexClass, MemberPool<R>> memberPools = new ConcurrentHashMap<>();

  MemberPoolCollection(
      AppView<AppInfoWithLiveness> appView,
      Equivalence<R> equivalence,
      SubtypingInfo subtypingInfo) {
    this.appView = appView;
    this.equivalence = equivalence;
    this.subtypingInfo = subtypingInfo;
  }

  public void buildAll(ExecutorService executorService, Timing timing) throws ExecutionException {
    timing.begin("Building member pool collection");
    try {
      List<Future<?>> futures = new ArrayList<>();

      // Generate a future for each class that will build the member pool collection for the
      // corresponding class. Note that, we visit the classes using a top-down class hierarchy
      // traversal, since this ensures that we do not visit library classes that are not
      // reachable from any program class.
      TopDownClassHierarchyTraversal.forAllClasses(appView)
          .visit(appView.appInfo().classes(), clazz -> submit(clazz, futures, executorService));
      ThreadUtils.awaitFutures(futures);
    } finally {
      timing.end();
    }
  }

  public MemberPool<R> buildForHierarchy(
      DexClass clazz, ExecutorService executorService, Timing timing) throws ExecutionException {
    timing.begin("Building member pool collection");
    try {
      List<Future<?>> futures = new ArrayList<>();
      submitAll(
          getAllSuperTypesInclusive(clazz, memberPools::containsKey), futures, executorService);
      submitAll(getAllSubTypesExclusive(clazz, memberPools::containsKey), futures, executorService);
      ThreadUtils.awaitFutures(futures);
    } finally {
      timing.end();
    }
    return get(clazz);
  }

  public boolean hasPool(DexClass clazz) {
    return memberPools.containsKey(clazz);
  }

  public MemberPool<R> get(DexClass clazz) {
    assert hasPool(clazz);
    return memberPools.get(clazz);
  }

  public boolean markIfNotSeen(DexClass clazz, R reference) {
    MemberPool<R> memberPool = get(clazz);
    Wrapper<R> key = equivalence.wrap(reference);
    if (memberPool.hasSeen(key)) {
      return true;
    }
    memberPool.seen(key);
    return false;
  }

  private void submitAll(
      Iterable<? extends DexClass> classes,
      List<Future<?>> futures,
      ExecutorService executorService) {
    for (DexClass clazz : classes) {
      submit(clazz, futures, executorService);
    }
  }

  private void submit(DexClass clazz, List<Future<?>> futures, ExecutorService executorService) {
    futures.add(executorService.submit(computeMemberPoolForClass(clazz)));
  }

  abstract Runnable computeMemberPoolForClass(DexClass clazz);

  private Set<DexClass> getAllSuperTypesInclusive(
      DexClass subject, Predicate<DexClass> stoppingCriterion) {
    Set<DexClass> superTypes = new HashSet<>();
    Deque<DexClass> worklist = new ArrayDeque<>();
    worklist.add(subject);
    while (!worklist.isEmpty()) {
      DexClass clazz = worklist.pop();
      if (stoppingCriterion.test(clazz)) {
        continue;
      }
      if (superTypes.add(clazz)) {
        if (clazz.superType != null) {
          addNonNull(worklist, appView.definitionFor(clazz.superType));
        }
        for (DexType interfaceType : clazz.interfaces.values) {
          addNonNull(worklist, appView.definitionFor(interfaceType));
        }
      }
    }
    return superTypes;
  }

  private Set<DexClass> getAllSubTypesExclusive(
      DexClass subject, Predicate<DexClass> stoppingCriterion) {
    Set<DexClass> subTypes = new HashSet<>();
    Deque<DexClass> worklist = new ArrayDeque<>();
    subtypingInfo.forAllImmediateExtendsSubtypes(
        subject.type, type -> addNonNull(worklist, appView.definitionFor(type)));
    subtypingInfo.forAllImmediateImplementsSubtypes(
        subject.type, type -> addNonNull(worklist, appView.definitionFor(type)));
    while (!worklist.isEmpty()) {
      DexClass clazz = worklist.pop();
      if (stoppingCriterion.test(clazz)) {
        continue;
      }
      if (subTypes.add(clazz)) {
        subtypingInfo.forAllImmediateExtendsSubtypes(
            clazz.type, type -> addNonNull(worklist, appView.definitionFor(type)));
        subtypingInfo.forAllImmediateImplementsSubtypes(
            clazz.type, type -> addNonNull(worklist, appView.definitionFor(type)));
      }
    }
    return subTypes;
  }

  public static class MemberPool<T> {

    private final DexClass clazz;
    private final Equivalence<T> equivalence;
    private MemberPool<T> superType;
    private final Set<MemberPool<T>> interfaces = new HashSet<>();
    private final Set<MemberPool<T>> subTypes = new HashSet<>();
    private final Set<Wrapper<T>> memberPool = new HashSet<>();

    MemberPool(Equivalence<T> equivalence, DexClass clazz) {
      this.equivalence = equivalence;
      this.clazz = clazz;
    }

    synchronized void linkSupertype(MemberPool<T> superType) {
      assert this.superType == null;
      this.superType = superType;
    }

    synchronized void linkSubtype(MemberPool<T> subType) {
      boolean added = subTypes.add(subType);
      assert added;
    }

    synchronized void linkInterface(MemberPool<T> itf) {
      boolean added = interfaces.add(itf);
      assert added;
    }

    public void seen(T member) {
      seen(equivalence.wrap(member));
    }

    public synchronized void seen(Wrapper<T> member) {
      boolean added = memberPool.add(member);
      assert added;
    }

    public boolean hasSeen(Wrapper<T> member) {
      return fold(member, false, true, (t, ignored) -> true);
    }

    public boolean hasSeenDirectly(Wrapper<T> member) {
      return here(member, false, (t, ignored) -> true);
    }

    public boolean hasSeenStrictlyAbove(Wrapper<T> member) {
      return above(member, false, false, true, (t, ignored) -> true);
    }

    public boolean hasSeenStrictlyBelow(Wrapper<T> member) {
      return below(member, false, true, (t, ignored) -> true);
    }

    private <S> S above(
        Wrapper<T> member,
        boolean inclusive,
        S value,
        S terminator,
        BiFunction<DexClass, S, S> accumulator) {
      WorkList<MemberPool<T>> workList = WorkList.newIdentityWorkList(this);
      while (workList.hasNext()) {
        MemberPool<T> next = workList.next();
        if (inclusive) {
          value = next.here(member, value, accumulator);
          if (value == terminator) {
            return value;
          }
        }
        inclusive = true;
        if (next.superType != null) {
          workList.addIfNotSeen(next.superType);
        }
        workList.addIfNotSeen(next.interfaces);
      }
      return value;
    }

    private <S> S here(Wrapper<T> member, S value, BiFunction<DexClass, S, S> accumulator) {
      if (memberPool.contains(member)) {
        return accumulator.apply(clazz, value);
      }
      return value;
    }

    public <S> S below(
        Wrapper<T> member, S value, S terminator, BiFunction<DexClass, S, S> accumulator) {
      WorkList<MemberPool<T>> workList = WorkList.newIdentityWorkList(this.subTypes);
      while (workList.hasNext()) {
        MemberPool<T> next = workList.next();
        value = next.here(member, value, accumulator);
        if (value == terminator) {
          return value;
        }
        workList.addIfNotSeen(next.interfaces);
        workList.addIfNotSeen(next.subTypes);
      }
      return value;
    }

    public <S> S fold(
        Wrapper<T> member, S initialValue, S terminator, BiFunction<DexClass, S, S> accumulator) {
      S value = above(member, true, initialValue, terminator, accumulator);
      if (value == terminator) {
        return value;
      }
      return below(member, initialValue, terminator, accumulator);
    }
  }

  private static <T> void addNonNull(Collection<T> collection, T item) {
    if (item != null) {
      collection.add(item);
    }
  }
}
