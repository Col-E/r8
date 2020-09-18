// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ProgramPackage implements Iterable<DexProgramClass> {

  private final String packageDescriptor;
  private final Set<DexProgramClass> classes;

  public ProgramPackage(String packageDescriptor) {
    this(packageDescriptor, Sets::newIdentityHashSet);
  }

  protected ProgramPackage(
      String packageDescriptor, Supplier<Set<DexProgramClass>> backingFactory) {
    this.packageDescriptor = packageDescriptor;
    this.classes = backingFactory.get();
  }

  public boolean add(DexProgramClass clazz) {
    assert clazz.getType().getPackageDescriptor().equals(packageDescriptor);
    return classes.add(clazz);
  }

  public boolean contains(DexProgramClass clazz) {
    return classes.contains(clazz);
  }

  public String getLastPackageName() {
    int index = packageDescriptor.lastIndexOf('/');
    if (index >= 0) {
      return packageDescriptor.substring(index + 1);
    }
    return packageDescriptor;
  }

  public String getPackageDescriptor() {
    return packageDescriptor;
  }

  public void forEachClass(Consumer<DexProgramClass> consumer) {
    forEach(consumer);
  }

  public void forEachField(Consumer<ProgramField> consumer) {
    forEach(clazz -> clazz.forEachProgramField(consumer));
  }

  public void forEachMethod(Consumer<ProgramMethod> consumer) {
    forEach(clazz -> clazz.forEachProgramMethod(consumer));
  }

  public Set<DexProgramClass> classesInPackage() {
    return ImmutableSet.copyOf(classes);
  }

  @Override
  public Iterator<DexProgramClass> iterator() {
    return classes.iterator();
  }
}
