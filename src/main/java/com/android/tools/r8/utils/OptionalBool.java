// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

public abstract class OptionalBool extends BooleanLatticeElement {

  public static final OptionalBool TRUE =
      new OptionalBool() {

        @Override
        public boolean isTrue() {
          return true;
        }

        @Override
        public boolean toBoolean() {
          return true;
        }

        @Override
        public int ordinal() {
          return 1;
        }

        @Override
        public String toString() {
          return "true";
        }
      };

  public static final OptionalBool FALSE =
      new OptionalBool() {

        @Override
        public boolean isFalse() {
          return true;
        }

        @Override
        public boolean toBoolean() {
          return false;
        }

        @Override
        public int ordinal() {
          return 0;
        }

        @Override
        public String toString() {
          return "false";
        }
      };

  public static final OptionalBool UNKNOWN =
      new OptionalBool() {

        @Override
        public boolean isUnknown() {
          return true;
        }

        @Override
        public boolean toBoolean() {
          throw new IllegalStateException("Cannot convert UNKNOWN to boolean");
        }

        @Override
        public int ordinal() {
          return 2;
        }

        @Override
        public String toString() {
          return "unknown";
        }
      };

  OptionalBool() {}

  public static OptionalBool of(boolean bool) {
    return bool ? TRUE : FALSE;
  }

  public static OptionalBool unknown() {
    return UNKNOWN;
  }

  @Override
  public OptionalBool asOptionalBool() {
    return this;
  }

  public abstract boolean toBoolean();

  @Override
  public boolean equals(Object other) {
    return this == other;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  public abstract int ordinal();

  // Force all subtypes to implement toString().
  @Override
  public abstract String toString();
}
