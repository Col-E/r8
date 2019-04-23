// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.NestAccessControl;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexClass;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestAttributesTest extends TestBase {

  static final String EXAMPLE_DIR = ToolHelper.EXAMPLES_JAVA11_BUILD_DIR;

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimesStartingFromIncluding(CfVm.JDK11).build();
  }

  public NestAttributesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNestMatesAttributes() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(Paths.get(EXAMPLE_DIR, "nestHostExample" + JAR_EXTENSION))
        .addKeepAllClassesRule()
        .compile()
        .inspect(
            inspector -> {
              assertEquals(11, inspector.allClasses().size());
              ImmutableSet<String> outerClassNames =
                  ImmutableSet.of(
                      "NestHostExample",
                      "BasicNestHostWithInnerClass",
                      "BasicNestHostWithAnonymousInnerClass");
              inspector.forAllClasses(
                  classSubject -> {
                    DexClass dexClass = classSubject.getDexClass();
                    assertTrue(dexClass.isInANest());
                    if (outerClassNames.contains(dexClass.type.getName())) {
                      assertNull(dexClass.getNestHostClassAttribute());
                      assertFalse(dexClass.getNestMembersClassAttributes().isEmpty());
                    } else {
                      assertTrue(dexClass.getNestMembersClassAttributes().isEmpty());
                      assertTrue(
                          outerClassNames.contains(
                              dexClass.getNestHostClassAttribute().getNestHost().getName()));
                    }
                  });
            });
  }
}
