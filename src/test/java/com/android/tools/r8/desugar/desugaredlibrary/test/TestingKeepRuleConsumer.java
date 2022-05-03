// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.test;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase.KeepRuleConsumer;

public class TestingKeepRuleConsumer implements KeepRuleConsumer {

  StringBuilder stringBuilder = new StringBuilder();
  String result = null;

  @Override
  public void accept(String string, DiagnosticsHandler handler) {
    assert stringBuilder != null;
    assert result == null;
    stringBuilder.append(string);
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    assert stringBuilder != null;
    assert result == null;
    result = stringBuilder.toString();
    stringBuilder = null;
  }

  public String get() {
    // TODO(clement): remove that branch once StringConsumer has finished again.
    if (stringBuilder != null) {
      finished(null);
    }

    assert stringBuilder == null;
    assert result != null;
    return result;
  }
}
