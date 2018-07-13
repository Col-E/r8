// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

// Per-class collection of method signatures.
//
// Example use case: to determine if a certain method can be publicized or not.
public class MethodPoolCollection {

  private static final Equivalence<DexMethod> equivalence = MethodSignatureEquivalence.get();

  private final DexApplication application;
  private final Map<DexClass, MethodPool> methodPools = new ConcurrentHashMap<>();

  public MethodPoolCollection(DexApplication application) {
    this.application = application;
  }

  public void buildAll(ExecutorService executorService, Timing timing) throws ExecutionException {
    timing.begin("Building method pool collection");
    try {
      List<Future<?>> futures = new ArrayList<>();
      submitAll(application.classes(), futures, executorService);
      ThreadUtils.awaitFutures(futures);
    } finally {
      timing.end();
    }
  }

  public MethodPool get(DexClass clazz) {
    assert methodPools.containsKey(clazz);
    return methodPools.get(clazz);
  }

  public boolean markIfNotSeen(DexClass clazz, DexMethod method) {
    MethodPool methodPool = get(clazz);
    Wrapper<DexMethod> key = equivalence.wrap(method);
    if (methodPool.hasSeen(key)) {
      return true;
    }
    methodPool.seen(key);
    return false;
  }

  private void submitAll(
      Iterable<DexProgramClass> classes, List<Future<?>> futures, ExecutorService executorService) {
    for (DexProgramClass clazz : classes) {
      futures.add(executorService.submit(computeMethodPoolPerClass(clazz)));
    }
  }

  private Runnable computeMethodPoolPerClass(DexClass clazz) {
    return () -> {
      MethodPool methodPool = methodPools.computeIfAbsent(clazz, k -> new MethodPool());
      clazz.forEachMethod(
          encodedMethod -> {
            // We will add private instance methods when we promote them.
            if (!encodedMethod.isPrivateMethod() || encodedMethod.isStaticMethod()) {
              methodPool.seen(equivalence.wrap(encodedMethod.method));
            }
          });
      if (clazz.superType != null) {
        DexClass superClazz = application.definitionFor(clazz.superType);
        if (superClazz != null) {
          MethodPool superPool = methodPools.computeIfAbsent(superClazz, k -> new MethodPool());
          superPool.linkSubtype(methodPool);
          methodPool.linkSupertype(superPool);
        }
      }
      if (clazz.isInterface()) {
        clazz.type.forAllImplementsSubtypes(
            implementer -> {
              DexClass subClazz = application.definitionFor(implementer);
              if (subClazz != null) {
                MethodPool childPool = methodPools.computeIfAbsent(subClazz, k -> new MethodPool());
                childPool.linkInterface(methodPool);
              }
            });
      }
    };
  }

  public static class MethodPool {
    private MethodPool superType;
    private final Set<MethodPool> interfaces = new HashSet<>();
    private final Set<MethodPool> subTypes = new HashSet<>();
    private final Set<Wrapper<DexMethod>> methodPool = new HashSet<>();

    private MethodPool() {}

    synchronized void linkSupertype(MethodPool superType) {
      assert this.superType == null;
      this.superType = superType;
    }

    synchronized void linkSubtype(MethodPool subType) {
      boolean added = subTypes.add(subType);
      assert added;
    }

    synchronized void linkInterface(MethodPool itf) {
      boolean added = interfaces.add(itf);
      assert added;
    }

    public synchronized void seen(Wrapper<DexMethod> method) {
      boolean added = methodPool.add(method);
      assert added;
    }

    public boolean hasSeen(Wrapper<DexMethod> method) {
      return hasSeenUpwardRecursive(method) || hasSeenDownwardRecursive(method);
    }

    private boolean hasSeenUpwardRecursive(Wrapper<DexMethod> method) {
      return methodPool.contains(method)
          || (superType != null && superType.hasSeenUpwardRecursive(method))
          || interfaces.stream().anyMatch(itf -> itf.hasSeenUpwardRecursive(method));
    }

    private boolean hasSeenDownwardRecursive(Wrapper<DexMethod> method) {
      return methodPool.contains(method)
          || subTypes.stream().anyMatch(subType -> subType.hasSeenDownwardRecursive(method));
    }
  }
}
