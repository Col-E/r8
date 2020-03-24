// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
//  for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.errors;

import com.android.tools.r8.Keep;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;

/**
 * Diagnostic for super types of library classes which are not library classes but required for
 * desugaring.
 */
@Keep
public class InvalidLibrarySuperclassDiagnostic implements DesugarDiagnostic {

  private final Origin origin;
  private final List<MethodReference> methods;
  private final ClassReference libraryType;
  private final ClassReference invalidSuperType;
  private final String message;

  public InvalidLibrarySuperclassDiagnostic(
      Origin origin,
      ClassReference libraryType,
      ClassReference invalidSuperType,
      String message,
      List<MethodReference> methods) {
    assert origin != null;
    assert libraryType != null;
    assert invalidSuperType != null;
    assert message != null;
    this.origin = origin;
    this.libraryType = libraryType;
    this.invalidSuperType = invalidSuperType;
    this.message = message;
    this.methods = methods;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    StringBuilder builder =
        new StringBuilder()
            .append("Superclass `")
            .append(invalidSuperType.getTypeName())
            .append("` of library class `")
            .append(libraryType.getTypeName())
            .append("` is ")
            .append(message)
            .append(
                ". A superclass of a library class should be a library class. This is required for"
                    + " the desugaring of ");
    StringUtils.append(builder, methods, ", ", StringUtils.BraceType.NONE);
    return builder.toString();
  }
}
