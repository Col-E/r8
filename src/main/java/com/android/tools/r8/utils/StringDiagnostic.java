// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.Location;
import com.android.tools.r8.origin.Origin;

public class StringDiagnostic implements Diagnostic {

  private final Location location;
  private final String message;

  public StringDiagnostic(String message) {
    this(message, Location.UNKNOWN);
  }

  public StringDiagnostic(String message, Origin origin) {
    this(message, new Location(origin));
  }

  public StringDiagnostic(String message, Location location) {
    this.location = location;
    this.message = message;
  }

  @Override
  public Location getLocation() {
    return location;
  }

  @Override
  public String getDiagnosticMessage() {
    return message;
  }
}
