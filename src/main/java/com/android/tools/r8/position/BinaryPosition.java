// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.position;

public class BinaryPosition implements Position {

  /**
   * Byte offset from start of the resource.
   */
  private final long offset;

  public BinaryPosition(long offset) {
    assert offset >= 0;
    this.offset = offset;
  }

  public long getOffset() {
    return offset;
  }

  @Override
  public String toString() {
    return "Index : " + offset;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(offset);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o != null  && o.getClass().equals(BinaryPosition.class)) {
      BinaryPosition other = (BinaryPosition) o;
      return offset == other.offset;
    }
    return false;
  }

  @Override
  public String getDescription() {
    return "Offset " + offset;
  }
}
