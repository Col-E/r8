// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

/** Three-point valued matcher on an access modifier. */
public class ModifierPattern {

  private static final ModifierPattern ANY =
      new ModifierPattern() {
        @Override
        public boolean isAny() {
          return true;
        }
      };

  private static final ModifierPattern POSITIVE =
      new ModifierPattern() {
        @Override
        public boolean isOnlyPositive() {
          return true;
        }
      };

  private static final ModifierPattern NEGATIVE =
      new ModifierPattern() {
        @Override
        public boolean isOnlyNegative() {
          return true;
        }
      };

  public static ModifierPattern fromAllowValue(boolean allow) {
    return allow ? onlyPositive() : onlyNegative();
  }

  public static ModifierPattern any() {
    return ANY;
  }

  public static ModifierPattern onlyPositive() {
    return POSITIVE;
  }

  public static ModifierPattern onlyNegative() {
    return NEGATIVE;
  }

  private ModifierPattern() {}

  public boolean isAny() {
    return false;
  }

  public boolean isOnlyPositive() {
    return false;
  }

  public boolean isOnlyNegative() {
    return false;
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }
}
