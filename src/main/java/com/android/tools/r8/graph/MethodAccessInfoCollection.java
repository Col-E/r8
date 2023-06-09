// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MethodAccessInfoCollection {

  private final Map<DexMethod, ProgramMethodSet> directInvokes;
  private final Map<DexMethod, ProgramMethodSet> interfaceInvokes;
  private final Map<DexMethod, ProgramMethodSet> staticInvokes;
  private final Map<DexMethod, ProgramMethodSet> superInvokes;
  private final Map<DexMethod, ProgramMethodSet> virtualInvokes;

  private boolean destroyed = false;

  private MethodAccessInfoCollection(
      Map<DexMethod, ProgramMethodSet> directInvokes,
      Map<DexMethod, ProgramMethodSet> interfaceInvokes,
      Map<DexMethod, ProgramMethodSet> staticInvokes,
      Map<DexMethod, ProgramMethodSet> superInvokes,
      Map<DexMethod, ProgramMethodSet> virtualInvokes) {
    this.directInvokes = directInvokes;
    this.interfaceInvokes = interfaceInvokes;
    this.staticInvokes = staticInvokes;
    this.superInvokes = superInvokes;
    this.virtualInvokes = virtualInvokes;
  }

  public static ConcurrentBuilder concurrentBuilder() {
    return new ConcurrentBuilder();
  }

  public static IdentityBuilder identityBuilder() {
    return new IdentityBuilder();
  }

  public void destroy() {
    assert !destroyed;
    directInvokes.clear();
    interfaceInvokes.clear();
    staticInvokes.clear();
    superInvokes.clear();
    virtualInvokes.clear();
    destroyed = true;
  }

  public Modifier modifier() {
    assert !destroyed;
    return new Modifier(
        directInvokes, interfaceInvokes, staticInvokes, superInvokes, virtualInvokes);
  }

  public void forEachMethodReference(Consumer<DexMethod> method) {
    assert !destroyed;
    Set<DexMethod> seen = Sets.newIdentityHashSet();
    directInvokes.keySet().forEach(ConsumerUtils.acceptIfNotSeen(method, seen));
    interfaceInvokes.keySet().forEach(ConsumerUtils.acceptIfNotSeen(method, seen));
    staticInvokes.keySet().forEach(ConsumerUtils.acceptIfNotSeen(method, seen));
    superInvokes.keySet().forEach(ConsumerUtils.acceptIfNotSeen(method, seen));
    virtualInvokes.keySet().forEach(ConsumerUtils.acceptIfNotSeen(method, seen));
  }

  public void forEachDirectInvoke(BiConsumer<DexMethod, ProgramMethodSet> consumer) {
    assert !destroyed;
    directInvokes.forEach(consumer);
  }

  public void forEachInterfaceInvoke(BiConsumer<DexMethod, ProgramMethodSet> consumer) {
    assert !destroyed;
    interfaceInvokes.forEach(consumer);
  }

  public void forEachStaticInvoke(BiConsumer<DexMethod, ProgramMethodSet> consumer) {
    assert !destroyed;
    staticInvokes.forEach(consumer);
  }

  public void forEachSuperInvoke(BiConsumer<DexMethod, ProgramMethodSet> consumer) {
    assert !destroyed;
    superInvokes.forEach(consumer);
  }

  public void forEachSuperInvokeContext(DexMethod method, Consumer<ProgramMethod> consumer) {
    assert !destroyed;
    superInvokes.getOrDefault(method, ProgramMethodSet.empty()).forEach(consumer);
  }

  public void forEachVirtualInvoke(BiConsumer<DexMethod, ProgramMethodSet> consumer) {
    assert !destroyed;
    virtualInvokes.forEach(consumer);
  }

  public void forEachVirtualInvokeContext(DexMethod method, Consumer<ProgramMethod> consumer) {
    assert !destroyed;
    virtualInvokes.getOrDefault(method, ProgramMethodSet.empty()).forEach(consumer);
  }

  public MethodAccessInfoCollection rewrittenWithLens(
      DexDefinitionSupplier definitions, GraphLens lens, Timing timing) {
    timing.begin("Rewrite MethodAccessInfoCollection");
    MethodAccessInfoCollection result;
    if (destroyed) {
      result = this;
    } else {
      MethodAccessInfoCollection.Builder<?> builder = identityBuilder();
      rewriteInvokesWithLens(builder, directInvokes, definitions, lens, InvokeType.DIRECT);
      rewriteInvokesWithLens(builder, interfaceInvokes, definitions, lens, InvokeType.INTERFACE);
      rewriteInvokesWithLens(builder, staticInvokes, definitions, lens, InvokeType.STATIC);
      rewriteInvokesWithLens(builder, superInvokes, definitions, lens, InvokeType.SUPER);
      rewriteInvokesWithLens(builder, virtualInvokes, definitions, lens, InvokeType.VIRTUAL);
      result = builder.build();
    }
    timing.end();
    return result;
  }

  private static void rewriteInvokesWithLens(
      MethodAccessInfoCollection.Builder<?> builder,
      Map<DexMethod, ProgramMethodSet> invokes,
      DexDefinitionSupplier definitions,
      GraphLens lens,
      InvokeType type) {
    invokes.forEach(
        (reference, contexts) -> {
          ProgramMethodSet newContexts = contexts.rewrittenWithLens(definitions, lens);
          for (ProgramMethod newContext : newContexts) {
            MethodLookupResult methodLookupResult =
                lens.lookupMethod(reference, newContext.getReference(), type);
            DexMethod newReference = methodLookupResult.getReference();
            InvokeType newType = methodLookupResult.getType();
            builder.registerInvokeInContext(newReference, newContext, newType);
          }
        });
  }

  public MethodAccessInfoCollection withoutPrunedItems(PrunedItems prunedItems) {
    if (!destroyed) {
      pruneItems(prunedItems, directInvokes);
      pruneItems(prunedItems, interfaceInvokes);
      pruneItems(prunedItems, staticInvokes);
      pruneItems(prunedItems, superInvokes);
      pruneItems(prunedItems, virtualInvokes);
    }
    return this;
  }

  private static void pruneItems(
      PrunedItems prunedItems, Map<DexMethod, ProgramMethodSet> invokes) {
    invokes
        .values()
        .removeIf(
            contexts -> {
              contexts.removeIf(
                  context -> {
                    if (prunedItems.isRemoved(context.getReference())) {
                      return true;
                    }
                    assert prunedItems.getPrunedApp().definitionFor(context.getReference()) != null;
                    return false;
                  });
              return contexts.isEmpty();
            });
  }

  public abstract static class Builder<T extends Map<DexMethod, ProgramMethodSet>> {

    private final T directInvokes;
    private final T interfaceInvokes;
    private final T staticInvokes;
    private final T superInvokes;
    private final T virtualInvokes;

    private Builder(Supplier<T> factory) {
      this(factory.get(), factory.get(), factory.get(), factory.get(), factory.get());
    }

    private Builder(
        T directInvokes, T interfaceInvokes, T staticInvokes, T superInvokes, T virtualInvokes) {
      this.directInvokes = directInvokes;
      this.interfaceInvokes = interfaceInvokes;
      this.staticInvokes = staticInvokes;
      this.superInvokes = superInvokes;
      this.virtualInvokes = virtualInvokes;
    }

    public T getDirectInvokes() {
      return directInvokes;
    }

    public T getInterfaceInvokes() {
      return interfaceInvokes;
    }

    public T getStaticInvokes() {
      return staticInvokes;
    }

    public T getSuperInvokes() {
      return superInvokes;
    }

    public T getVirtualInvokes() {
      return virtualInvokes;
    }

    public boolean registerInvokeInContext(
        DexMethod invokedMethod, ProgramMethod context, InvokeType type) {
      switch (type) {
        case DIRECT:
          return registerInvokeDirectInContext(invokedMethod, context);
        case INTERFACE:
          return registerInvokeInterfaceInContext(invokedMethod, context);
        case STATIC:
          return registerInvokeStaticInContext(invokedMethod, context);
        case SUPER:
          return registerInvokeSuperInContext(invokedMethod, context);
        case VIRTUAL:
          return registerInvokeVirtualInContext(invokedMethod, context);
        default:
          assert false;
          return false;
      }
    }

    public boolean registerInvokeDirectInContext(DexMethod invokedMethod, ProgramMethod context) {
      return registerInvokeMethodInContext(invokedMethod, context, directInvokes);
    }

    public void registerInvokeDirectInContexts(DexMethod invokedMethod, ProgramMethodSet contexts) {
      contexts.forEach(context -> registerInvokeDirectInContext(invokedMethod, context));
    }

    public boolean registerInvokeInterfaceInContext(
        DexMethod invokedMethod, ProgramMethod context) {
      return registerInvokeMethodInContext(invokedMethod, context, interfaceInvokes);
    }

    public void registerInvokeInterfaceInContexts(
        DexMethod invokedMethod, ProgramMethodSet contexts) {
      contexts.forEach(context -> registerInvokeInterfaceInContext(invokedMethod, context));
    }

    public boolean registerInvokeStaticInContext(DexMethod invokedMethod, ProgramMethod context) {
      return registerInvokeMethodInContext(invokedMethod, context, staticInvokes);
    }

    public void registerInvokeStaticInContexts(DexMethod invokedMethod, ProgramMethodSet contexts) {
      contexts.forEach(context -> registerInvokeStaticInContext(invokedMethod, context));
    }

    public boolean registerInvokeSuperInContext(DexMethod invokedMethod, ProgramMethod context) {
      return registerInvokeMethodInContext(invokedMethod, context, superInvokes);
    }

    public void registerInvokeSuperInContexts(DexMethod invokedMethod, ProgramMethodSet contexts) {
      contexts.forEach(context -> registerInvokeSuperInContext(invokedMethod, context));
    }

    public boolean registerInvokeVirtualInContext(DexMethod invokedMethod, ProgramMethod context) {
      return registerInvokeMethodInContext(invokedMethod, context, virtualInvokes);
    }

    public void registerInvokeVirtualInContexts(
        DexMethod invokedMethod, ProgramMethodSet contexts) {
      contexts.forEach(context -> registerInvokeVirtualInContext(invokedMethod, context));
    }

    private static boolean registerInvokeMethodInContext(
        DexMethod invokedMethod, ProgramMethod context, Map<DexMethod, ProgramMethodSet> invokes) {
      return invokes
          .computeIfAbsent(invokedMethod, ignore -> ProgramMethodSet.create())
          .add(context);
    }

    public MethodAccessInfoCollection build() {
      return new MethodAccessInfoCollection(
          directInvokes, interfaceInvokes, staticInvokes, superInvokes, virtualInvokes);
    }
  }

  public static class ConcurrentBuilder
      extends Builder<ConcurrentHashMap<DexMethod, ProgramMethodSet>> {

    private ConcurrentBuilder() {
      super(ConcurrentHashMap::new);
    }
  }

  public static class IdentityBuilder
      extends Builder<IdentityHashMap<DexMethod, ProgramMethodSet>> {

    private IdentityBuilder() {
      super(IdentityHashMap::new);
    }
  }

  public static class Modifier extends Builder<Map<DexMethod, ProgramMethodSet>> {

    private Modifier(
        Map<DexMethod, ProgramMethodSet> directInvokes,
        Map<DexMethod, ProgramMethodSet> interfaceInvokes,
        Map<DexMethod, ProgramMethodSet> staticInvokes,
        Map<DexMethod, ProgramMethodSet> superInvokes,
        Map<DexMethod, ProgramMethodSet> virtualInvokes) {
      super(directInvokes, interfaceInvokes, staticInvokes, superInvokes, virtualInvokes);
    }

    public void addAll(MethodAccessInfoCollection collection) {
      collection.forEachDirectInvoke(this::registerInvokeDirectInContexts);
      collection.forEachInterfaceInvoke(this::registerInvokeInterfaceInContexts);
      collection.forEachStaticInvoke(this::registerInvokeStaticInContexts);
      collection.forEachSuperInvoke(this::registerInvokeSuperInContexts);
      collection.forEachVirtualInvoke(this::registerInvokeVirtualInContexts);
    }
  }
}
