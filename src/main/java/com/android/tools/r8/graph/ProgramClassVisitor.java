// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.google.common.collect.Sets;
import java.util.Set;

/**
 * Implements traversal of the class hierarchy in topological order. A class is visited after its
 * super class and its interfaces are visited. Only visits program classes and does NOT visit
 * classpath, nor library classes.
 *
 * <p>NOTE: The visiting is processed by traversing program classes only, which means that in
 * presence of classpath it is NOT guaranteed that class C is visited before class D if there exists
 * a classpath class X in class hierarchy between C and D, like:
 *
 * <pre>
 *   class ProgramClassS {}
 *   class ClasspathClassX extends ProgramClassS {}
 *   class ProgramClassD extends ClasspathClassX {}
 * </pre>
 *
 * The above consideration does not apply to library classes, since we assume library classes never
 * extend or implement program/classpath class.
 */
public abstract class ProgramClassVisitor {

  final AppView<?> appView;

  private final Set<DexProgramClass> visited = Sets.newIdentityHashSet();

  protected ProgramClassVisitor(AppView<?> appView) {
    this.appView = appView;
  }

  private void accept(DexType type) {
    DexProgramClass clazz = appView.app().programDefinitionFor(type);
    if (clazz != null) {
      accept(clazz);
    }
  }

  private void accept(DexTypeList types) {
    for (DexType type : types.values) {
      accept(type);
    }
  }

  private void accept(DexProgramClass clazz) {
    if (visited.add(clazz)) {
      if (clazz.hasSuperType()) {
        accept(clazz.getSuperType());
      }
      accept(clazz.getInterfaces());
      visit(clazz);
    }
  }

  public void run(DexProgramClass[] classes) {
    for (DexProgramClass clazz : classes) {
      accept(clazz);
    }
  }

  /** Called for each class defined in the application. */
  public abstract void visit(DexProgramClass clazz);
}
