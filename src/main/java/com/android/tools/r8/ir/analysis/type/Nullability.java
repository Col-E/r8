// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.errors.Unreachable;

/**
 * Encodes the following lattice.
 *
 * <pre>
 *          MAYBE NULL
 *          /        \
 *   DEFINITELY     DEFINITELY
 *      NULL         NOT NULL
 * </pre>
 */
public class Nullability {

  private static final Nullability DEFINITELY_NULL = new Nullability();
  private static final Nullability DEFINITELY_NOT_NULL = new Nullability();
  private static final Nullability MAYBE_NULL = new Nullability();

  private Nullability() {}

  public boolean isDefinitelyNull() {
    return this == DEFINITELY_NULL;
  }

  public boolean isDefinitelyNotNull() {
    return this == DEFINITELY_NOT_NULL;
  }

  public boolean isMaybeNull() {
    return this == MAYBE_NULL;
  }

  public Nullability join(Nullability other) {
    if (this == other) {
      return this;
    }
    return MAYBE_NULL;
  }

  public boolean lessThanOrEqual(Nullability other) {
    return join(other) == other;
  }

  public boolean isNullable() {
    return this == MAYBE_NULL || this == DEFINITELY_NULL;
  }

  public static Nullability definitelyNull() {
    return DEFINITELY_NULL;
  }

  public static Nullability definitelyNotNull() {
    return DEFINITELY_NOT_NULL;
  }

  public static Nullability maybeNull() {
    return MAYBE_NULL;
  }

  @Override
  public String toString() {
    if (this == MAYBE_NULL) {
      return "@Nullable";
    }
    if (this == DEFINITELY_NULL) {
      return "@Null";
    }
    if (this == DEFINITELY_NOT_NULL) {
      return "@NotNull";
    }
    throw new Unreachable("Unknown Nullability.");
  }
}
