// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.references.Reference.classFromClass;
import static com.android.tools.r8.references.Reference.methodFromMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DataResourceConsumerForTesting;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector.QueryNode;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ServiceLoaderTest extends TestBase {

  private final boolean includeWorldGreeter;
  private final TestParameters parameters;

  private DataResourceConsumerForTesting dataResourceConsumer;
  private static final String LINE_COMMENT = "# This is a comment.";
  private static final String POSTFIX_COMMENT = "# POSTFIX_COMMENT";

  @Parameters(name = "{1}, include WorldGreeter: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public ServiceLoaderTest(boolean includeWorldGreeter, TestParameters parameters) {
    this.includeWorldGreeter = includeWorldGreeter;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = includeWorldGreeter ? "Hello world!" : "Hello";

    List<String> serviceImplementations = Lists.newArrayList();

    // Add a comment to the service implementations file.
    serviceImplementations.add(LINE_COMMENT);

    // Postfix a valid line with a comment.
    serviceImplementations.add(HelloGreeter.class.getTypeName() + POSTFIX_COMMENT);

    if (includeWorldGreeter) {
      serviceImplementations.add(WorldGreeter.class.getTypeName());
    }

    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addInnerClasses(ServiceLoaderTest.class)
            .addKeepMainRule(TestClass.class)
            .addDataEntryResources(
                DataEntryResource.fromBytes(
                    StringUtils.lines(serviceImplementations).getBytes(),
                    "META-INF/services/" + Greeter.class.getTypeName(),
                    Origin.unknown()))
            .addOptionsModification(
                options -> {
                  dataResourceConsumer =
                      new DataResourceConsumerForTesting(options.dataResourceConsumer);
                  options.dataResourceConsumer = dataResourceConsumer;
                  options.inlinerOptions().enableInliningOfInvokesWithNullableReceivers = false;
                })
            .enableGraphInspector()
            .enableMemberValuePropagationAnnotations()
            .enableNoMethodStaticizingAnnotations()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(expectedOutput);

    CodeInspector inspector = result.inspector();

    ClassSubject greeterSubject = inspector.clazz(Greeter.class);
    assertEquals(includeWorldGreeter, greeterSubject.isPresent());

    ClassSubject helloGreeterSubject = inspector.clazz(HelloGreeter.class);
    assertThat(helloGreeterSubject, isPresent());

    ClassSubject worldGreeterSubject = inspector.clazz(WorldGreeter.class);
    assertEquals(includeWorldGreeter, worldGreeterSubject.isPresent());

    String serviceFileName =
        includeWorldGreeter ? greeterSubject.getFinalName() : helloGreeterSubject.getFinalName();
    List<String> lines =
        dataResourceConsumer.get(AppServices.SERVICE_DIRECTORY_NAME + serviceFileName);
    assertEquals(includeWorldGreeter ? 2 : 1, lines.size());
    // Comments in application service files are not carried over to the output.
    lines.forEach(line -> assertFalse(line.contains(LINE_COMMENT)));
    lines.forEach(line -> assertFalse(line.contains(POSTFIX_COMMENT)));
    if (includeWorldGreeter) {
      assertTrue(
          helloGreeterSubject.getFinalName().compareTo(worldGreeterSubject.getFinalName()) < 0);
    }
    assertEquals(helloGreeterSubject.getFinalName(), lines.get(0));
    if (includeWorldGreeter) {
      assertEquals(worldGreeterSubject.getFinalName(), lines.get(1));
    }

    verifyGraphInformation(result.graphInspector());
  }

  private void verifyGraphInformation(GraphInspector graphInspector) throws Exception {
    assertEquals(1, graphInspector.getRoots().size());
    QueryNode keepMain = graphInspector.rule(Origin.unknown(), 1, 1).assertRoot();

    MethodReference mainMethod =
        methodFromMethod(TestClass.class.getDeclaredMethod("main", String[].class));
    graphInspector.method(mainMethod).assertKeptBy(keepMain);

    ClassReference helloGreeterClass = classFromClass(HelloGreeter.class);
    MethodReference helloGreeterInitMethod = methodFromMethod(HelloGreeter.class.getConstructor());
    MethodReference helloGreeterGreetingMethod =
        methodFromMethod(HelloGreeter.class.getDeclaredMethod("greeting"));

    graphInspector.clazz(helloGreeterClass).assertKeptBy(graphInspector.method(mainMethod));
    graphInspector.clazz(helloGreeterClass).assertReflectedFrom(mainMethod);
    graphInspector.method(helloGreeterInitMethod).assertReflectedFrom(mainMethod);

    // TODO(b/121313747): greeting() is called from main(), so this should be strengthened to
    //  `assertInvokedFrom(mainMethod)`.
    graphInspector.method(helloGreeterGreetingMethod).assertPresent();

    if (includeWorldGreeter) {
      ClassReference worldGreeterClass = classFromClass(WorldGreeter.class);
      MethodReference worldGreeterInitMethod =
          methodFromMethod(WorldGreeter.class.getConstructor());
      MethodReference worldGreeterGreetingMethod =
          methodFromMethod(WorldGreeter.class.getDeclaredMethod("greeting"));

      graphInspector.clazz(worldGreeterClass).assertKeptBy(graphInspector.method(mainMethod));
      graphInspector.clazz(worldGreeterClass).assertReflectedFrom(mainMethod);
      graphInspector.method(worldGreeterInitMethod).assertReflectedFrom(mainMethod);

      // TODO(b/121313747): greeting() is called from main(), so this should be strengthened to
      //  `assertInvokedFrom(mainMethod)`.
      graphInspector.method(worldGreeterGreetingMethod).assertPresent();
    }
  }

  @Test
  public void testResourceElimination() throws Exception {
    String expectedOutput = "Hello world!";

    List<String> serviceImplementations = Lists.newArrayList(HelloGreeter.class.getTypeName());
    if (includeWorldGreeter) {
      serviceImplementations.add(WorldGreeter.class.getTypeName());
    }

    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(ServiceLoaderTest.class)
            .addKeepMainRule(OtherTestClass.class)
            .addDataEntryResources(
                DataEntryResource.fromBytes(
                    StringUtils.lines(serviceImplementations).getBytes(),
                    "META-INF/services/" + Greeter.class.getTypeName(),
                    Origin.unknown()))
            .addOptionsModification(
                options -> {
                  dataResourceConsumer =
                      new DataResourceConsumerForTesting(options.dataResourceConsumer);
                  options.dataResourceConsumer = dataResourceConsumer;
                })
            .enableNoMethodStaticizingAnnotations()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), OtherTestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    ClassSubject greeterSubject = inspector.clazz(Greeter.class);
    assertThat(greeterSubject, not(isPresent()));

    ClassSubject helloGreeterSubject = inspector.clazz(HelloGreeter.class);
    assertThat(helloGreeterSubject, not(isPresent()));

    ClassSubject worldGreeterSubject = inspector.clazz(WorldGreeter.class);
    assertThat(worldGreeterSubject, not(isPresent()));

    assertTrue(dataResourceConsumer.isEmpty());
  }

  static class TestClass {

    public static void main(String[] args) {
      for (Greeter greeter : ServiceLoader.load(Greeter.class)) {
        System.out.print(greeter.greeting());
      }
    }
  }

  static class OtherTestClass {

    public static void main(String[] args) {
      System.out.print("Hello world!");
    }
  }

  public interface Greeter {

    String greeting();
  }

  public static class HelloGreeter implements Greeter {

    @NeverPropagateValue
    @NoMethodStaticizing
    @Override
    public String greeting() {
      return "Hello";
    }
  }

  public static class WorldGreeter implements Greeter {

    @NoMethodStaticizing
    @Override
    public String greeting() {
      return " world!";
    }
  }
}
