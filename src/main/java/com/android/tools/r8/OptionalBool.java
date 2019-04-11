// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

/** Three point boolean lattice. */
public abstract class OptionalBool {

  private static final OptionalBool TRUE =
      new OptionalBool() {
        @Override
        public boolean isTrue() {
          return true;
        }

        @Override
        public String toString() {
          return "true";
        }
      };

  private static final OptionalBool FALSE =
      new OptionalBool() {
        @Override
        public boolean isFalse() {
          return true;
        }

        @Override
        public String toString() {
          return "false";
        }
      };

  private static final OptionalBool UNKNOWN =
      new OptionalBool() {
        @Override
        public boolean isUnknown() {
          return true;
        }

        @Override
        public String toString() {
          return "unknown";
        }
      };

  public static OptionalBool of(boolean bool) {
    return bool ? TRUE : FALSE;
  }

  public static OptionalBool unknown() {
    return UNKNOWN;
  }

  private OptionalBool() {}

  public boolean isTrue() {
    return false;
  }

  public boolean isFalse() {
    return false;
  }

  public boolean isUnknown() {
    return false;
  }

  public boolean isPossiblyTrue() {
    return !isFalse();
  }

  public boolean isPossiblyFalse() {
    return !isTrue();
  }

  public boolean getBooleanValue() {
    if (isUnknown()) {
      throw new IllegalStateException("Attempt to convert unknown value to a boolean");
    }
    return isTrue();
  }
}
