// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumWithAssertionsDisabledStaticFieldTest extends TestBase {

  private enum AssertionTransformation {
    ENABLE,
    DISABLE,
    PASSTHROUGH
  }

  @Parameter(0)
  public AssertionTransformation assertionTransformation;

  @Parameter(1)
  public boolean enableRuntimeAssertions;

  @Parameter(2)
  public TestParameters parameters;

  @Parameters(name = "{2}, transformation: {0}, -ea: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        AssertionTransformation.values(),
        BooleanUtils.values(),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    assumeTrue(parameters.isCfRuntime() || !enableRuntimeAssertions);
    testForR8(parameters.getBackend())
        .addProgramClasses(
            EnumWithAssertionsDisabledStaticFieldMainClass.class,
            EnumWithAssertionsDisabledStaticFieldEnumClass.class)
        .addKeepMainRule(EnumWithAssertionsDisabledStaticFieldMainClass.class)
        .addEnumUnboxingInspector(
            inspector ->
                inspector.assertUnboxed(EnumWithAssertionsDisabledStaticFieldEnumClass.class))
        .addAssertionsConfiguration(
            builder -> {
              switch (assertionTransformation) {
                case ENABLE:
                  return builder.setCompileTimeEnable().setScopeAll().build();
                case DISABLE:
                  return builder.setCompileTimeDisable().setScopeAll().build();
                case PASSTHROUGH:
                  return builder.setPassthrough().setScopeAll().build();
                default:
                  throw new Unreachable();
              }
            })
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .enableRuntimeAssertions(enableRuntimeAssertions)
        .run(parameters.getRuntime(), EnumWithAssertionsDisabledStaticFieldMainClass.class)
        .applyIf(
            assertionTransformation == AssertionTransformation.ENABLE
                || (assertionTransformation == AssertionTransformation.PASSTHROUGH
                    && enableRuntimeAssertions),
            result -> result.assertFailureWithErrorThatThrows(AssertionError.class),
            TestRunResult::assertSuccessWithEmptyOutput);
  }
}

// Intentionally added as a top-level class because the $assertionsDisabled field is always
// synthesized on the outer-most class.
class EnumWithAssertionsDisabledStaticFieldMainClass {

  public static void main(String[] args) {
    EnumWithAssertionsDisabledStaticFieldEnumClass.A.fail();
  }
}

@NeverClassInline
enum EnumWithAssertionsDisabledStaticFieldEnumClass {
  A;

  @NeverInline
  public void fail() {
    assert false;
  }
}
