// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.rules.TemporaryFolder;

public class TestState {

  private final TemporaryFolder temp;

  public TestState(TemporaryFolder temp) {
    this.temp = temp;
  }

  public Path getNewTempFolder() throws IOException {
    return temp.newFolder().toPath();
  }
}
