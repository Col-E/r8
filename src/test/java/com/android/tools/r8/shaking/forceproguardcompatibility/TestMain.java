// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.forceproguardcompatibility;

public class TestMain {

  public static class MentionedClass {
    public MentionedClass() {
    }
  }

  @TestAnnotation(0)
  public static class MentionedClassWithAnnotation {
    public MentionedClassWithAnnotation() {
    }
  }

  public static void main(String[] args) {
    Class<?> clazz = args.length == 8 ? MentionedClass.class : MentionedClassWithAnnotation.class;
    System.out.println(clazz.getCanonicalName());
  }
}
