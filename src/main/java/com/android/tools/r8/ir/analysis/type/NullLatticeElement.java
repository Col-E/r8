// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

/**
 * Encodes the following lattice.
 *
 * <pre>
 *          MAYBE NULL
 *          /        \
 *   DEFINITELY     DEFINITELY
 *      NULL         NOT NULL
 *          \        /
 *            BOTTOM
 * </pre>
 */
public class NullLatticeElement {

  private static final NullLatticeElement BOTTOM = new NullLatticeElement();
  private static final NullLatticeElement DEFINITELY_NULL = new NullLatticeElement();
  private static final NullLatticeElement DEFINITELY_NOT_NULL = new NullLatticeElement();
  private static final NullLatticeElement MAYBE_NULL = new NullLatticeElement();

  private NullLatticeElement() {}

  public boolean isDefinitelyNull() {
    return this == DEFINITELY_NULL;
  }

  public boolean isDefinitelyNotNull() {
    return this == DEFINITELY_NOT_NULL;
  }

  public NullLatticeElement leastUpperBound(NullLatticeElement other) {
    if (this == BOTTOM) {
      return other;
    }
    if (this == other || other == BOTTOM) {
      return this;
    }
    return MAYBE_NULL;
  }

  public boolean lessThanOrEqual(NullLatticeElement other) {
    return leastUpperBound(other) == other;
  }

  static NullLatticeElement bottom() {
    return BOTTOM;
  }

  static NullLatticeElement definitelyNull() {
    return DEFINITELY_NULL;
  }

  static NullLatticeElement definitelyNotNull() {
    return DEFINITELY_NOT_NULL;
  }

  static NullLatticeElement maybeNull() {
    return MAYBE_NULL;
  }
}
