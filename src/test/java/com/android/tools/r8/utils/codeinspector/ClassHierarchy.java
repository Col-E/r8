// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexClass;
import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Given a code inspector, builds a mapping from application types to their direct super types.
public class ClassHierarchy {

  private final Map<FoundClassSubject, Set<FoundClassSubject>> directSubtypes = new HashMap<>();
  private final Set<FoundClassSubject> roots = new HashSet<>();

  private ClassHierarchy() {}

  public static ClassHierarchy build(CodeInspector inspector) {
    ClassHierarchy result = new ClassHierarchy();
    for (FoundClassSubject classSubject : inspector.allClasses()) {
      DexClass clazz = classSubject.getDexClass();

      ClassSubject superClassSubject = inspector.clazz(clazz.superType.toSourceString());
      if (superClassSubject.isPresent()) {
        result
            .directSubtypes
            .computeIfAbsent(superClassSubject.asFoundClassSubject(), key -> new HashSet<>())
            .add(classSubject);
      } else {
        result.roots.add(classSubject);
      }
    }
    return result;
  }

  public Set<FoundClassSubject> getDirectSubtypes(FoundClassSubject clazz) {
    return directSubtypes.getOrDefault(clazz, ImmutableSet.of());
  }

  public Set<FoundClassSubject> getRoots() {
    return roots;
  }
}
