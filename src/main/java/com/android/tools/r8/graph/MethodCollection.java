package com.android.tools.r8.graph;

import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
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

  public TraversalContinuation traverse(Function<DexEncodedMethod, TraversalContinuation> fn) {
    return backing.traverse(fn);
  }

  public void forEachMethod(Consumer<DexEncodedMethod> consumer) {
    backing.forEachMethod(consumer);
  }

  public Iterable<DexEncodedMethod> methods() {
    return backing.methods();
  }

  public Iterable<DexEncodedMethod> methods(Predicate<DexEncodedMethod> predicate) {
    return IterableUtils.filter(methods(), predicate);
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
    cachedClassInitializer = null;
    backing.addDirectMethod(directMethod);
  }

  public DexEncodedMethod replaceDirectMethod(
      DexMethod method, Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    cachedClassInitializer = null;
    return backing.replaceDirectMethod(method, replacement);
  }

  public void replaceMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    backing.replaceMethods(replacement);
  }

  public void replaceVirtualMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    backing.replaceVirtualMethods(replacement);
  }

  public void replaceDirectMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    cachedClassInitializer = null;
    backing.replaceDirectMethods(replacement);
  }

  /**
   * Replace a direct method, if found, by a computed virtual method using the replacement function.
   *
   * @param method Direct method to replace if present.
   * @param replacement Replacement function computing the virtual replacement.
   * @return Returns the replacement if found, null otherwise.
   */
  public DexEncodedMethod replaceDirectMethodWithVirtualMethod(
      DexMethod method, Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    // The class initializer can never by converted to a virtual.
    return backing.replaceDirectMethodWithVirtualMethod(method, replacement);
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

  public void removeDirectMethod(DexMethod method) {
    cachedClassInitializer = null;
    backing.removeDirectMethod(method);
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

  public void setVirtualMethods(DexEncodedMethod[] methods) {
    assert verifyCorrectnessOfMethodHolders(methods);
    backing.setVirtualMethods(methods);
  }

  public void virtualizeMethods(Set<DexEncodedMethod> privateInstanceMethods) {
    backing.virtualizeMethods(privateInstanceMethods);
  }

  public boolean hasAnnotations() {
    return traverse(
            method ->
                method.hasAnnotation()
                    ? TraversalContinuation.BREAK
                    : TraversalContinuation.CONTINUE)
        .shouldBreak();
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
