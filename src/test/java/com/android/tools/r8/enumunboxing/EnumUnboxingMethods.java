// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

// This class implements support methods for enum unboxing. The enum unboxing optimization may
// rewrite any call to an enum method into one of the following methods.
// The methods Enum#name, Enum#toString and MyEnum#valueOf cannot be implemented here since they
// are different on each Enum implementation.
public class EnumUnboxingMethods {

  // An enum is unboxed to ordinal + 1.
  // For example, enum E {A,B}, is unboxed to null -> 0, A -> 1, B-> 2.
  // Computing the ordinal of an unboxed enum throws a null pointer exception on 0,
  // else answers the value - 1.
  public static int ordinal(int unboxedEnum) {
    if (unboxedEnum == 0) {
      throw new NullPointerException();
    }
    return unboxedEnum - 1;
  }

  // The values methods normally reads the $VALUES field, then clones and returns it.
  // In our case we just create a new instance each time.
  // Note: This can replace a MyEnum#values() call, but not a MyEnum#$VALUES static get.
  // numEnums is the number of elements in the enum.
  public static int[] values(int numEnums) {
    int[] ints = new int[numEnums];
    for (int i = 0; i < ints.length; i++) {
      ints[i] = i + 1;
    }
    return ints;
  }

  // We assume the enum could be unboxed only if both parameters were proven to be of the same
  // enum types, so we do not check they belong to the same Enum type.
  // We need the 0 checks for null pointer exception.
  public static int compareTo(int unboxedEnum1, int unboxedEnum2) {
    if (unboxedEnum1 == 0 || unboxedEnum2 == 0) {
      throw new NullPointerException();
    }
    // Formula: unboxedEnum1 - 1 - (unboxedEnum2 - 1), simplified as follow:
    return unboxedEnum1 - unboxedEnum2;
  }

  // Equals on Enum is implemented using directly ==. The invoke raises a NPE if the
  // receiver is null, but returns false if the parameter is null.
  public static boolean equals(int unboxedEnum1, int unboxedEnum2) {
    if (unboxedEnum1 == 0) {
      throw new NullPointerException();
    }
    return unboxedEnum1 == unboxedEnum2;
  }

  // Objects#equals is similar to equals without the NPE for null entries.
  // Objects.equals(null,null) answers true.
  public static boolean objectEquals(int unboxedEnum1, int unboxedEnum2) {
    return unboxedEnum1 == unboxedEnum2;
  }

  // Methods zeroCheck and zeroCheckMessage are used to replace null checks on unboxed enums.
  public static void zeroCheck(int unboxedEnum) {
    if (unboxedEnum == 0) {
      throw new NullPointerException();
    }
  }

  public static void zeroCheckMessage(int unboxedEnum, String message) {
    if (unboxedEnum == 0) {
      throw new NullPointerException(message);
    }
  }
}
