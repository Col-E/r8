// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.util.HashSet;
import java.util.Set;

public enum AccessVisibility {
  PUBLIC,
  PROTECTED,
  PACKAGE_PRIVATE,
  PRIVATE;

  private static final ImmutableSet<AccessVisibility> ALL = ImmutableSortedSet.copyOf(values());

  public String toSourceSyntax() {
    switch (this) {
      case PUBLIC:
        return "public";
      case PROTECTED:
        return "protected";
      case PACKAGE_PRIVATE:
        throw new KeepEdgeException("No source syntax for package-private visibility.");
      case PRIVATE:
        return "private";
      default:
        throw new KeepEdgeException("Unexpected access visibility: " + this);
    }
  }

  public static boolean containsAll(Set<AccessVisibility> visibilities) {
    return visibilities.size() == AccessVisibility.values().length;
  }

  public static Set<AccessVisibility> createSet() {
    return new HashSet<>();
  }

  public static Set<AccessVisibility> all() {
    return ALL;
  }
}
