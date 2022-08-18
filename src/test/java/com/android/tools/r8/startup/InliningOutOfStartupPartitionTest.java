// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.experimental.startup.StartupClass;
import com.android.tools.r8.experimental.startup.StartupItem;
import com.android.tools.r8.experimental.startup.StartupMethod;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.utils.StartupTestingUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InliningOutOfStartupPartitionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    List<StartupItem<ClassReference, MethodReference, ?>> startupItems =
        ImmutableList.of(
            StartupClass.referenceBuilder()
                .setClassReference(Reference.classFromClass(Main.class))
                .build(),
            StartupMethod.referenceBuilder()
                .setMethodReference(MethodReferenceUtils.mainMethod(Main.class))
                .build());
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .apply(
            testBuilder -> StartupTestingUtils.setStartupConfiguration(testBuilder, startupItems))
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());
              assertThat(mainClassSubject.uniqueMethodWithName("postStartupMethod"), isAbsent());

              ClassSubject postStartupClassSubject = inspector.clazz(PostStartupClass.class);
              // TODO(b/242815611): Should be present since inlining increases the size of the
              //  startup.
              assertThat(postStartupClassSubject, isAbsent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      PostStartupClass.runPostStartup();
    }

    static void postStartupMethod() {
      System.out.println("Hello, world!");
    }
  }

  static class PostStartupClass {

    static void runPostStartup() {
      Main.postStartupMethod();
    }
  }
}
