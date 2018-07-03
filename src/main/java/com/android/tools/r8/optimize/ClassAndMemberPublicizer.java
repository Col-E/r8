// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.optimize.PublicizerLense.PublicizedLenseBuilder;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public final class ClassAndMemberPublicizer {
  private final DexApplication application;
  private final AppInfo appInfo;
  private final RootSet rootSet;
  private final GraphLense previousLense;
  private final PublicizedLenseBuilder lenseBuilder;

  private final Equivalence<DexMethod> equivalence = MethodSignatureEquivalence.get();
  private final Map<DexClass, MethodPool> methodPools = new ConcurrentHashMap<>();

  private ClassAndMemberPublicizer(
      DexApplication application,
      AppInfo appInfo,
      RootSet rootSet,
      GraphLense previousLense) {
    this.application = application;
    this.appInfo = appInfo;
    this.rootSet = rootSet;
    this.previousLense = previousLense;
    lenseBuilder = PublicizerLense.createBuilder();
  }

  /**
   * Marks all package private and protected methods and fields as public.
   * Makes all private static methods public.
   * Makes private instance methods public final instance methods, if possible.
   * <p>
   * This will destructively update the DexApplication passed in as argument.
   */
  public static GraphLense run(
      ExecutorService executorService,
      Timing timing,
      DexApplication application,
      AppInfo appInfo,
      RootSet rootSet,
      GraphLense previousLense) throws ExecutionException {
    return new ClassAndMemberPublicizer(application, appInfo, rootSet, previousLense)
        .run(executorService, timing);
  }

  private GraphLense run(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    // Phase 1: Collect methods to check if private instance methods don't have conflicts.
    timing.begin("Phase 1: collectMethods");
    try {
      List<Future<?>> futures = new ArrayList<>();
      application.classes().forEach(clazz ->
          futures.add(executorService.submit(computeMethodPoolPerClass(clazz))));
      ThreadUtils.awaitFutures(futures);
    } finally {
      timing.end();
    }

    // Phase 2: Visit classes and promote class/member to public if possible.
    timing.begin("Phase 2: promoteToPublic");
    DexType.forAllInterfaces(appInfo.dexItemFactory, this::publicizeType);
    publicizeType(appInfo.dexItemFactory.objectType);
    timing.end();

    return lenseBuilder.build(appInfo, previousLense);
  }

  private Runnable computeMethodPoolPerClass(DexClass clazz) {
    return () -> {
      MethodPool methodPool = methodPools.computeIfAbsent(clazz, k -> new MethodPool());
      clazz.forEachMethod(encodedMethod -> {
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
        clazz.type.forAllImplementsSubtypes(implementer -> {
          DexClass subClazz = application.definitionFor(implementer);
          if (subClazz != null) {
            MethodPool childPool = methodPools.computeIfAbsent(subClazz, k -> new MethodPool());
            childPool.linkInterface(methodPool);
          }
        });
      }
    };
  }

  private void publicizeType(DexType type) {
    DexClass clazz = application.definitionFor(type);
    if (clazz != null && clazz.isProgramClass()) {
      clazz.accessFlags.promoteToPublic();
      clazz.forEachField(field -> field.accessFlags.promoteToPublic());
      Set<DexEncodedMethod> privateInstanceEncodedMethods = new LinkedHashSet<>();
      clazz.forEachMethod(encodedMethod -> {
        if (publicizeMethod(clazz, encodedMethod)) {
          privateInstanceEncodedMethods.add(encodedMethod);
        }
      });
      if (!privateInstanceEncodedMethods.isEmpty()) {
        clazz.virtualizeMethods(privateInstanceEncodedMethods);
      }
    }

    type.forAllExtendsSubtypes(this::publicizeType);
  }

  private boolean publicizeMethod(DexClass holder, DexEncodedMethod encodedMethod) {
    MethodAccessFlags accessFlags = encodedMethod.accessFlags;
    if (accessFlags.isPublic()) {
      return false;
    }

    if (appInfo.dexItemFactory.isClassConstructor(encodedMethod.method)) {
      return false;
    }

    if (!accessFlags.isPrivate()) {
      accessFlags.unsetProtected();
      accessFlags.setPublic();
      return false;
    }
    assert accessFlags.isPrivate();

    if (appInfo.dexItemFactory.isConstructor(encodedMethod.method)) {
      // TODO(b/72211928)
      return false;
    }

    if (!accessFlags.isStatic()) {
      // If this method is mentioned in keep rules, do not transform (rule applications changed).
      if (rootSet.noShrinking.containsKey(encodedMethod)) {
        return false;
      }

      // We can't publicize private instance methods in interfaces or methods that are copied from
      // interfaces to lambda-desugared classes because this will be added as a new default method.
      // TODO(b/111118390): It might be possible to transform it into static methods, though.
      if (holder.isInterface() || accessFlags.isSynthetic()) {
        return false;
      }

      MethodPool methodPool = methodPools.get(holder);
      Wrapper<DexMethod> key = equivalence.wrap(encodedMethod.method);
      if (methodPool.hasSeen(key)) {
        // We can't do anything further because even renaming is not allowed due to the keep rule.
        if (rootSet.noObfuscation.contains(encodedMethod)) {
          return false;
        }
        // TODO(b/111118390): Renaming will enable more private instance methods to be publicized.
        return false;
      }
      methodPool.seen(key);
      lenseBuilder.add(encodedMethod.method);
      accessFlags.unsetPrivate();
      accessFlags.setFinal();
      accessFlags.setPublic();
      // Although the current method became public, it surely has the single virtual target.
      encodedMethod.method.setSingleVirtualMethodCache(
          encodedMethod.method.getHolder(), encodedMethod);
      encodedMethod.markPublicized();
      return true;
    }

    // For private static methods we can just relax the access to private, since
    // even though JLS prevents from declaring static method in derived class if
    // an instance method with same signature exists in superclass, JVM actually
    // does not take into account access of the static methods.
    accessFlags.unsetPrivate();
    accessFlags.setPublic();
    return false;
  }

  // Per-class collection of method signatures, which will be used to determine if a certain method
  // can be publicized or not.
  static class MethodPool {
    private MethodPool superType;
    private final Set<MethodPool> interfaces = new HashSet<>();
    private final Set<MethodPool> subTypes = new HashSet<>();
    private final Set<Wrapper<DexMethod>> methodPool = new HashSet<>();

    MethodPool() {
    }

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

    synchronized void seen(Wrapper<DexMethod> method) {
      boolean added = methodPool.add(method);
      assert added;
    }

    boolean hasSeen(Wrapper<DexMethod> method) {
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
