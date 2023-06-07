package com.android.tools.r8.graph;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class MethodCollection {

  @FunctionalInterface
  public interface MethodCollectionFactory {

    MethodCollection create(DexClass holder);

    static MethodCollectionFactory empty() {
      return fromMethods(DexEncodedMethod.EMPTY_ARRAY, DexEncodedMethod.EMPTY_ARRAY);
    }

    static MethodCollectionFactory fromMethods(
        DexEncodedMethod[] directs, DexEncodedMethod[] virtuals) {
      return holder -> MethodCollection.create(holder, directs, virtuals);
    }
  }

  // Threshold between using an array and a map for the backing store.
  // Compiling R8 plus library shows classes with up to 30 methods account for about 95% of classes.
  private static final int ARRAY_BACKING_THRESHOLD = 30;

  private final DexClass holder;
  private MethodCollectionBacking backing;
  private DexEncodedMethod cachedClassInitializer = DexEncodedMethod.SENTINEL;

  /** Should only be called via 'createInternal' (and the concurrency checking subtype). */
  MethodCollection(DexClass holder, MethodCollectionBacking backing) {
    this.holder = holder;
    this.backing = backing;
  }

  public static MethodCollection create(
      DexClass holder, DexEncodedMethod[] directMethods, DexEncodedMethod[] virtualMethods) {
    int methodCount = directMethods.length + virtualMethods.length;
    MethodCollectionBacking backing;
    if (methodCount > ARRAY_BACKING_THRESHOLD) {
      backing = MethodMapBacking.createLinked(methodCount);
      backing.setDirectMethods(directMethods);
      backing.setVirtualMethods(virtualMethods);
    } else {
      backing = MethodArrayBacking.fromArrays(directMethods, virtualMethods);
    }
    return createInternal(holder, backing);
  }

  private static MethodCollection createInternal(DexClass holder, MethodCollectionBacking backing) {
    return InternalOptions.USE_METHOD_COLLECTION_CONCURRENCY_CHECKED
        ? new MethodCollectionConcurrencyChecked(holder, backing)
        : new MethodCollection(holder, backing);
  }

  public MethodCollection fixup(
      DexClass newHolder, Function<DexEncodedMethod, DexEncodedMethod> fn) {
    MethodCollectionBacking newBacking = backing.map(fn);
    return createInternal(newHolder, newBacking);
  }

  private void resetCaches() {
    resetDirectMethodCaches();
    resetVirtualMethodCaches();
  }

  private void resetDirectMethodCaches() {
    resetClassInitializerCache();
  }

  private void resetVirtualMethodCaches() {
    // Nothing to do.
  }

  public boolean hasMethods(Predicate<DexEncodedMethod> predicate) {
    return getMethod(predicate) != null;
  }

  public boolean hasDirectMethods() {
    return hasDirectMethods(alwaysTrue());
  }

  public boolean hasDirectMethods(Predicate<DexEncodedMethod> predicate) {
    return backing.getDirectMethod(predicate) != null;
  }

  public boolean hasVirtualMethods() {
    return hasVirtualMethods(alwaysTrue());
  }

  public boolean hasVirtualMethods(Predicate<DexEncodedMethod> predicate) {
    return backing.getVirtualMethod(predicate) != null;
  }

  public int numberOfDirectMethods() {
    return backing.numberOfDirectMethods();
  }

  public int numberOfVirtualMethods() {
    return backing.numberOfVirtualMethods();
  }

  public int size() {
    return backing.size();
  }

  public TraversalContinuation<?, ?> traverse(
      Function<DexEncodedMethod, TraversalContinuation<?, ?>> fn) {
    return backing.traverse(fn);
  }

  public void forEachMethod(Consumer<DexEncodedMethod> consumer) {
    forEachMethodMatching(alwaysTrue(), consumer);
  }

  public void forEachMethodMatching(
      Predicate<DexEncodedMethod> predicate, Consumer<DexEncodedMethod> consumer) {
    backing.forEachMethod(
        method -> {
          if (predicate.test(method)) {
            consumer.accept(method);
          }
        });
  }

  public void forEachDirectMethod(Consumer<DexEncodedMethod> consumer) {
    forEachDirectMethodMatching(alwaysTrue(), consumer);
  }

  public void forEachDirectMethodMatching(
      Predicate<DexEncodedMethod> predicate, Consumer<DexEncodedMethod> consumer) {
    backing.forEachDirectMethod(
        method -> {
          if (predicate.test(method)) {
            consumer.accept(method);
          }
        });
  }

  public void forEachVirtualMethod(Consumer<DexEncodedMethod> consumer) {
    forEachVirtualMethodMatching(alwaysTrue(), consumer);
  }

  public void forEachVirtualMethodMatching(
      Predicate<DexEncodedMethod> predicate, Consumer<DexEncodedMethod> consumer) {
    backing.forEachVirtualMethod(
        method -> {
          if (predicate.test(method)) {
            consumer.accept(method);
          }
        });
  }

  public Iterable<DexEncodedMethod> methods() {
    return backing.methods();
  }

  public Iterable<DexEncodedMethod> methods(Predicate<? super DexEncodedMethod> predicate) {
    return IterableUtils.filter(methods(), predicate);
  }

  public List<DexEncodedMethod> allMethodsSorted() {
    List<DexEncodedMethod> sorted = new ArrayList<>(size());
    forEachMethod(sorted::add);
    sorted.sort(Comparator.comparing(DexEncodedMember::getReference));
    return sorted;
  }

  public Iterable<DexEncodedMethod> directMethods() {
    return backing.directMethods();
  }

  public Iterable<DexEncodedMethod> virtualMethods() {
    return backing.virtualMethods();
  }

  public DexEncodedMethod getMethod(DexMethod method) {
    return backing.getMethod(method.getProto(), method.getName());
  }

  public DexEncodedMethod getMethod(DexProto methodProto, DexString methodName) {
    return backing.getMethod(methodProto, methodName);
  }

  public final DexEncodedMethod getMethod(DexMethodSignature method) {
    return getMethod(method.getProto(), method.getName());
  }

  public DexEncodedMethod getMethod(Predicate<DexEncodedMethod> predicate) {
    DexEncodedMethod result = backing.getDirectMethod(predicate);
    return result != null ? result : backing.getVirtualMethod(predicate);
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

  private void resetClassInitializerCache() {
    cachedClassInitializer = DexEncodedMethod.SENTINEL;
  }

  public synchronized DexEncodedMethod getClassInitializer() {
    if (cachedClassInitializer == DexEncodedMethod.SENTINEL) {
      cachedClassInitializer = null;
      for (DexEncodedMethod directMethod : directMethods()) {
        if (directMethod.isClassInitializer()) {
          cachedClassInitializer = directMethod;
          break;
        }
      }
    }
    return cachedClassInitializer;
  }

  public void addMethod(DexEncodedMethod method) {
    resetCaches();
    backing.addMethod(method);
  }

  public void addVirtualMethod(DexEncodedMethod virtualMethod) {
    resetVirtualMethodCaches();
    backing.addVirtualMethod(virtualMethod);
  }

  public void addDirectMethod(DexEncodedMethod directMethod) {
    resetDirectMethodCaches();
    backing.addDirectMethod(directMethod);
  }

  public DexEncodedMethod replaceDirectMethod(
      DexMethod method, Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    resetDirectMethodCaches();
    return backing.replaceDirectMethod(method, replacement);
  }

  public DexEncodedMethod replaceVirtualMethod(
      DexMethod method, Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    resetVirtualMethodCaches();
    return backing.replaceVirtualMethod(method, replacement);
  }

  public void replaceMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    resetCaches();
    backing.replaceMethods(replacement);
  }

  @SuppressWarnings("unchecked")
  public <T extends DexClassAndMethod> void replaceClassAndMethods(
      Function<T, DexEncodedMethod> replacement) {
    assert holder.isProgramClass();
    replaceMethods(method -> replacement.apply((T) DexClassAndMethod.create(holder, method)));
  }

  public void replaceDirectMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    resetDirectMethodCaches();
    backing.replaceDirectMethods(replacement);
  }

  public void replaceVirtualMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    resetVirtualMethodCaches();
    backing.replaceVirtualMethods(replacement);
  }

  public void replaceAllDirectMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    resetDirectMethodCaches();
    backing.replaceAllDirectMethods(replacement);
  }

  public void replaceAllVirtualMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    resetVirtualMethodCaches();
    backing.replaceAllVirtualMethods(replacement);
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
    resetCaches();
    return backing.replaceDirectMethodWithVirtualMethod(method, replacement);
  }

  public void addDirectMethods(Collection<DexEncodedMethod> methods) {
    assert verifyCorrectnessOfMethodHolders(methods);
    resetDirectMethodCaches();
    backing.addDirectMethods(methods);
  }

  public void clearDirectMethods() {
    resetDirectMethodCaches();
    backing.clearDirectMethods();
  }

  public DexEncodedMethod removeMethod(DexMethod method) {
    DexEncodedMethod removed = backing.removeMethod(method);
    if (removed != null) {
      if (backing.belongsToDirectPool(removed)) {
        resetDirectMethodCaches();
      } else {
        assert backing.belongsToVirtualPool(removed);
        resetVirtualMethodCaches();
      }
    }
    return removed;
  }

  public void removeMethods(Set<DexEncodedMethod> methods) {
    backing.removeMethods(methods);
    resetDirectMethodCaches();
    resetVirtualMethodCaches();
  }

  public void setDirectMethods(DexEncodedMethod[] methods) {
    assert verifyCorrectnessOfMethodHolders(methods);
    resetDirectMethodCaches();
    backing.setDirectMethods(methods);
  }

  public void setSingleDirectMethod(DexEncodedMethod method) {
    setDirectMethods(new DexEncodedMethod[] {method});
  }

  public void addVirtualMethods(Collection<DexEncodedMethod> methods) {
    assert verifyCorrectnessOfMethodHolders(methods);
    resetVirtualMethodCaches();
    backing.addVirtualMethods(methods);
  }

  public void clearVirtualMethods() {
    resetVirtualMethodCaches();
    backing.clearVirtualMethods();
  }

  public void setVirtualMethods(DexEncodedMethod[] methods) {
    assert verifyCorrectnessOfMethodHolders(methods);
    resetVirtualMethodCaches();
    backing.setVirtualMethods(methods);
  }

  public void virtualizeMethods(Set<DexEncodedMethod> privateInstanceMethods) {
    resetVirtualMethodCaches();
    backing.virtualizeMethods(privateInstanceMethods);
  }

  public boolean hasAnnotations() {
    return traverse(
            method ->
                method.hasAnyAnnotations()
                    ? TraversalContinuation.doBreak()
                    : TraversalContinuation.doContinue())
        .shouldBreak();
  }

  public void useSortedBacking() {
    assert size() == 0;
    backing = MethodMapBacking.createSorted();
  }

  public boolean verify() {
    forEachMethod(
        method -> {
          assert verifyCorrectnessOfMethodHolder(method);
        });
    assert backing.verify();
    return true;
  }

  private boolean verifyCorrectnessOfMethodHolder(DexEncodedMethod method) {
    assert method.getHolderType() == holder.type
        : "Expected method `"
            + method.getReference().toSourceString()
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

  public String getBackingDescriptionString() {
    return backing.getDescriptionString();
  }
}
