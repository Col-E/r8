// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

@interface UsedAnnotation {
  // Intentionally left empty.
}

@interface UnusedAnnotation {
  // Intentionally left empty.
}

class UsedAnnotationDependent {
  // Intentionally left empty.
}

class UnusedAnnotationDependent {
  // Intentionally left empty.
}

class AnnotationUser {
  private int intField;

  @UsedAnnotation void live() {
    System.out.println("live(" + intField++ + ")");
  }

  @UnusedAnnotation void dead() {
    throw new AssertionError("Should be removed.");
  }
}

class MainUsesAnnotationUser {
  public static void main(String[] args) {
    AnnotationUser user = new AnnotationUser();
    user.live();
  }
}
