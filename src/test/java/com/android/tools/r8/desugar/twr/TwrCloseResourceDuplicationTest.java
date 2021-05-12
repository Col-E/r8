// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.twr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.io.IOException;
import java.util.List;
import java.util.jar.JarFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TwrCloseResourceDuplicationTest extends TestBase {

  static final int INPUT_CLASSES = 3;

  static final String EXPECTED =
      StringUtils.lines(
          "foo opened 1",
          "foo post close 1",
          "foo opened 2",
          "foo caught from 2: RuntimeException",
          "foo post close 2",
          "bar opened 1",
          "bar post close 1",
          "bar opened 2",
          "bar caught from 2: RuntimeException",
          "bar post close 2");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public TwrCloseResourceDuplicationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private String getZipFile() throws IOException {
    return ZipUtils.ZipBuilder.builder(temp.newFile("file.zip").toPath())
        // DEX VMs from 4.4 up-to 9.0 including, will fail if no entry is added.
        .addBytes("entry", new byte[1])
        .build()
        .toString();
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class, getZipFile())
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class, getZipFile())
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              // There should be exactly one synthetic class besides the three program classes.
              int expectedSynthetics =
                  parameters.getApiLevel().isLessThan(apiLevelWithTwrCloseResourceSupport())
                      ? 1
                      : 0;
              assertEquals(INPUT_CLASSES + expectedSynthetics, inspector.allClasses().size());
            });
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(Foo.class, Bar.class)
        .applyIf(
            parameters.getApiLevel().isLessThan(apiLevelWithTwrCloseResourceSupport()),
            builder -> builder.addDontWarn("java.lang.AutoCloseable"))
        .setMinApi(parameters.getApiLevel())
        .noMinification()
        .run(parameters.getRuntime(), TestClass.class, getZipFile())
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              // R8 will optimize the generated methods for the two cases below where the thrown
              // exception is known or not, thus the synthetic methods will be 2.
              int expectedSynthetics =
                  BooleanUtils.intValue(
                      parameters.getApiLevel().isLessThan(apiLevelWithTwrCloseResourceSupport()));
              List<FoundClassSubject> foundClassSubjects = inspector.allClasses();
              assertEquals(INPUT_CLASSES + expectedSynthetics, foundClassSubjects.size());
            });
  }

  static class Foo {
    void foo(String name) {
      try (JarFile f = new JarFile(name)) {
        System.out.println("foo opened 1");
      } catch (Exception e) {
        System.out.println("foo caught from 1: " + e.getClass().getSimpleName());
      } finally {
        System.out.println("foo post close 1");
      }
      try (JarFile f = new JarFile(name)) {
        System.out.println("foo opened 2");
        throw new RuntimeException();
      } catch (Exception e) {
        System.out.println("foo caught from 2: " + e.getClass().getSimpleName());
      } finally {
        System.out.println("foo post close 2");
      }
    }
  }

  static class Bar {
    void bar(String name) {
      try (JarFile f = new JarFile(name)) {
        System.out.println("bar opened 1");
      } catch (Exception e) {
        System.out.println("bar caught from 1: " + e.getClass().getSimpleName());
      } finally {
        System.out.println("bar post close 1");
      }
      try (JarFile f = new JarFile(name)) {
        System.out.println("bar opened 2");
        throw new RuntimeException();
      } catch (Exception e) {
        System.out.println("bar caught from 2: " + e.getClass().getSimpleName());
      } finally {
        System.out.println("bar post close 2");
      }
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      new Foo().foo(args[0]);
      new Bar().bar(args[0]);
    }
  }
}
