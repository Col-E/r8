// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.enums;

import com.android.tools.r8.AssumeMayHaveSideEffects;
import com.android.tools.r8.ForceInline;
import com.android.tools.r8.NeverInline;
import java.util.concurrent.TimeUnit;

class Names {
  enum Number {
    ONE, TWO;

    public static final Direction DOWN = Direction.DOWN;
    public static final Number DEFAULT = TWO;
  }

  enum Direction {
    UP, DOWN
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  private static String simple() {
    return Number.TWO.name();
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  private static String local() {
    Number two = Number.TWO;
    return two.name();
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  private static String multipleUsages() {
    Number two = Number.TWO;
    return two.ordinal() + two.name();
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  private static String inlined() {
    return inlined2(Number.TWO);
  }

  @ForceInline
  private static String inlined2(Number number) {
    return number.name();
  }

  @NeverInline
  private static String libraryType() {
    return TimeUnit.SECONDS.name();
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  private static String differentTypeStaticField() {
    return Number.DOWN.name();
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  private static String nonValueStaticField() {
    return Number.DEFAULT.name();
  }

  @NeverInline
  private static String phi(boolean value) {
    Number number = Number.ONE;
    if (value) {
      number = Number.TWO;
    }
    return number.name();
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  private static String nonStaticGet() {
    return new Names().two.name();
  }

  private final Number two = Number.TWO;

  public static void main(String[] args) {
    System.out.println(simple());
    System.out.println(local());
    System.out.println(multipleUsages());
    System.out.println(inlined());
    System.out.println(libraryType());
    System.out.println(differentTypeStaticField());
    System.out.println(nonValueStaticField());
    System.out.println(phi(true));
    System.out.println(nonStaticGet());
  }
}
