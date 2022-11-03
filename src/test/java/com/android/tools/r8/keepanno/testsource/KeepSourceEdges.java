// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.testsource;

import com.android.tools.r8.keepanno.ast.KeepConsequences;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMembersPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodNamePattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepTarget;
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
    // Build the class target.
    KeepQualifiedClassNamePattern name = KeepQualifiedClassNamePattern.exact(clazz.getTypeName());
    KeepItemPattern classItem = KeepItemPattern.builder().setClassPattern(name).build();
    KeepTarget classTarget = KeepTarget.builder().setItem(classItem).build();
    // Build the constructor target.
    KeepMethodPattern constructorMethod =
        KeepMethodPattern.builder().setNamePattern(KeepMethodNamePattern.exact("<init>")).build();
    KeepMembersPattern constructorMembers =
        KeepMembersPattern.builder().addMethodPattern(constructorMethod).build();
    KeepItemPattern constructorItem =
        KeepItemPattern.builder()
            .setClassPattern(name)
            .setMembersPattern(constructorMembers)
            .build();
    KeepTarget constructorTarget = KeepTarget.builder().setItem(constructorItem).build();
    // The consequet set is the class an its constructor.
    KeepConsequences consequences =
        KeepConsequences.builder().addTarget(classTarget).addTarget(constructorTarget).build();
    KeepEdge edge = KeepEdge.builder().setConsequences(consequences).build();
    return Collections.singleton(edge);
  }
}
