// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.Before;

public class TypeAnalysisTestBase extends TestBase {

  private final AndroidApp app;
  private final String className;
  private final InternalOptions options = new InternalOptions();

  AppInfo appInfo;

  public TypeAnalysisTestBase(Class<?> clazz) throws Exception {
    this.app = testForD8().release().addProgramClasses(clazz).compile().app;
    this.className = clazz.getTypeName();
  }

  public TypeAnalysisTestBase(AndroidApp app, String className) {
    this.app = app;
    this.className = className;
  }

  @Before
  public void setup() throws Exception {
    appInfo =
        new AppInfo(
            new ApplicationReader(app, options, new Timing("TypeAnalysisTest.appReader")).read());
  }

  public void buildAndCheckIR(String methodName, Consumer<IRCode> irInspector) throws Exception {
    CodeInspector inspector = new CodeInspector(app);
    MethodSubject methodSubject = inspector.clazz(className).uniqueMethodWithName(methodName);
    IRCode code =
        methodSubject
            .getMethod()
            .buildIR(appInfo, GraphLense.getIdentityLense(), options, Origin.unknown());
    irInspector.accept(code);
  }

  @SuppressWarnings("unchecked")
  public static <T extends Instruction> T getMatchingInstruction(
      IRCode code, Predicate<Instruction> predicate) {
    Instruction result = null;
    Iterable<Instruction> instructions = code::instructionIterator;
    for (Instruction instruction : instructions) {
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
