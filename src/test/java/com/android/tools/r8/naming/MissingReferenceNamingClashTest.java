// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.DescriptorUtils.descriptorToJavaType;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** This is a reproduction of b/196406764 */
@RunWith(Parameterized.class)
public class MissingReferenceNamingClashTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MissingReferenceNamingClashTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    // The references to Missing will be rewritten to a.a but the definition will not be present.
    String newDescriptor = "La/a;";
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(A.class, Main.class)
            .addProgramClassFileData(
                transformer(Anno.class)
                    .replaceClassDescriptorInMembers(descriptor(Missing.class), newDescriptor)
                    .transform())
            .setMinApi(parameters.getApiLevel())
            .addKeepMainRule(Main.class)
            .addKeepClassAndMembersRules(Anno.class)
            .addKeepClassRulesWithAllowObfuscation(A.class)
            .addKeepRuntimeVisibleAnnotations()
            .addDontWarn(descriptorToJavaType(newDescriptor))
            .compile();
    if (parameters.isCfRuntime()) {
      compileResult
          .inspect(
              inspector -> {
                ClassSubject aSubject = inspector.clazz(A.class);
                assertThat(aSubject, isPresent());
                assertEquals(newDescriptor, aSubject.getFinalDescriptor());
              })
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutputLines("A::foo");
    } else {
      // TODO(b/196406764): This should not fail.
      AssertionError assertionError =
          assertThrows(
              AssertionError.class,
              () -> {
                compileResult.run(parameters.getRuntime(), Main.class);
              });
      assertThat(assertionError.getMessage(), containsString("Out-of-order type ids"));
    }
  }

  /* Will be missing on input */
  public enum Missing {
    FOO;
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface Anno {

    /* Rewritten to a.a */ Missing missing();
  }

  public static class A {

    public void foo() {
      System.out.println("A::foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new A().foo();
    }
  }
}
