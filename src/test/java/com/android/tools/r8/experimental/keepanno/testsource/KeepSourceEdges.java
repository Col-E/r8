// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.testsource;

import com.android.tools.r8.experimental.keepanno.ast.KeepConsequences;
import com.android.tools.r8.experimental.keepanno.ast.KeepEdge;
import com.android.tools.r8.experimental.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.experimental.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.experimental.keepanno.ast.KeepTarget;
import java.util.Collections;
import java.util.Set;

/**
 * Utility to get the AST edges for the various annotation test sources.
 *
 * <p>This makes it easier to share the test sources among tests (e.g., processor and asm tests).
 */
public class KeepSourceEdges {

  public static Set<KeepEdge> getExpectedEdges(Class<?> clazz) {
    if (clazz.equals(KeepClassAndDefaultConstructorSource.class)) {
      return getKeepClassAndDefaultConstructorSourceEdges();
    }
    throw new RuntimeException();
  }

  public static Set<KeepEdge> getKeepClassAndDefaultConstructorSourceEdges() {
    Class<?> clazz = KeepClassAndDefaultConstructorSource.class;
    KeepQualifiedClassNamePattern name = KeepQualifiedClassNamePattern.exact(clazz.getTypeName());
    KeepItemPattern item = KeepItemPattern.builder().setClassPattern(name).build();
    KeepTarget target = KeepTarget.builder().setItem(item).build();
    KeepConsequences consequences = KeepConsequences.builder().addTarget(target).build();
    KeepEdge edge = KeepEdge.builder().setConsequences(consequences).build();
    return Collections.singleton(edge);
  }
}
