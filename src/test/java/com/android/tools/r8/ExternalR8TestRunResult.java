// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

public class ExternalR8TestRunResult extends TestRunResult<ExternalR8TestRunResult> {

  private final Path outputJar;
  private final String proguardMap;

  public ExternalR8TestRunResult(
      AndroidApp app, Path outputJar, String proguardMap, ProcessResult result) {
    super(app, result);
    this.outputJar = outputJar;
    this.proguardMap = proguardMap;
  }

  public Path outputJar() {
    return outputJar;
  }

  @Override
  protected ExternalR8TestRunResult self() {
    return this;
  }

  @Override
  public CodeInspector inspector() throws IOException, ExecutionException {
    // See comment in base class.
    assertSuccess();
    assertNotNull(app);
    return new CodeInspector(app, proguardMap);
  }
}
