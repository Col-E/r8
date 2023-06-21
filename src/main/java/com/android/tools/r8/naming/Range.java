// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.positions.MappedPositionToClassNameMapperBuilder.getMaxLineNumber;

import java.util.Objects;

/** Represents a line number range. */
public class Range {

  public final int from;
  public final int to;
  public final boolean isCardinal;

  public Range(int line) {
    this(line, line, true);
  }

  public Range(int from, int to) {
    this(from, to, false);
  }

  private Range(int from, int to, boolean isCardinal) {
    this.from = from;
    this.to = to;
    this.isCardinal = isCardinal;
    assert from <= to;
  }

  public boolean contains(int value) {
    return from <= value && value <= to;
  }

  @Override
  public String toString() {
    return isCardinal ? (from + "") : (from + ":" + to);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Range)) {
      return false;
    }

    Range range = (Range) o;
    return from == range.from && to == range.to && isCardinal == range.isCardinal;
  }

  public int span() {
    if (isCardinal) {
      return 1;
    }
    return (to - from) + 1;
  }

  public boolean isSingleLine() {
    return isCardinal || to == from;
  }

  @Override
  public int hashCode() {
    return Objects.hash(from, to, isCardinal);
  }

  public boolean isCatchAll() {
    return from == 0 && to == getMaxLineNumber();
  }

  public boolean isPreamble() {
    return from == 0 && to == 0;
  }
}
