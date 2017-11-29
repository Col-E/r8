// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.Location;

public class ProguardRuleParserException extends Exception implements Diagnostic {

  private final String message;
  private final String snippet;
  private final Location location;

  public ProguardRuleParserException(String message, String snippet, Location location) {
    this.message = message;
    this.snippet = snippet;
    this.location = location;
  }

  public ProguardRuleParserException(String message, String snippet, Location location,
      Throwable cause) {
    this(message, snippet,location);
    initCause(cause);
  }

  @Override
  public Location getLocation() {
    return location;
  }

  @Override
  public String getDiagnosticMessage() {
    return message + " at " + snippet;
  }

  @Override
  public String getMessage() {
    return message + " at " + snippet;
  }

  public String getParseError() {
    return message;
  }

  public String getSnippet() {
    return snippet;
  }
}
