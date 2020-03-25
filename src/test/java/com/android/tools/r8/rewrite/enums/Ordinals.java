// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.enums;

import com.android.tools.r8.AssumeMayHaveSideEffects;
import com.android.tools.r8.ForceInline;
import com.android.tools.r8.NeverInline;
import java.util.concurrent.TimeUnit;

class Ordinals {
  enum Number {
    ONE, TWO;

    public static final Direction DOWN = Direction.DOWN;
    public static final Number DEFAULT = TWO;
  }

  enum Direction {
    UP, DOWN
  }

  @NeverInline
  private static long simple() {
    return Number.TWO.ordinal();
  }

  @NeverInline
  private static long local() {
    Number two = Number.TWO;
    return two.ordinal();
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  private static String multipleUsages() {
    Number two = Number.TWO;
    return two.name() + two.ordinal();
  }

  @NeverInline
  private static long inlined() {
    return inlined2(Number.TWO);
  }
  @ForceInline
  private static long inlined2(Number number) {
    return number.ordinal();
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  private static int inSwitch() {
    // Unlike normal invocations of Enum.ordinal(), a switch on enum will emit an invoke-virtual
    // where the receiver is the enum subtype and not java.lang.Enum. This test ensures that case
    // is always handled. But by virtue of replacing the ordinal() call with a constant, the switch
    // completely disappears. We synthesize a corresponding constant value in a return for
    // testing that the ordinal() call was correctly replaced and the switch was removed.
    switch (Number.TWO) {
      case ONE: return 10;
      case TWO: return 11;
      default: throw new AssertionError();
    }
  }

  @NeverInline
  private static long libraryType() {
    return TimeUnit.SECONDS.ordinal();
  }

  @NeverInline
  private static long differentTypeStaticField() {
    return Number.DOWN.ordinal();
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  private static long nonValueStaticField() {
    return Number.DEFAULT.ordinal();
  }

  @NeverInline
  private static long phi(boolean value) {
    Number number = Number.ONE;
    if (value) {
      number = Number.TWO;
    }
    return number.ordinal();
  }

  @NeverInline
  private static long nonStaticGet() {
    return new Ordinals().two.ordinal();
  }
  private final Number two = Number.TWO;

  public static void main(String[] args) {
    System.out.println(simple());
    System.out.println(local());
    System.out.println(multipleUsages());
    System.out.println(inlined());
    System.out.println(inSwitch());
    System.out.println(libraryType());
    System.out.println(differentTypeStaticField());
    System.out.println(nonValueStaticField());
    System.out.println(phi(true));
    System.out.println(nonStaticGet());
  }
}
