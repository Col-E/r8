// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b147865212;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexDebugEvent.StartLocal;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress147865212 extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;

  public Regress147865212(TestParameters parameters) {
    this.parameters = parameters;
  }

  private boolean hasExceptionLocal(MethodSubject method) {
    return Arrays.stream(method.getMethod().getCode().asDexCode().getDebugInfo().events)
        .anyMatch(event -> event instanceof StartLocal);
  }

  private MethodSubject build(boolean lineNumberForLocal) throws Exception {
    CodeInspector inspector =
        testForD8()
            .addProgramClassFileData(FlafDump.dump(lineNumberForLocal))
            .setMinApi(parameters.getApiLevel())
            .debug()
            .compile()
            .inspector();
    ClassSubject clazz = inspector.clazz("FlafKt");
    assertTrue(clazz.isPresent());
    MethodSubject method = clazz.uniqueMethodWithName("box");
    assertTrue(method.isPresent());
    return method;
  }

  @Test
  public void testWithLineNumberForLocal() throws Exception {
    MethodSubject method = build(true);
    assertTrue(hasExceptionLocal(method));
  }

  @Test
  public void testWithoutLineNumberForLocal() throws Exception {
    MethodSubject method = build(false);
    assertFalse(hasExceptionLocal(method));
  }
}
