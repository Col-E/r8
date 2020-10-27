// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramField;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class ProgramFieldSet implements Iterable<ProgramField> {

  private static final ProgramFieldSet EMPTY = new ProgramFieldSet(ImmutableMap.of());

  private Map<DexField, ProgramField> backing;

  private ProgramFieldSet(Map<DexField, ProgramField> backing) {
    this.backing = backing;
  }

  public static ProgramFieldSet create() {
    return new ProgramFieldSet(new IdentityHashMap<>());
  }

  public static ProgramFieldSet empty() {
    return EMPTY;
  }

  public boolean add(ProgramField field) {
    ProgramField existing = backing.put(field.getReference(), field);
    assert existing == null || existing.isStructurallyEqualTo(field);
    return existing == null;
  }

  public void addAll(Iterable<ProgramField> fields) {
    fields.forEach(this::add);
  }

  public void addAll(ProgramFieldSet fields) {
    backing.putAll(fields.backing);
  }

  public boolean createAndAdd(DexProgramClass clazz, DexEncodedField definition) {
    return add(new ProgramField(clazz, definition));
  }

  public boolean contains(DexEncodedField field) {
    return backing.containsKey(field.getReference());
  }

  public boolean contains(ProgramField field) {
    return backing.containsKey(field.getReference());
  }

  public void clear() {
    backing.clear();
  }

  public boolean isEmpty() {
    return backing.isEmpty();
  }

  @Override
  public Iterator<ProgramField> iterator() {
    return backing.values().iterator();
  }

  public boolean remove(DexField field) {
    ProgramField existing = backing.remove(field);
    return existing != null;
  }

  public boolean remove(DexEncodedField field) {
    return remove(field.getReference());
  }

  public int size() {
    return backing.size();
  }

  public Stream<ProgramField> stream() {
    return backing.values().stream();
  }

  public Set<DexEncodedField> toDefinitionSet() {
    assert backing instanceof IdentityHashMap;
    Set<DexEncodedField> definitions = Sets.newIdentityHashSet();
    forEach(field -> definitions.add(field.getDefinition()));
    return definitions;
  }
}
