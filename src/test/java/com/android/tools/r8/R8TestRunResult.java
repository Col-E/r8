// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class R8TestRunResult extends TestRunResult<R8TestRunResult> {

  public interface GraphInspectorSupplier {
    GraphInspector get() throws IOException, ExecutionException;
  }

  private final String proguardMap;
  private final GraphInspectorSupplier graphInspector;

  public R8TestRunResult(
      AndroidApp app,
      ProcessResult result,
      String proguardMap,
      GraphInspectorSupplier graphInspector) {
    super(app, result);
    this.proguardMap = proguardMap;
    this.graphInspector = graphInspector;
  }

  @Override
  protected R8TestRunResult self() {
    return this;
  }

  @Override
  public CodeInspector inspector() throws IOException, ExecutionException {
    // See comment in base class.
    assertSuccess();
    assertNotNull(app);
    return new CodeInspector(app, proguardMap);
  }

  public GraphInspector graphInspector() throws IOException, ExecutionException {
    assertSuccess();
    return graphInspector.get();
  }

  public String proguardMap() {
    return proguardMap;
  }
}
