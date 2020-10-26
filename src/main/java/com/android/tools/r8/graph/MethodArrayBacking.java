// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.utils.PredicateUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class MethodArrayBacking extends MethodCollectionBacking {

  private DexEncodedMethod[] directMethods = DexEncodedMethod.EMPTY_ARRAY;
  private DexEncodedMethod[] virtualMethods = DexEncodedMethod.EMPTY_ARRAY;

  private boolean verifyNoDuplicateMethods() {
    Set<DexMethod> unique = Sets.newIdentityHashSet();
    forEachMethod(
        method -> {
          boolean changed = unique.add(method.method);
          assert changed : "Duplicate method `" + method.method.toSourceString() + "`";
        });
    return true;
  }

  @Override
  boolean verify() {
    assert verifyNoDuplicateMethods();
    return true;
  }

  @Override
  public int numberOfDirectMethods() {
    return directMethods.length;
  }

  @Override
  public int numberOfVirtualMethods() {
    return virtualMethods.length;
  }

  @Override
  int size() {
    return directMethods.length + virtualMethods.length;
  }

  @Override
  TraversalContinuation traverse(Function<DexEncodedMethod, TraversalContinuation> fn) {
    for (DexEncodedMethod method : directMethods) {
      TraversalContinuation stepResult = fn.apply(method);
      if (stepResult.shouldBreak()) {
        return stepResult;
      }
    }
    for (DexEncodedMethod method : virtualMethods) {
      TraversalContinuation stepResult = fn.apply(method);
      if (stepResult.shouldBreak()) {
        return stepResult;
      }
    }
    return TraversalContinuation.CONTINUE;
  }

  @Override
  public Iterable<DexEncodedMethod> methods() {
    return Iterables.concat(Arrays.asList(directMethods), Arrays.asList(virtualMethods));
  }

  @Override
  List<DexEncodedMethod> directMethods() {
    assert directMethods != null;
    return Arrays.asList(directMethods);
  }

  @Override
  void addDirectMethods(Collection<DexEncodedMethod> methods) {
    DexEncodedMethod[] newMethods = new DexEncodedMethod[directMethods.length + methods.size()];
    System.arraycopy(directMethods, 0, newMethods, 0, directMethods.length);
    int i = directMethods.length;
    for (DexEncodedMethod method : methods) {
      newMethods[i] = method;
      i++;
    }
    directMethods = newMethods;
    assert verifyNoDuplicateMethods();
  }

  @Override
  void clearDirectMethods() {
    directMethods = DexEncodedMethod.EMPTY_ARRAY;
  }

  @Override
  DexEncodedMethod removeMethod(DexMethod method) {
    DexEncodedMethod removedDirectMethod =
        removeMethodHelper(
            method, directMethods, newDirectMethods -> directMethods = newDirectMethods);
    if (removedDirectMethod != null) {
      assert belongsToDirectPool(removedDirectMethod);
      return removedDirectMethod;
    }
    DexEncodedMethod removedVirtualMethod =
        removeMethodHelper(
            method, virtualMethods, newVirtualMethods -> virtualMethods = newVirtualMethods);
    assert removedVirtualMethod == null || belongsToVirtualPool(removedVirtualMethod);
    return removedVirtualMethod;
  }

  private DexEncodedMethod removeMethodHelper(
      DexMethod method,
      DexEncodedMethod[] methods,
      Consumer<DexEncodedMethod[]> newMethodsConsumer) {
    for (int i = 0; i < methods.length; i++) {
      if (method.match(methods[i])) {
        return removeMethodWithIndex(i, methods, newMethodsConsumer);
      }
    }
    return null;
  }

  @Override
  void removeMethods(Set<DexEncodedMethod> methods) {
    directMethods = removeMethodsHelper(methods, directMethods);
    virtualMethods = removeMethodsHelper(methods, virtualMethods);
  }

  private static DexEncodedMethod[] removeMethodsHelper(
      Set<DexEncodedMethod> methodsToRemove, DexEncodedMethod[] existingMethods) {
    List<DexEncodedMethod> newMethods = new ArrayList<>(existingMethods.length);
    for (DexEncodedMethod method : existingMethods) {
      if (!methodsToRemove.contains(method)) {
        newMethods.add(method);
      }
    }
    return newMethods.toArray(DexEncodedMethod.EMPTY_ARRAY);
  }

  private DexEncodedMethod removeMethodWithIndex(
      int index, DexEncodedMethod[] methods, Consumer<DexEncodedMethod[]> newMethodsConsumer) {
    DexEncodedMethod removed = methods[index];
    DexEncodedMethod[] newMethods = new DexEncodedMethod[methods.length - 1];
    System.arraycopy(methods, 0, newMethods, 0, index);
    System.arraycopy(methods, index + 1, newMethods, index, methods.length - index - 1);
    newMethodsConsumer.accept(newMethods);
    return removed;
  }

  @Override
  void setDirectMethods(DexEncodedMethod[] methods) {
    directMethods = MoreObjects.firstNonNull(methods, DexEncodedMethod.EMPTY_ARRAY);
    assert verifyNoDuplicateMethods();
  }

  @Override
  List<DexEncodedMethod> virtualMethods() {
    assert virtualMethods != null;
    return Arrays.asList(virtualMethods);
  }

  @Override
  void addVirtualMethods(Collection<DexEncodedMethod> methods) {
    DexEncodedMethod[] newMethods = new DexEncodedMethod[virtualMethods.length + methods.size()];
    System.arraycopy(virtualMethods, 0, newMethods, 0, virtualMethods.length);
    int i = virtualMethods.length;
    for (DexEncodedMethod method : methods) {
      newMethods[i] = method;
      i++;
    }
    virtualMethods = newMethods;
    assert verifyNoDuplicateMethods();
  }

  @Override
  void clearVirtualMethods() {
    virtualMethods = DexEncodedMethod.EMPTY_ARRAY;
  }

  @Override
  void setVirtualMethods(DexEncodedMethod[] methods) {
    virtualMethods = MoreObjects.firstNonNull(methods, DexEncodedMethod.EMPTY_ARRAY);
    assert verifyNoDuplicateMethods();
  }

  @Override
  void virtualizeMethods(Set<DexEncodedMethod> privateInstanceMethods) {
    int vLen = virtualMethods.length;
    int dLen = directMethods.length;
    int mLen = privateInstanceMethods.size();
    assert mLen <= dLen;

    DexEncodedMethod[] newDirectMethods = new DexEncodedMethod[dLen - mLen];
    int index = 0;
    for (int i = 0; i < dLen; i++) {
      DexEncodedMethod encodedMethod = directMethods[i];
      if (!privateInstanceMethods.contains(encodedMethod)) {
        newDirectMethods[index++] = encodedMethod;
      }
    }
    assert index == dLen - mLen;
    setDirectMethods(newDirectMethods);

    DexEncodedMethod[] newVirtualMethods = new DexEncodedMethod[vLen + mLen];
    System.arraycopy(virtualMethods, 0, newVirtualMethods, 0, vLen);
    index = vLen;
    for (DexEncodedMethod encodedMethod : privateInstanceMethods) {
      newVirtualMethods[index++] = encodedMethod;
    }
    setVirtualMethods(newVirtualMethods);
  }

  @Override
  DexEncodedMethod getDirectMethod(DexMethod method) {
    for (DexEncodedMethod directMethod : directMethods) {
      if (method.match(directMethod)) {
        return directMethod;
      }
    }
    return null;
  }

  @Override
  DexEncodedMethod getDirectMethod(Predicate<DexEncodedMethod> predicate) {
    return PredicateUtils.findFirst(directMethods, predicate);
  }

  @Override
  DexEncodedMethod getVirtualMethod(DexMethod method) {
    for (DexEncodedMethod virtualMethod : virtualMethods) {
      if (method.match(virtualMethod)) {
        return virtualMethod;
      }
    }
    return null;
  }

  @Override
  DexEncodedMethod getVirtualMethod(Predicate<DexEncodedMethod> predicate) {
    return PredicateUtils.findFirst(virtualMethods, predicate);
  }

  @Override
  DexEncodedMethod getMethod(DexMethod method) {
    DexEncodedMethod result = getDirectMethod(method);
    return result == null ? getVirtualMethod(method) : result;
  }

  @Override
  void addMethod(DexEncodedMethod method) {
    if (belongsToDirectPool(method)) {
      addDirectMethod(method);
    } else {
      addVirtualMethod(method);
    }
  }

  @Override
  void addVirtualMethod(DexEncodedMethod virtualMethod) {
    assert belongsToVirtualPool(virtualMethod);
    virtualMethods = Arrays.copyOf(virtualMethods, virtualMethods.length + 1);
    virtualMethods[virtualMethods.length - 1] = virtualMethod;
  }

  @Override
  void addDirectMethod(DexEncodedMethod directMethod) {
    assert belongsToDirectPool(directMethod);
    directMethods = Arrays.copyOf(directMethods, directMethods.length + 1);
    directMethods[directMethods.length - 1] = directMethod;
  }

  @Override
  public DexEncodedMethod replaceDirectMethod(
      DexMethod method, Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    DexEncodedMethod newMethod = replaceMethod(method, replacement, directMethods);
    assert newMethod == null || belongsToDirectPool(newMethod);
    return newMethod;
  }

  @Override
  public DexEncodedMethod replaceVirtualMethod(
      DexMethod method, Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    DexEncodedMethod newMethod = replaceMethod(method, replacement, virtualMethods);
    assert newMethod == null || belongsToVirtualPool(newMethod);
    return newMethod;
  }

  private DexEncodedMethod replaceMethod(
      DexMethod reference,
      Function<DexEncodedMethod, DexEncodedMethod> replacement,
      DexEncodedMethod[] methods) {
    for (int i = 0; i < methods.length; i++) {
      DexEncodedMethod method = methods[i];
      if (reference.match(method)) {
        DexEncodedMethod newMethod = replacement.apply(method);
        methods[i] = newMethod;
        return newMethod;
      }
    }
    return null;
  }

  @Override
  public DexEncodedMethod replaceDirectMethodWithVirtualMethod(
      DexMethod method, Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    for (int i = 0; i < directMethods.length; i++) {
      DexEncodedMethod directMethod = directMethods[i];
      if (method.match(directMethod)) {
        DexEncodedMethod newMethod = replacement.apply(directMethod);
        assert belongsToVirtualPool(newMethod);
        removeMethodWithIndex(
            i, directMethods, newDirectMethods -> directMethods = newDirectMethods);
        addVirtualMethod(newMethod);
        return newMethod;
      }
    }
    return null;
  }

  @Override
  public void replaceMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    replaceDirectMethods(replacement);
    replaceVirtualMethods(replacement);
  }

  @Override
  public void replaceDirectMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    for (int i = 0; i < directMethods.length; i++) {
      DexEncodedMethod method = directMethods[i];
      DexEncodedMethod newMethod = replacement.apply(method);
      assert newMethod != null;
      if (method != newMethod) {
        assert belongsToDirectPool(newMethod);
        directMethods[i] = newMethod;
      }
    }
  }

  @Override
  public void replaceVirtualMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    for (int i = 0; i < virtualMethods.length; i++) {
      DexEncodedMethod method = virtualMethods[i];
      DexEncodedMethod newMethod = replacement.apply(method);
      if (method != newMethod) {
        assert belongsToVirtualPool(newMethod);
        virtualMethods[i] = newMethod;
      }
    }
  }

  @Override
  public void replaceAllDirectMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    DexEncodedMethod[] oldMethods = directMethods;
    clearDirectMethods();
    DexEncodedMethod[] newMethods = new DexEncodedMethod[oldMethods.length];
    for (int i = 0; i < oldMethods.length; i++) {
      newMethods[i] = replacement.apply(oldMethods[i]);
    }
    directMethods = newMethods;
  }

  @Override
  public void replaceAllVirtualMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    DexEncodedMethod[] oldMethods = virtualMethods;
    clearVirtualMethods();
    DexEncodedMethod[] newMethods = new DexEncodedMethod[oldMethods.length];
    for (int i = 0; i < oldMethods.length; i++) {
      newMethods[i] = replacement.apply(oldMethods[i]);
    }
    virtualMethods = newMethods;
  }
}
