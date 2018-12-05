// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import java.util.List;

public interface TestDiagnosticMessages {

  public List<Diagnostic> getInfos();

  public List<Diagnostic> getWarnings();

  public List<Diagnostic> getErrors();
}
