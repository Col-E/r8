// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexClasspathClass;
import java.util.function.Supplier;

/** Represents a collection of classpath classes. */
public class ClasspathClassCollection extends ClassMap<DexClasspathClass> {

  private ClasspathClassCollection() {
    this(null);
  }

  public ClasspathClassCollection(ClassProvider<DexClasspathClass> classProvider) {
    super(null, classProvider);
  }

  public static ClasspathClassCollection empty() {
    return new ClasspathClassCollection();
  }

  @Override
  DexClasspathClass resolveClassConflict(DexClasspathClass a, DexClasspathClass b) {
    throw new CompilationError("Classpath type already present: " + a.type.toSourceString());
  }

  @Override
  Supplier<DexClasspathClass> getTransparentSupplier(DexClasspathClass clazz) {
    return clazz;
  }

  @Override
  ClassKind<DexClasspathClass> getClassKind() {
    return ClassKind.CLASSPATH;
  }

  @Override
  public String toString() {
    return "classpath classes: " + super.toString();
  }
}
