// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.naming.AdaptResourceFileContentsTest.DataResourceConsumerForTesting;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ServiceLoaderTest extends TestBase {

  private final Backend backend;
  private final boolean includeWorldGreeter;

  private DataResourceConsumerForTesting dataResourceConsumer;

  @Parameters(name = "Backend: {0}, include WorldGreeter: {1}")
  public static List<Object[]> data() {
    return buildParameters(Backend.values(), BooleanUtils.values());
  }

  public ServiceLoaderTest(Backend backend, boolean includeWorldGreeter) {
    this.backend = backend;
    this.includeWorldGreeter = includeWorldGreeter;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = includeWorldGreeter ? "Hello world!" : "Hello";

    List<String> serviceImplementations = Lists.newArrayList(HelloGreeter.class.getTypeName());
    if (includeWorldGreeter) {
      serviceImplementations.add(WorldGreeter.class.getTypeName());
    }

    CodeInspector inspector =
        testForR8(backend)
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
                })
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

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
    assertEquals(helloGreeterSubject.getFinalName(), lines.get(0));
    if (includeWorldGreeter) {
      assertEquals(worldGreeterSubject.getFinalName(), lines.get(1));
    }

    // TODO(b/124181030): Verify that -whyareyoukeeping works as intended.
  }

  @Test
  public void testResourceElimination() throws Exception {
    String expectedOutput = "Hello world!";

    List<String> serviceImplementations = Lists.newArrayList(HelloGreeter.class.getTypeName());
    if (includeWorldGreeter) {
      serviceImplementations.add(WorldGreeter.class.getTypeName());
    }

    CodeInspector inspector =
        testForR8(backend)
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
            .run(OtherTestClass.class)
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

    @Override
    public String greeting() {
      return "Hello";
    }
  }

  public static class WorldGreeter implements Greeter {

    @Override
    public String greeting() {
      return " world!";
    }
  }
}
