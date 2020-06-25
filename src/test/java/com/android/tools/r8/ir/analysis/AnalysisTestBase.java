// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis;

import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.Before;

public abstract class AnalysisTestBase extends TestBase {

  protected final TestParameters parameters;
  private final AndroidApp app;
  private final String className;

  public AppView<?> appView;

  public AnalysisTestBase(TestParameters parameters, Class<?> clazz) throws Exception {
    this.parameters = parameters;
    this.app =
        testForD8()
            .release()
            .setMinApi(parameters.getRuntime())
            .addProgramClasses(clazz)
            .compile()
            .app;
    this.className = clazz.getTypeName();
  }

  public AnalysisTestBase(
      TestParameters parameters, String mainClassName, Class<?>... classes) throws Exception {
    this.parameters = parameters;
    this.app =
        testForD8()
            .addProgramClasses(classes)
            .setMinApi(parameters.getRuntime())
            .compile()
            .app;
    this.className = mainClassName;
  }

  public AnalysisTestBase(TestParameters parameters, AndroidApp app, String className) {
    this.parameters = parameters;
    this.app = app;
    this.className = className;
  }

  @Before
  public void setup() throws Exception {
    appView = computeAppViewWithLiveness(app);
  }

  public void buildAndCheckIR(String methodName, Consumer<IRCode> irInspector) {
    CodeInspector inspector = new CodeInspector(appView.appInfo().app());
    MethodSubject methodSubject = inspector.clazz(className).uniqueMethodWithName(methodName);
    irInspector.accept(methodSubject.buildIR());
  }

  @SuppressWarnings("unchecked")
  public static <T extends Instruction> T getMatchingInstruction(
      IRCode code, Predicate<Instruction> predicate) {
    Instruction result = null;
    for (Instruction instruction : code.instructions()) {
      if (predicate.test(instruction)) {
        if (result != null) {
          fail();
        }
        result = instruction;
      }
    }
    if (result == null) {
      fail();
    }
    return (T) result;
  }
}
