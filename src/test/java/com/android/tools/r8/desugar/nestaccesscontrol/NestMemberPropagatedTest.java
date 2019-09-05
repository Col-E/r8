// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.classesMatching;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.getExpectedResult;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.getMainClass;
import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestMemberPropagatedTest extends TestBase {

  public NestMemberPropagatedTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimesStartingFromIncluding(CfVm.JDK11).build();
  }

  @Test
  public void testPvtMemberPropagated() throws Exception {
    List<Path> toCompile = classesMatching("NestPvtFieldPropagated");
    testForR8(parameters.getBackend())
        .addKeepMainRule(getMainClass("memberPropagated"))
        .noMinification()
        .addOptionsModification(
            options -> {
              options.enableClassInlining = false;
            })
        .addProgramFiles(toCompile)
        .compile()
        .inspect(this::assertMemberPropagated)
        .run(parameters.getRuntime(), getMainClass("memberPropagated"))
        .assertSuccessWithOutput(getExpectedResult("memberPropagated"));
  }

  private void assertMemberPropagated(CodeInspector inspector) {
    for (FoundClassSubject subj : inspector.allClasses()) {
      assertEquals(0, subj.allFields().size());
    }
  }
}
