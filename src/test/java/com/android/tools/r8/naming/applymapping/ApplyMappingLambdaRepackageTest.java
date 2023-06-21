// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApplyMappingLambdaRepackageTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult firstRunResult =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(Main.class, PackagePrivate.class, Foo.class)
            .setMinApi(parameters)
            .addKeepMainRule(Main.class)
            .addKeepRules("-repackageclasses foo")
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableNoAccessModificationAnnotationsForMembers()
            .run(parameters.getRuntime(), Main.class)
            .assertSuccessWithOutputLines("Hello World");
    R8TestRunResult secondRunResult =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(Main.class, PackagePrivate.class, Foo.class)
            .setMinApi(parameters)
            .addKeepMainRule(Main.class)
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableNoAccessModificationAnnotationsForMembers()
            .addApplyMapping(firstRunResult.proguardMap())
            .compile()
            .run(parameters.getRuntime(), Main.class);
    assertEquals(firstRunResult.proguardMap(), secondRunResult.proguardMap());
    secondRunResult.assertSuccessWithOutputLines("Hello World");
  }

  @NeverClassInline
  public static class PackagePrivate {

    @NeverInline
    @NoAccessModification
    void print() {
      System.out.println("Hello World");
    }
  }

  interface Foo {

    @NeverInline
    void doSomething(PackagePrivate o);

    @NeverInline
    static Foo getInstance() {
      return x -> {
        x.print();
      };
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Foo.getInstance().doSomething(new PackagePrivate());
    }
  }
}
