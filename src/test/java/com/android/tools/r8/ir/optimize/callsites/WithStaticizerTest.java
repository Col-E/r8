// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WithStaticizerTest extends TestBase {

  private static final Class<?> MAIN = Main.class;

  @Parameters(name = "{1}, experimental: {0}")
  public static List<Object[]> data() {
    // TODO(b/112831361): support for class staticizer in CF backend.
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  private final boolean enableExperimentalArgumentPropagation;
  private final TestParameters parameters;

  public WithStaticizerTest(
      boolean enableExperimentalArgumentPropagation, TestParameters parameters) {
    this.enableExperimentalArgumentPropagation = enableExperimentalArgumentPropagation;
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(WithStaticizerTest.class)
        .addKeepMainRule(MAIN)
        .addOptionsModification(
            options ->
                options
                    .callSiteOptimizationOptions()
                    .setEnableExperimentalArgumentPropagation(
                        enableExperimentalArgumentPropagation))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("Input")
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    // Check if the candidate is indeed staticized.
    ClassSubject companion = inspector.clazz(Host.Companion.class);
    assertThat(companion, not(isPresent()));

    // Null check in Companion#foo is migrated to Host#foo.
    ClassSubject host = inspector.clazz(Host.class);
    assertThat(host, isPresent());
    MethodSubject foo = host.uniqueMethodWithName("foo");
    assertThat(foo, isPresent());
    if (enableExperimentalArgumentPropagation) {
      assertTrue(foo.streamInstructions().noneMatch(InstructionSubject::isIf));
    } else {
      // TODO(b/139246447): Can optimize branches since `arg` is definitely not null.
      assertTrue(foo.streamInstructions().anyMatch(InstructionSubject::isIf));
    }
  }

  @NeverClassInline
  static class Host {
    private static final Companion companion = new Companion();

    @NeverClassInline
    static class Companion {
      @NeverInline
      public void foo(Object arg) {
        // Technically same as String#valueOf
        if (arg != null) {
          System.out.println(arg.toString());
        } else {
          System.out.println("null");
        }
      }
    }

    @NeverInline
    static void bar(Object arg) {
      companion.foo(arg);
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class Input {
    @NeverInline
    @Override
    public String toString() {
      return "Input";
    }
  }

  static class Main {
    public static void main(String[] args) {
      Host.bar(new Input());
    }
  }
}
