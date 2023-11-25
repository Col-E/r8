// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

@KeepForApi
public class AndroidApiUnknownReferenceDiagnostic extends AndroidApiDiagnostic {

  private final DexReference reference;

  AndroidApiUnknownReferenceDiagnostic(DexReference reference) {
    this.reference = reference;
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
    return reference.toSourceString() + " cannot be found in the api database.";
  }
}
