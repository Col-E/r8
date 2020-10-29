// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.Keep;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.tracereferences.Tracer.TracedClassImpl;
import com.android.tools.r8.tracereferences.Tracer.TracedFieldImpl;
import com.android.tools.r8.tracereferences.Tracer.TracedMethodImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Keep
public class MissingDefinitionsDiagnostic implements Diagnostic {

  private final Set<TracedClassImpl> missingClasses;
  private final Set<TracedFieldImpl> missingFields;
  private final Set<TracedMethodImpl> missingMethods;

  MissingDefinitionsDiagnostic(
      Set<TracedClassImpl> missingClasses,
      Set<TracedFieldImpl> missingFields,
      Set<TracedMethodImpl> missingMethods) {
    this.missingClasses = missingClasses;
    this.missingFields = missingFields;
    this.missingMethods = missingMethods;
  }

  @Override
  public Origin getOrigin() {
    return Origin.unknown();
  }

  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    StringBuilder builder = new StringBuilder("Tracereferences found ");
    List<String> components = new ArrayList<>();
    if (missingClasses.size() > 0) {
      components.add("" + missingClasses.size() + " classes");
    }
    if (missingFields.size() > 0) {
      components.add("" + missingClasses.size() + " fields");
    }
    if (missingMethods.size() > 0) {
      components.add("" + missingClasses.size() + " methods");
    }
    assert components.size() > 0;
    for (int i = 0; i < components.size(); i++) {
      if (i != 0) {
        builder.append(i < components.size() - 1 ? ", " : " and ");
      }
      builder.append(components.get(i));
    }
    builder.append(" without definition");
    builder.append(System.lineSeparator());
    builder.append(System.lineSeparator());
    builder.append("Classes without definition:");
    missingClasses.forEach(
        clazz ->
            builder
                .append("  ")
                .append(clazz.getReference().toString())
                .append(System.lineSeparator()));
    builder.append("Fields without definition");
    missingFields.forEach(
        field ->
            builder
                .append("  ")
                .append(field.getReference().toString())
                .append(System.lineSeparator()));
    builder.append("Methods without definition");
    missingMethods.forEach(
        method ->
            builder
                .append("  ")
                .append(method.getReference().toString())
                .append(System.lineSeparator()));
    return builder.toString();
  }
}
