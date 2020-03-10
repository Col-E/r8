package com.android.tools.r8.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class MethodCollection {

  private final DexClass holder;
  private final MethodArrayBacking backing = new MethodArrayBacking();
  private Optional<DexEncodedMethod> cachedClassInitializer = null;

  public MethodCollection(DexClass holder) {
    this.holder = holder;
  }

  public int size() {
    return backing.size();
  }

  public void forEachMethod(Consumer<DexEncodedMethod> consumer) {
    backing.forEachMethod(consumer);
  }

  public List<DexEncodedMethod> allMethodsSorted() {
    List<DexEncodedMethod> sorted = new ArrayList<>(size());
    forEachMethod(sorted::add);
    sorted.sort((a, b) -> a.method.slowCompareTo(b.method));
    return sorted;
  }

  public List<DexEncodedMethod> directMethods() {
    return backing.directMethods();
  }

  public List<DexEncodedMethod> virtualMethods() {
    return backing.virtualMethods();
  }

  public DexEncodedMethod getMethod(DexMethod method) {
    return backing.getMethod(method);
  }

  public DexEncodedMethod getDirectMethod(DexMethod method) {
    return backing.getDirectMethod(method);
  }

  public DexEncodedMethod getDirectMethod(Predicate<DexEncodedMethod> predicate) {
    return backing.getDirectMethod(predicate);
  }

  public DexEncodedMethod getVirtualMethod(DexMethod method) {
    return backing.getVirtualMethod(method);
  }

  public DexEncodedMethod getVirtualMethod(Predicate<DexEncodedMethod> predicate) {
    return backing.getVirtualMethod(predicate);
  }

  public DexEncodedMethod getClassInitializer() {
    if (cachedClassInitializer == null) {
      cachedClassInitializer = Optional.empty();
      for (DexEncodedMethod directMethod : directMethods()) {
        if (directMethod.isClassInitializer()) {
          cachedClassInitializer = Optional.of(directMethod);
          break;
        }
      }
    }
    return cachedClassInitializer.orElse(null);
  }

  public void addMethod(DexEncodedMethod method) {
    backing.addMethod(method);
  }

  public void addVirtualMethod(DexEncodedMethod virtualMethod) {
    backing.addVirtualMethod(virtualMethod);
  }

  public void addDirectMethod(DexEncodedMethod directMethod) {
    backing.addDirectMethod(directMethod);
  }

  public void appendDirectMethod(DexEncodedMethod method) {
    assert verifyCorrectnessOfMethodHolder(method);
    cachedClassInitializer = null;
    backing.appendDirectMethod(method);
  }

  public void appendDirectMethods(Collection<DexEncodedMethod> methods) {
    assert verifyCorrectnessOfMethodHolders(methods);
    cachedClassInitializer = null;
    backing.appendDirectMethods(methods);
  }

  public void removeDirectMethod(int index) {
    cachedClassInitializer = null;
    backing.removeDirectMethod(index);
  }

  public void removeDirectMethod(DexMethod method) {
    backing.removeDirectMethod(method);
  }

  public void setDirectMethod(int index, DexEncodedMethod method) {
    assert verifyCorrectnessOfMethodHolder(method);
    cachedClassInitializer = null;
    backing.setDirectMethod(index, method);
  }

  public void setDirectMethods(DexEncodedMethod[] methods) {
    assert verifyCorrectnessOfMethodHolders(methods);
    cachedClassInitializer = null;
    backing.setDirectMethods(methods);
  }

  public void appendVirtualMethod(DexEncodedMethod method) {
    assert verifyCorrectnessOfMethodHolder(method);
    backing.appendVirtualMethod(method);
  }

  public void appendVirtualMethods(Collection<DexEncodedMethod> methods) {
    assert verifyCorrectnessOfMethodHolders(methods);
    backing.appendVirtualMethods(methods);
  }

  public void setVirtualMethod(int index, DexEncodedMethod method) {
    assert verifyCorrectnessOfMethodHolder(method);
    backing.setVirtualMethod(index, method);
  }

  public void setVirtualMethods(DexEncodedMethod[] methods) {
    assert verifyCorrectnessOfMethodHolders(methods);
    backing.setVirtualMethods(methods);
  }

  public void virtualizeMethods(Set<DexEncodedMethod> privateInstanceMethods) {
    backing.virtualizeMethods(privateInstanceMethods);
  }

  public boolean hasAnnotations() {
    return backing.hasAnnotations();
  }

  public boolean isSorted() {
    return backing.isSorted();
  }

  public void sort() {
    backing.sort();
  }

  public boolean verify() {
    forEachMethod(
        method -> {
          assert verifyCorrectnessOfMethodHolder(method);
        });
    assert backing.verifyNoDuplicateMethods();
    return true;
  }

  private boolean verifyCorrectnessOfMethodHolder(DexEncodedMethod method) {
    assert method.method.holder == holder.type
        : "Expected method `"
            + method.method.toSourceString()
            + "` to have holder `"
            + holder.type.toSourceString()
            + "`";
    return true;
  }

  private boolean verifyCorrectnessOfMethodHolders(DexEncodedMethod[] methods) {
    if (methods == null) {
      return true;
    }
    return verifyCorrectnessOfMethodHolders(Arrays.asList(methods));
  }

  private boolean verifyCorrectnessOfMethodHolders(Iterable<DexEncodedMethod> methods) {
    for (DexEncodedMethod method : methods) {
      assert verifyCorrectnessOfMethodHolder(method);
    }
    return true;
  }
}
