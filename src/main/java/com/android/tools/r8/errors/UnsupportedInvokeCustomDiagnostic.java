// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import static com.android.tools.r8.utils.InternalOptions.invokeCustomApiLevel;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

@KeepForApi
public class UnsupportedInvokeCustomDiagnostic extends UnsupportedFeatureDiagnostic {

  // API: MUST NOT CHANGE!
  private static final String DESCRIPTOR = "invoke-custom";

  public UnsupportedInvokeCustomDiagnostic(Origin origin, Position position) {
    super(DESCRIPTOR, invokeCustomApiLevel(), origin, position);
  }

  @Override
  public String getDiagnosticMessage() {
    return UnsupportedFeatureDiagnostic.makeMessage(invokeCustomApiLevel(), "Invoke-customs", null);
  }
}
