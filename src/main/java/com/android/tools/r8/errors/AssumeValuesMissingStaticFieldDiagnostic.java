// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

@KeepForApi
public class AssumeValuesMissingStaticFieldDiagnostic implements Diagnostic {

  private final DexType fieldHolder;
  private final DexString fieldName;
  private final Origin origin;
  private final Position position;

  private AssumeValuesMissingStaticFieldDiagnostic(
      DexType fieldHolder, DexString fieldName, Origin origin, Position position) {
    this.fieldHolder = fieldHolder;
    this.fieldName = fieldName;
    this.origin = origin;
    this.position = position;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return position;
  }

  @Override
  public String getDiagnosticMessage() {
    return "The field "
        + fieldHolder.getTypeName()
        + "."
        + fieldName
        + " is used as the return value in an -assumenosideeffects or -assumevalues rule"
        + ", but no such static field exists.";
  }

  public static class Builder {

    private DexType fieldHolder;
    private DexString fieldName;
    private Origin origin;
    private Position position;

    public Builder() {}

    public Builder setField(DexType fieldHolder, DexString fieldName) {
      this.fieldHolder = fieldHolder;
      this.fieldName = fieldName;
      return this;
    }

    public Builder setOrigin(Origin origin) {
      this.origin = origin;
      return this;
    }

    public Builder setPosition(Position position) {
      this.position = position;
      return this;
    }

    public AssumeValuesMissingStaticFieldDiagnostic build() {
      return new AssumeValuesMissingStaticFieldDiagnostic(fieldHolder, fieldName, origin, position);
    }
  }
}
