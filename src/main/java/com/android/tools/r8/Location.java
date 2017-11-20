// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;

/**
 * Describe the location of an event.
 */
public class Location {

  /**
   * Location is unknown or event is not location related.
   */
  public static final Location UNKNOWN = new Location(Origin.unknown());

  private final Origin origin;

  public Location(Origin origin) {
    assert origin != null;
    this.origin = origin;
  }

  /**
   * Returns the {@link Origin} of the resource concerned by this {@link Location}
   */
  public Origin getOrigin() {
    return origin;
  }

  /**
   * Returns a basic textual description of this {@link Location}.
   */
  public String getDescription() {
    if (origin == Origin.unknown()) {
      return "<Unknown>";
    }
    return origin.toString();
  }
}
