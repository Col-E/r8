// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

// TODO(b/154778581): Flesh out this class/interface and keep it.
public abstract class ApiLevelDiagnostic implements Diagnostic {

  @Override
  public Origin getOrigin() {
    return Origin.unknown();
  }

  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }
}
