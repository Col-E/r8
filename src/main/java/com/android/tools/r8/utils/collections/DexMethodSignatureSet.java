// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

public class DexMethodSignatureSet implements Iterable<DexMethodSignature> {

  private final Set<DexMethodSignature> backing;

  private DexMethodSignatureSet(Set<DexMethodSignature> backing) {
    this.backing = backing;
  }

  public static DexMethodSignatureSet create() {
    return new DexMethodSignatureSet(new HashSet<>());
  }

  public static DexMethodSignatureSet create(DexMethodSignatureSet collection) {
    return new DexMethodSignatureSet(new HashSet<>(collection.backing));
  }

  public static DexMethodSignatureSet createLinked() {
    return new DexMethodSignatureSet(new LinkedHashSet<>());
  }

  public boolean add(DexMethodSignature signature) {
    return backing.add(signature);
  }

  public boolean add(DexMethod method) {
    return add(method.getSignature());
  }

  public boolean add(DexEncodedMethod method) {
    return add(method.getReference());
  }

  public boolean add(DexClassAndMethod method) {
    return add(method.getReference());
  }

  public void addAll(Iterable<DexMethodSignature> signatures) {
    signatures.forEach(this::add);
  }

  public void addAllMethods(Iterable<DexEncodedMethod> methods) {
    methods.forEach(this::add);
  }

  public void addAll(DexMethodSignatureSet signatures) {
    addAll(signatures.backing);
  }

  public <T> void addAll(Iterable<T> elements, Function<T, Iterable<DexMethodSignature>> fn) {
    for (T element : elements) {
      addAll(fn.apply(element));
    }
  }

  public boolean contains(DexMethodSignature signature) {
    return backing.contains(signature);
  }

  @Override
  public Iterator<DexMethodSignature> iterator() {
    return backing.iterator();
  }

  public boolean remove(DexMethodSignature signature) {
    return backing.remove(signature);
  }

  public boolean remove(DexEncodedMethod method) {
    return remove(method.getSignature());
  }

  public void removeAll(Iterable<DexMethodSignature> signatures) {
    signatures.forEach(this::remove);
  }

  public void removeAllMethods(Iterable<DexEncodedMethod> methods) {
    methods.forEach(this::remove);
  }
}
