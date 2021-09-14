// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.enums;

import com.android.tools.r8.AssumeMayHaveSideEffects;
import com.android.tools.r8.ForceInline;
import com.android.tools.r8.NeverInline;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

class ToStrings {
  enum TypeToString {
    ONE, TWO;

    @Override public String toString() {
      return name().toLowerCase(Locale.US);
    }
  }

  enum ValueToString {
    ONE {

      @Override
      public String toString() {
        return "one";
      }
    },
    TWO
  }

  enum NoToString {
    ONE, TWO;

    public static final NoToString DEFAULT = TWO;
    public static final Direction DOWN = Direction.DOWN;
  }

  enum Direction {
    UP, DOWN
  }

  @NeverInline
  private static String typeToString() {
    return TypeToString.ONE.toString();
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  private static String valueWithToString() {
    return ValueToString.ONE.toString();
  }

  @NeverInline
  private static String valueWithoutToString() {
    return ValueToString.TWO.toString();
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  private static String noToString() {
    return NoToString.TWO.toString();
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  private static String local() {
    NoToString two = NoToString.TWO;
    return two.toString();
  }

  @NeverInline
  private static String multipleUsages() {
    NoToString two = NoToString.TWO;
    // Side-effect instead of concatenation avoids two toString calls.
    System.out.print(two.ordinal());
    return two.toString();
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  private static String inlined() {
    return inlined2(NoToString.TWO);
  }

  @ForceInline
  private static String inlined2(NoToString number) {
    return number.toString();
  }

  @NeverInline
  private static String libraryType() {
    return TimeUnit.SECONDS.toString();
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  private static String differentTypeStaticField() {
    return NoToString.DOWN.toString();
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  private static String nonValueStaticField() {
    return NoToString.DEFAULT.toString();
  }

  @NeverInline
  private static String phi(boolean value) {
    NoToString number = NoToString.ONE;
    if (value) {
      number = NoToString.TWO;
    }
    return number.toString();
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  private static String nonStaticGet() {
    return new ToStrings().two.toString();
  }

  private final NoToString two = NoToString.TWO;

  public static void main(String[] args) {
    System.out.println(typeToString());
    System.out.println(valueWithToString());
    System.out.println(valueWithoutToString());
    System.out.println(noToString());
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
