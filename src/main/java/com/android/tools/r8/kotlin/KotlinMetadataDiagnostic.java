// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

public class KotlinMetadataDiagnostic implements Diagnostic {

  private final Origin origin;
  private final Position position;
  private final String message;

  public KotlinMetadataDiagnostic(Origin origin, Position position, String message) {
    this.origin = origin;
    this.position = position;
    this.message = message;
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
    return message;
  }

  static KotlinMetadataDiagnostic messageInvalidUnderlyingType(DexClass clazz, String typeAlias) {
    return new KotlinMetadataDiagnostic(
        clazz.getOrigin(),
        Position.UNKNOWN,
        "The type alias "
            + typeAlias
            + " in class "
            + clazz.type.getName()
            + " has an invalid underlying type. The type-alias is removed from the output.");
  }
}
