// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.Descriptor;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
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
import java.util.function.Predicate;

// Per-class collection of member signatures.
public abstract class MemberPoolCollection<T extends Descriptor> {

  final Equivalence<T> equivalence;
  final DexApplication application;
  final Map<DexClass, MemberPool<T>> memberPools = new ConcurrentHashMap<>();

  MemberPoolCollection(DexApplication application, Equivalence<T> equivalence) {
    this.application = application;
    this.equivalence = equivalence;
  }

  public void buildAll(ExecutorService executorService, Timing timing) throws ExecutionException {
    timing.begin("Building member pool collection");
    try {
      List<Future<?>> futures = new ArrayList<>();
      List<? extends DexClass> classes = application.classes();
      submitAll(classes, futures, executorService);
      ThreadUtils.awaitFutures(futures);
    } finally {
      timing.end();
    }
  }

  public MemberPool<T> buildForHierarchy(
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

  public MemberPool<T> get(DexClass clazz) {
    assert hasPool(clazz);
    return memberPools.get(clazz);
  }

  public boolean markIfNotSeen(DexClass clazz, T reference) {
    MemberPool<T> memberPool = get(clazz);
    Wrapper<T> key = equivalence.wrap(reference);
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
      futures.add(executorService.submit(computeMemberPoolForClass(clazz)));
    }
  }

  abstract Runnable computeMemberPoolForClass(DexClass clazz);

  // TODO(jsjeon): maybe be part of AppInfoWithSubtyping?
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
          addNonNull(worklist, application.definitionFor(clazz.superType));
        }
        for (DexType interfaceType : clazz.interfaces.values) {
          addNonNull(worklist, application.definitionFor(interfaceType));
        }
      }
    }
    return superTypes;
  }

  // TODO(jsjeon): maybe be part of AppInfoWithSubtyping?
  private Set<DexClass> getAllSubTypesExclusive(
      DexClass subject, Predicate<DexClass> stoppingCriterion) {
    Set<DexClass> subTypes = new HashSet<>();
    Deque<DexClass> worklist = new ArrayDeque<>();
    subject.type.forAllExtendsSubtypes(
        type -> addNonNull(worklist, application.definitionFor(type)));
    subject.type.forAllImplementsSubtypes(
        type -> addNonNull(worklist, application.definitionFor(type)));
    while (!worklist.isEmpty()) {
      DexClass clazz = worklist.pop();
      if (stoppingCriterion.test(clazz)) {
        continue;
      }
      if (subTypes.add(clazz)) {
        clazz.type.forAllExtendsSubtypes(
            type -> addNonNull(worklist, application.definitionFor(type)));
        clazz.type.forAllImplementsSubtypes(
            type -> addNonNull(worklist, application.definitionFor(type)));
      }
    }
    return subTypes;
  }

  public static class MemberPool<T> {
    private Equivalence<T> equivalence;
    private MemberPool<T> superType;
    private final Set<MemberPool<T>> interfaces = new HashSet<>();
    private final Set<MemberPool<T>> subTypes = new HashSet<>();
    private final Set<Wrapper<T>> memberPool = new HashSet<>();

    MemberPool(Equivalence<T> equivalence) {
      this.equivalence = equivalence;
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

    public void seen(T descriptor) {
      seen(equivalence.wrap(descriptor));
    }

    public synchronized void seen(Wrapper<T> descriptor) {
      boolean added = memberPool.add(descriptor);
      assert added;
    }

    public boolean hasSeen(T descriptor) {
      return hasSeen(equivalence.wrap(descriptor));
    }

    public boolean hasSeen(Wrapper<T> descriptor) {
      return hasSeenUpwardRecursive(descriptor) || hasSeenDownwardRecursive(descriptor);
    }

    public boolean hasSeenDirectly(T descriptor) {
      return hasSeenDirectly(equivalence.wrap(descriptor));
    }

    public boolean hasSeenDirectly(Wrapper<T> descriptor) {
      return memberPool.contains(descriptor);
    }

    private boolean hasSeenUpwardRecursive(Wrapper<T> descriptor) {
      return memberPool.contains(descriptor)
          || (superType != null && superType.hasSeenUpwardRecursive(descriptor))
          || interfaces.stream().anyMatch(itf -> itf.hasSeenUpwardRecursive(descriptor));
    }

    private boolean hasSeenDownwardRecursive(Wrapper<T> reference) {
      return memberPool.contains(reference)
          || subTypes.stream().anyMatch(subType -> subType.hasSeenDownwardRecursive(reference));
    }
  }

  private static <T> void addNonNull(Collection<T> collection, T item) {
    if (item != null) {
      collection.add(item);
    }
  }
}
