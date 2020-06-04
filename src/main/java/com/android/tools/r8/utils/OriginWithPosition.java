// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import java.util.Objects;

public class OriginWithPosition {
  private final Origin origin;
  private final Position position;

  public OriginWithPosition(Origin origin, Position position) {
    this.origin = origin;
    this.position = position;
  }

  public Origin getOrigin() {
    return origin;
  }

  public Position getPosition() {
    return position;
  }

  @Override
  public int hashCode() {
    return origin.hashCode() * 13 + position.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof OriginWithPosition) {
      return Objects.equals(((OriginWithPosition) other).origin, origin)
          && Objects.equals(((OriginWithPosition) other).position, position);
    }
    return false;
  }
}
