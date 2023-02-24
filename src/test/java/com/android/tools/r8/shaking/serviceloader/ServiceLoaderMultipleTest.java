// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.serviceloader;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.ServiceLoader;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ServiceLoaderMultipleTest extends TestBase {

  private static Path jarWithServiceInterface;
  private static Path jarWithImpl1;
  private static Path jarWithImpl2;
  private static Path jarWithImpl1Impl2;
  private static Path jarWithImpl2Impl1;
  private static Path jarWithMultipleImpl1;
  private static Path jarWithMultipleImpl1WithNoClasses;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @BeforeClass
  public static void buildServiceJars() throws Exception {
    jarWithServiceInterface = createJarBuilder().addClass(Greeter.class).build();
    jarWithImpl1 =
        createJarBuilder()
            .addClass(GreeterImpl1.class)
            .addResource(
                "META-INF/services/" + Greeter.class.getTypeName(),
                StringUtils.lines(GreeterImpl1.class.getTypeName()))
            .build();
    jarWithImpl2 =
        createJarBuilder()
            .addClass(GreeterImpl2.class)
            .addResource(
                "META-INF/services/" + Greeter.class.getTypeName(),
                StringUtils.lines(GreeterImpl2.class.getTypeName()))
            .build();
    jarWithImpl1Impl2 =
        createJarBuilder()
            .addClass(GreeterImpl1.class)
            .addClass(GreeterImpl2.class)
            .addResource(
                "META-INF/services/" + Greeter.class.getTypeName(),
                StringUtils.lines(
                    GreeterImpl1.class.getTypeName(), GreeterImpl2.class.getTypeName()))
            .build();
    jarWithImpl2Impl1 =
        createJarBuilder()
            .addClass(GreeterImpl1.class)
            .addClass(GreeterImpl2.class)
            .addResource(
                "META-INF/services/" + Greeter.class.getTypeName(),
                StringUtils.lines(
                    GreeterImpl2.class.getTypeName(), GreeterImpl1.class.getTypeName()))
            .build();
    jarWithMultipleImpl1 =
        createJarBuilder()
            .addClass(GreeterImpl1.class)
            .addResource(
                "META-INF/services/" + Greeter.class.getTypeName(),
                StringUtils.lines(
                    GreeterImpl1.class.getTypeName(), GreeterImpl1.class.getTypeName()))
            .build();
    jarWithMultipleImpl1WithNoClasses =
        createJarBuilder()
            .addResource(
                "META-INF/services/" + Greeter.class.getTypeName(),
                StringUtils.lines(
                    GreeterImpl1.class.getTypeName(), GreeterImpl1.class.getTypeName()))
            .build();
  }

  private static JarBuilder createJarBuilder() throws Exception {
    return JarBuilder.builder(getStaticTemp());
  }

  @Test
  public void testMultipleServicesWithSameImplementationJVM() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addClasspath(jarWithServiceInterface, jarWithMultipleImpl1, jarWithMultipleImpl1)
        .addProgramClasses(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello 1");
  }

  @Test
  public void testMultipleServicesWithSameImplementationR8() throws Exception {
    testForR8(parameters.getBackend())
        // Don't pass jarWithMultipleImpl1 twice as to the JVM, as R8 will fail with multiple
        // definition of the same class.
        .addProgramFiles(
            jarWithServiceInterface, jarWithMultipleImpl1, jarWithMultipleImpl1WithNoClasses)
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello 1");
  }

  @Test
  public void testMultipleServicesDifferentJarsJVM() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addClasspath(jarWithServiceInterface, jarWithImpl1, jarWithImpl2)
        .addProgramClasses(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello 1", "Hello 2");
  }

  @Test
  public void testMultipleServicesDifferentJarsR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(jarWithServiceInterface, jarWithImpl1, jarWithImpl2)
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello 1", "Hello 2");
  }

  @Test
  public void testMultipleServicesDifferentJarsOppositeOrderJVM() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addClasspath(jarWithServiceInterface, jarWithImpl2, jarWithImpl1)
        .addProgramClasses(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello 2", "Hello 1");
  }

  @Test
  public void testMultipleServicesDifferentJarsOppositeOrderR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(jarWithServiceInterface, jarWithImpl2, jarWithImpl1)
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello 2", "Hello 1");
  }

  @Test
  public void testMultipleServicesSameJarsJVM() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addClasspath(jarWithServiceInterface, jarWithImpl1Impl2)
        .addProgramClasses(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello 1", "Hello 2");
  }

  @Test
  public void testMultipleServicesSameJarsR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(jarWithServiceInterface, jarWithImpl1Impl2)
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello 1", "Hello 2");
  }

  @Test
  public void testMultipleServicesSameJarsOppositeOrderJVM() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addClasspath(jarWithServiceInterface, jarWithImpl2Impl1)
        .addProgramClasses(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello 2", "Hello 1");
  }

  @Test
  public void testMultipleServicesSameJarsOppositeOrderR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(jarWithServiceInterface, jarWithImpl2Impl1)
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello 2", "Hello 1");
  }

  static class TestClass {

    public static void main(String[] args) {
      for (Greeter greeter : ServiceLoader.load(Greeter.class)) {
        System.out.println(greeter.greeting());
      }
    }
  }

  public interface Greeter {

    String greeting();
  }

  public static class GreeterImpl1 implements Greeter {

    @Override
    public String greeting() {
      return "Hello 1";
    }
  }

  public static class GreeterImpl2 implements Greeter {

    @Override
    public String greeting() {
      return "Hello 2";
    }
  }
}
