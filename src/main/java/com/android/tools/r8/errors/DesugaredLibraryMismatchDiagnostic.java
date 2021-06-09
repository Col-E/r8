// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import java.util.Set;
import java.util.stream.Collectors;

public class DesugaredLibraryMismatchDiagnostic implements Diagnostic {

  private final Set<String> desugaredLibraryIdentifiers;
  private final Set<Marker> markers;

  public DesugaredLibraryMismatchDiagnostic(
      Set<String> desugaredLibraryIdentifiers, Set<Marker> markers) {
    this.desugaredLibraryIdentifiers = desugaredLibraryIdentifiers;
    this.markers = markers;
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
    return "The compilation is merging inputs with different desugared library desugaring "
        + desugaredLibraryIdentifiers
        + ", which may lead to unexpected runtime errors."
        + "The markers are "
        + markers.stream().map(Marker::toString).collect(Collectors.joining(", "));
  }
}
