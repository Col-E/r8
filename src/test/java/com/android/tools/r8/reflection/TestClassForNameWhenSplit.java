// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.reflection;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dexsplitter.SplitterTestBase;
import com.android.tools.r8.references.Reference;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TestClassForNameWhenSplit extends TestBase {

  private final TestParameters parameters;
  private final String EXPECTED = "caught";

  @Parameters(name="{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public TestClassForNameWhenSplit(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testClassNotFound() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testClassForNameIsKeept() throws Exception {
    Path featurePath = temp.newFile("feature1.zip").toPath();
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .setMinApi(parameters.getApiLevel())
        .addFeatureSplit(
            builder ->
                SplitterTestBase.simpleSplitProvider(
                    builder, featurePath, temp, Foobar.class))
        .addKeepMainRule(Main.class)
        .addKeepClassRules(Foobar.class)
        .compile()
        .disassemble()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class Main {

    public static void main(String[] args) {
      try {
        foo();
      } catch (ClassNotFoundException e) {
        System.out.println("caught");
      } catch (IllegalAccessException e) {
        System.out.println("illegal access");
      } catch (InstantiationException e) {
        System.out.println("instantiation exception");
      }
    }

    private static void foo()
        throws ClassNotFoundException, IllegalAccessException, InstantiationException {
      try {
        // Ensure cl init has been assumed triggered
        new Foobar();
      } catch (NoClassDefFoundError e) { }
      // It is not valid to replace this with just classForName, even if we see that there is only
      // a trivial clinit or the fact that we have already triggered it above.
      Class<?> foobar =
          Class.forName("com.android.tools.r8.reflection.TestClassForNameWhenSplit$Foobar");
      foobar.newInstance();
    }
  }

  public static class Foobar {
    public Foobar() {
      if (System.currentTimeMillis() < 2) {
        System.out.println("A long time ago, in a galaxy far far away");
      }
    }
  }
}
