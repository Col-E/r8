// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.utils.TraversalContinuation;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class MethodCollectionConcurrencyChecked extends MethodCollection {
  private AtomicInteger readCount = new AtomicInteger();
  private AtomicInteger writeCount = new AtomicInteger();

  MethodCollectionConcurrencyChecked(DexClass holder, MethodCollectionBacking backing) {
    super(holder, backing);
  }

  private boolean assertReadEntry() {
    assert writeCount.get() == 0;
    assert readCount.incrementAndGet() >= 1;
    return true;
  }

  private boolean assertReadExit() {
    assert readCount.decrementAndGet() >= 0;
    assert writeCount.get() == 0;
    return true;
  }

  private boolean assertWriteEntry() {
    assert readCount.get() == 0;
    assert writeCount.incrementAndGet() == 1;
    return true;
  }

  private boolean assertWriteExit() {
    assert writeCount.decrementAndGet() == 0;
    assert readCount.get() == 0;
    return true;
  }

  @Override
  public boolean hasDirectMethods(Predicate<DexEncodedMethod> predicate) {
    assert assertReadEntry();
    boolean result = super.getDirectMethod(predicate) != null;
    assert assertReadExit();
    return result;
  }

  @Override
  public boolean hasVirtualMethods(Predicate<DexEncodedMethod> predicate) {
    assert assertReadEntry();
    boolean result = super.getVirtualMethod(predicate) != null;
    assert assertReadExit();
    return result;
  }

  @Override
  public int numberOfDirectMethods() {
    assert assertReadEntry();
    int result = super.numberOfDirectMethods();
    assert assertReadExit();
    return result;
  }

  @Override
  public int numberOfVirtualMethods() {
    assert assertReadEntry();
    int result = super.numberOfVirtualMethods();
    assert assertReadExit();
    return result;
  }

  @Override
  public int size() {
    assert assertReadEntry();
    int result = super.size();
    assert assertReadExit();
    return result;
  }

  @Override
  public TraversalContinuation<?> traverse(
      Function<DexEncodedMethod, TraversalContinuation<?>> fn) {
    assert assertReadEntry();
    TraversalContinuation<?> result = super.traverse(fn);
    assert assertReadExit();
    return result;
  }

  @Override
  public void forEachMethodMatching(
      Predicate<DexEncodedMethod> predicate, Consumer<DexEncodedMethod> consumer) {
    assert assertReadEntry();
    super.forEachMethodMatching(predicate, consumer);
    assert assertReadExit();
  }

  @Override
  public void forEachDirectMethodMatching(
      Predicate<DexEncodedMethod> predicate, Consumer<DexEncodedMethod> consumer) {
    assert assertReadEntry();
    super.forEachDirectMethodMatching(predicate, consumer);
    assert assertReadExit();
  }

  @Override
  public void forEachVirtualMethodMatching(
      Predicate<DexEncodedMethod> predicate, Consumer<DexEncodedMethod> consumer) {
    assert assertReadEntry();
    super.forEachVirtualMethodMatching(predicate, consumer);
    assert assertReadExit();
  }

  @Override
  public Iterable<DexEncodedMethod> methods() {
    // TODO(sgjesse): Maybe wrap in an iterator that checks a modification counter.
    return super.methods();
  }

  @Override
  public Iterable<DexEncodedMethod> directMethods() {
    // TODO(sgjesse): Maybe wrap in an iterator that checks a modification counter.
    return super.directMethods();
  }

  @Override
  public Iterable<DexEncodedMethod> virtualMethods() {
    // TODO(sgjesse): Maybe wrap in an iterator that checks a modification counter.
    return super.virtualMethods();
  }

  @Override
  public DexEncodedMethod getMethod(DexMethod method) {
    assert assertReadEntry();
    DexEncodedMethod result = super.getMethod(method);
    assert assertReadExit();
    return result;
  }

  @Override
  public DexEncodedMethod getMethod(Predicate<DexEncodedMethod> predicate) {
    assert assertReadEntry();
    DexEncodedMethod result = super.getMethod(predicate);
    assert assertReadExit();
    return result;
  }

  @Override
  public DexEncodedMethod getDirectMethod(DexMethod method) {
    assert assertReadEntry();
    DexEncodedMethod result = super.getDirectMethod(method);
    assert assertReadExit();
    return result;
  }

  @Override
  public DexEncodedMethod getDirectMethod(Predicate<DexEncodedMethod> predicate) {
    assert assertReadEntry();
    DexEncodedMethod result = super.getDirectMethod(predicate);
    assert assertReadExit();
    return result;
  }

  @Override
  public DexEncodedMethod getVirtualMethod(DexMethod method) {
    assert assertReadEntry();
    DexEncodedMethod result = super.getVirtualMethod(method);
    assert assertReadExit();
    return result;
  }

  @Override
  public DexEncodedMethod getVirtualMethod(Predicate<DexEncodedMethod> predicate) {
    assert assertReadEntry();
    DexEncodedMethod result = super.getVirtualMethod(predicate);
    assert assertReadExit();
    return result;
  }

  @Override
  public void addMethod(DexEncodedMethod method) {
    assert assertWriteEntry();
    super.addMethod(method);
    assert assertWriteExit();
  }

  @Override
  public void addVirtualMethod(DexEncodedMethod virtualMethod) {
    assert assertWriteEntry();
    super.addVirtualMethod(virtualMethod);
    assert assertWriteExit();
  }

  @Override
  public void addDirectMethod(DexEncodedMethod directMethod) {
    assert assertWriteEntry();
    super.addDirectMethod(directMethod);
    assert assertWriteExit();
  }

  @Override
  public DexEncodedMethod replaceDirectMethod(
      DexMethod method, Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    assert assertWriteEntry();
    DexEncodedMethod result = super.replaceDirectMethod(method, replacement);
    assert assertWriteExit();
    return result;
  }

  @Override
  public DexEncodedMethod replaceVirtualMethod(
      DexMethod method, Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    assert assertWriteEntry();
    DexEncodedMethod result = super.replaceVirtualMethod(method, replacement);
    assert assertWriteExit();
    return result;
  }

  @Override
  public void replaceMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    assert assertWriteEntry();
    super.replaceMethods(replacement);
    assert assertWriteExit();
  }

  @Override
  public void replaceDirectMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    assert assertWriteEntry();
    super.replaceDirectMethods(replacement);
    assert assertWriteExit();
  }

  @Override
  public void replaceVirtualMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    assert assertWriteEntry();
    super.replaceVirtualMethods(replacement);
    assert assertWriteExit();
  }

  @Override
  public void replaceAllDirectMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    assert assertWriteEntry();
    super.replaceAllDirectMethods(replacement);
    assert assertWriteExit();
  }

  @Override
  public void replaceAllVirtualMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    assert assertWriteEntry();
    super.replaceAllVirtualMethods(replacement);
    assert assertWriteExit();
  }

  @Override
  public DexEncodedMethod replaceDirectMethodWithVirtualMethod(
      DexMethod method, Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    assert assertWriteEntry();
    DexEncodedMethod result = super.replaceDirectMethodWithVirtualMethod(method, replacement);
    assert assertWriteExit();
    return result;
  }

  @Override
  public void addDirectMethods(Collection<DexEncodedMethod> methods) {
    assert assertWriteEntry();
    super.addDirectMethods(methods);
    assert assertWriteExit();
  }

  @Override
  public void clearDirectMethods() {
    assert assertWriteEntry();
    super.clearDirectMethods();
    assert assertWriteExit();
  }

  @Override
  public DexEncodedMethod removeMethod(DexMethod method) {
    assert assertWriteEntry();
    DexEncodedMethod result = super.removeMethod(method);
    assert assertWriteExit();
    return result;
  }

  @Override
  public void removeMethods(Set<DexEncodedMethod> methods) {
    assert assertWriteEntry();
    super.removeMethods(methods);
    assert assertWriteExit();
  }

  @Override
  public void setDirectMethods(DexEncodedMethod[] methods) {
    assert assertWriteEntry();
    super.setDirectMethods(methods);
    assert assertWriteExit();
  }

  @Override
  public void addVirtualMethods(Collection<DexEncodedMethod> methods) {
    assert assertWriteEntry();
    super.addVirtualMethods(methods);
    assert assertWriteExit();
  }

  @Override
  public void clearVirtualMethods() {
    assert assertWriteEntry();
    super.clearVirtualMethods();
    assert assertWriteExit();
  }

  @Override
  public void setVirtualMethods(DexEncodedMethod[] methods) {
    assert assertWriteEntry();
    super.setVirtualMethods(methods);
    assert assertWriteExit();
  }

  @Override
  public void virtualizeMethods(Set<DexEncodedMethod> privateInstanceMethods) {
    assert assertWriteEntry();
    super.virtualizeMethods(privateInstanceMethods);
    assert assertWriteExit();
  }

  @Override
  public void useSortedBacking() {
    assert assertWriteEntry();
    super.useSortedBacking();
    assert assertWriteExit();
  }
}
