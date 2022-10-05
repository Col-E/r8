// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.staticizer;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InstanceInsideCompanionTest extends TestBase {
  private static final Class<?> MAIN = Main.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // TODO(b/112831361): support for class staticizer in CF backend.
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;

  public InstanceInsideCompanionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void b143684491() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InstanceInsideCompanionTest.class)
        .addKeepMainRule(MAIN)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("Candidate#foo(false)");
    // TODO(b/159174309): Disable inspection until fixed.
    // .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    // Check if the instance is gone.
    ClassSubject host = inspector.clazz(Candidate.Host.class);
    assertThat(host, isPresent());
    FieldSubject instance = host.uniqueFieldWithOriginalName("INSTANCE");
    assertThat(instance, not(isPresent()));

    ClassSubject candidate = inspector.clazz(Candidate.class);
    assertThat(candidate, not(isPresent()));

    // Check if the candidate method is staticized and migrated.
    MethodSubject foo = host.uniqueMethodWithOriginalName("foo");
    assertThat(foo, isPresent());
    assertTrue(foo.isStatic());
    assertTrue(
        foo.streamInstructions().anyMatch(
            i -> i.isInvokeVirtual()
                && i.getMethod().toSourceString().contains("PrintStream.println")));
  }

  @NeverClassInline
  static class Candidate {
    private static class Host {
      static final Candidate INSTANCE = new Candidate();
    }

    public static Candidate getInstance() {
      return Host.INSTANCE;
    }

    @NeverInline
    public void foo(Object arg) {
      System.out.println("Candidate#foo(" + (arg != null) + ")");
    }
  }

  static class Main {
    public static void main(String[] args) {
      Candidate.getInstance().foo(null);
    }
  }
}
