// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ProgramMethodSet implements Iterable<ProgramMethod> {

  private static final ProgramMethodSet EMPTY = new ProgramMethodSet(ImmutableMap::of);

  private final Map<DexMethod, ProgramMethod> backing;
  private final Supplier<? extends Map<DexMethod, ProgramMethod>> backingFactory;

  protected ProgramMethodSet(Supplier<? extends Map<DexMethod, ProgramMethod>> backingFactory) {
    this(backingFactory, backingFactory.get());
  }

  protected ProgramMethodSet(
      Supplier<? extends Map<DexMethod, ProgramMethod>> backingFactory,
      Map<DexMethod, ProgramMethod> backing) {
    this.backing = backing;
    this.backingFactory = backingFactory;
  }

  public static ProgramMethodSet create() {
    return new ProgramMethodSet(IdentityHashMap::new);
  }

  public static ProgramMethodSet create(int capacity) {
    return new ProgramMethodSet(IdentityHashMap::new, new IdentityHashMap<>(capacity));
  }

  public static ProgramMethodSet create(ProgramMethod element) {
    ProgramMethodSet result = create();
    result.add(element);
    return result;
  }

  public static ProgramMethodSet createConcurrent() {
    return new ProgramMethodSet(ConcurrentHashMap::new);
  }

  public static ProgramMethodSet createLinked() {
    return new ProgramMethodSet(LinkedHashMap::new);
  }

  public static ProgramMethodSet empty() {
    return EMPTY;
  }

  public boolean add(ProgramMethod method) {
    ProgramMethod existing = backing.put(method.getReference(), method);
    assert existing == null || existing.isStructurallyEqualTo(method);
    return existing == null;
  }

  public void addAll(Iterable<ProgramMethod> methods) {
    methods.forEach(this::add);
  }

  public void addAll(ProgramMethodSet methods) {
    backing.putAll(methods.backing);
  }

  public boolean createAndAdd(DexProgramClass clazz, DexEncodedMethod definition) {
    return add(new ProgramMethod(clazz, definition));
  }

  public boolean contains(DexEncodedMethod method) {
    return backing.containsKey(method.getReference());
  }

  public boolean contains(ProgramMethod method) {
    return backing.containsKey(method.getReference());
  }

  public void clear() {
    backing.clear();
  }

  public boolean isEmpty() {
    return backing.isEmpty();
  }

  @Override
  public Iterator<ProgramMethod> iterator() {
    return backing.values().iterator();
  }

  public boolean remove(DexMethod method) {
    ProgramMethod existing = backing.remove(method);
    return existing != null;
  }

  public boolean remove(DexEncodedMethod method) {
    return remove(method.getReference());
  }

  public ProgramMethodSet rewrittenWithLens(DexDefinitionSupplier definitions, GraphLens lens) {
    ProgramMethodSet rewritten = new ProgramMethodSet(backingFactory);
    forEach(
        method -> {
          ProgramMethod newMethod = lens.mapProgramMethod(method, definitions);
          if (newMethod != null) {
            rewritten.add(newMethod);
          }
        });
    return rewritten;
  }

  public int size() {
    return backing.size();
  }

  public Stream<ProgramMethod> stream() {
    return backing.values().stream();
  }

  public Set<DexEncodedMethod> toDefinitionSet() {
    assert backing instanceof IdentityHashMap;
    Set<DexEncodedMethod> definitions = Sets.newIdentityHashSet();
    forEach(method -> definitions.add(method.getDefinition()));
    return definitions;
  }
}
