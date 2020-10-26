// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.utils.TraversalContinuation;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class MethodCollectionBacking {

  // Internal consistency.

  abstract boolean verify();

  boolean belongsToDirectPool(DexEncodedMethod method) {
    return method.accessFlags.isStatic()
        || method.accessFlags.isPrivate()
        || method.accessFlags.isConstructor();
  }

  boolean belongsToVirtualPool(DexEncodedMethod method) {
    return !belongsToDirectPool(method);
  }

  // Collection methods.

  abstract int numberOfDirectMethods();

  abstract int numberOfVirtualMethods();

  abstract int size();

  // Traversal methods.

  abstract TraversalContinuation traverse(Function<DexEncodedMethod, TraversalContinuation> fn);

  void forEachMethod(Consumer<DexEncodedMethod> fn) {
    forEachMethod(fn, alwaysTrue());
  }

  void forEachMethod(Consumer<DexEncodedMethod> fn, Predicate<DexEncodedMethod> predicate) {
    traverse(
        method -> {
          if (predicate.test(method)) {
            fn.accept(method);
          }
          return TraversalContinuation.CONTINUE;
        });
  }

  void forEachDirectMethod(Consumer<DexEncodedMethod> fn) {
    forEachMethod(fn, this::belongsToDirectPool);
  }

  void forEachVirtualMethod(Consumer<DexEncodedMethod> fn) {
    forEachMethod(fn, this::belongsToVirtualPool);
  }

  abstract Iterable<DexEncodedMethod> methods();

  abstract Iterable<DexEncodedMethod> directMethods();

  abstract Iterable<DexEncodedMethod> virtualMethods();

  // Lookup methods.

  abstract DexEncodedMethod getMethod(DexMethod method);

  abstract DexEncodedMethod getDirectMethod(DexMethod method);

  abstract DexEncodedMethod getDirectMethod(Predicate<DexEncodedMethod> predicate);

  abstract DexEncodedMethod getVirtualMethod(DexMethod method);

  abstract DexEncodedMethod getVirtualMethod(Predicate<DexEncodedMethod> predicate);

  // Amendment methods.

  abstract void addMethod(DexEncodedMethod method);

  abstract void addDirectMethod(DexEncodedMethod method);

  abstract void addVirtualMethod(DexEncodedMethod method);

  abstract void addDirectMethods(Collection<DexEncodedMethod> methods);

  abstract void addVirtualMethods(Collection<DexEncodedMethod> methods);

  // Removal methods.

  abstract void clearDirectMethods();

  abstract void clearVirtualMethods();

  abstract DexEncodedMethod removeMethod(DexMethod method);

  abstract void removeMethods(Set<DexEncodedMethod> method);

  // Replacement/mutation methods.

  abstract void setDirectMethods(DexEncodedMethod[] methods);

  abstract void setVirtualMethods(DexEncodedMethod[] methods);

  abstract void replaceMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement);

  abstract void replaceDirectMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement);

  abstract void replaceAllDirectMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement);

  abstract void replaceVirtualMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement);

  abstract void replaceAllVirtualMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement);

  abstract DexEncodedMethod replaceDirectMethod(
      DexMethod method, Function<DexEncodedMethod, DexEncodedMethod> replacement);

  abstract DexEncodedMethod replaceVirtualMethod(
      DexMethod method, Function<DexEncodedMethod, DexEncodedMethod> replacement);

  abstract DexEncodedMethod replaceDirectMethodWithVirtualMethod(
      DexMethod method, Function<DexEncodedMethod, DexEncodedMethod> replacement);

  abstract void virtualizeMethods(Set<DexEncodedMethod> privateInstanceMethods);
}
