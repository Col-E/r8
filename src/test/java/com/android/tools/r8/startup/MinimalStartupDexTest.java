// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.experimental.startup.StartupClass;
import com.android.tools.r8.experimental.startup.StartupConfiguration;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MinimalStartupDexTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.L)
        .build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .addOptionsModification(
            options ->
                options
                    .getStartupOptions()
                    .setEnableMinimalStartupDex(true)
                    .setEnableStartupCompletenessCheckForTesting()
                    .setStartupConfiguration(
                        StartupConfiguration.builder()
                            .addStartupClass(
                                StartupClass.dexBuilder()
                                    .setClassReference(
                                        toDexType(Main.class, options.dexItemFactory()))
                                    .build())
                            .addStartupClass(
                                StartupClass.dexBuilder()
                                    .setClassReference(
                                        toDexType(AStartupClass.class, options.dexItemFactory()))
                                    .build())
                            .build()))
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspectMultiDex(
            primaryDexInspector -> {
              // StartupClass should be in the primary dex.
              ClassSubject startupClassSubject = primaryDexInspector.clazz(AStartupClass.class);
              assertThat(startupClassSubject, isPresent());

              MethodSubject startupMethodSubject = startupClassSubject.uniqueMethodWithName("foo");
              assertThat(startupMethodSubject, isPresent());
              assertTrue(
                  startupMethodSubject.streamInstructions().noneMatch(InstructionSubject::isThrow));
            },
            secondaryDexInspector -> {
              // NonStartupClass should be in the secondary dex and should be transformed such that
              // all methods throw null.
              ClassSubject nonStartupClassSubject =
                  secondaryDexInspector.clazz(NonStartupClass.class);
              assertThat(nonStartupClassSubject, isPresent());

              MethodSubject nonStartupClinitSubject = nonStartupClassSubject.clinit();
              assertThat(nonStartupClinitSubject, isPresent());
              assertTrue(
                  nonStartupClinitSubject
                      .streamInstructions()
                      .anyMatch(InstructionSubject::isThrow));

              MethodSubject nonStartupMethodSubject =
                  nonStartupClassSubject.uniqueMethodWithName("bar");
              assertThat(nonStartupMethodSubject, isPresent());
              assertTrue(
                  nonStartupMethodSubject
                      .streamInstructions()
                      .anyMatch(InstructionSubject::isThrow));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo");
  }

  static class Main {

    public static void main(String[] args) {
      AStartupClass.foo();
    }

    // @Keep
    public void onClick() {
      NonStartupClass.bar();
    }
  }

  static class AStartupClass {

    @NeverInline
    static void foo() {
      System.out.println("foo");
    }
  }

  static class NonStartupClass {

    @NeverInline
    static void bar() {
      System.out.println("bar");
    }
  }
}
