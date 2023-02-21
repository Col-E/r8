// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.serviceloaders;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DataResourceConsumerForTesting;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// Test where the service class and the service implementation class are missing.
@RunWith(Parameterized.class)
public class MissingServiceClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MissingServiceClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    DataResourceConsumerForTesting dataResourceConsumer = new DataResourceConsumerForTesting();
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addDataEntryResources(
            DataEntryResource.fromBytes(
                StringUtils.lines(ServiceImpl.class.getTypeName()).getBytes(),
                AppServices.SERVICE_DIRECTORY_NAME + Service.class.getTypeName(),
                Origin.unknown()))
        .addOptionsModification(o -> o.dataResourceConsumer = dataResourceConsumer)
        .addDontWarn(ServiceImpl.class)
        .allowDiagnosticWarningMessages()
        .setMinApi(parameters)
        .compile()
        .inspectDiagnosticMessages(this::inspectDiagnosticMessagesWithDontWarn)
        .addRunClasspathClasses(Service.class, ServiceImpl.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithEmptyOutput();

    inspectResource(
        dataResourceConsumer.get(AppServices.SERVICE_DIRECTORY_NAME + Service.class.getTypeName()));
  }

  @Test
  public void testMainDex() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForMainDexListGenerator()
        .addProgramClasses(TestClass.class)
        .addLibraryFiles(ToolHelper.getFirstSupportedAndroidJar(parameters.getApiLevel()))
        .addMainDexRules(keepMainProguardConfiguration(TestClass.class))
        .addDataEntryResources(
            DataEntryResource.fromBytes(
                StringUtils.lines(ServiceImpl.class.getTypeName()).getBytes(),
                AppServices.SERVICE_DIRECTORY_NAME + Service.class.getTypeName(),
                Origin.unknown()))
        .run()
        .inspectDiagnosticMessages(this::inspectDiagnosticMessagesWithoutDontWarn);
  }

  private void inspectDiagnosticMessagesWithDontWarn(TestDiagnosticMessages diagnostics) {
    diagnostics
        .assertOnlyWarnings()
        .assertWarningsMatch(
            diagnosticMessage(
                containsString(
                    "Unexpected reference to missing service class: "
                        + AppServices.SERVICE_DIRECTORY_NAME
                        + Service.class.getTypeName()
                        + ".")));
  }

  private void inspectDiagnosticMessagesWithoutDontWarn(TestDiagnosticMessages inspector) {
    inspector
        .assertOnlyWarnings()
        .assertWarningsMatch(
            diagnosticMessage(
                containsString(
                    "Unexpected reference to missing service class: "
                        + AppServices.SERVICE_DIRECTORY_NAME
                        + Service.class.getTypeName()
                        + ".")),
            diagnosticMessage(
                containsString(
                    "Unexpected reference to missing service implementation class in "
                        + AppServices.SERVICE_DIRECTORY_NAME
                        + Service.class.getTypeName()
                        + ": "
                        + ServiceImpl.class.getTypeName()
                        + ".")));
  }

  private void inspectResource(List<String> contents) {
    assertNotNull(contents);
    assertEquals(1, contents.size());
    assertEquals(ServiceImpl.class.getTypeName(), contents.get(0));
  }

  static class TestClass {

    public static void main(String[] args) throws ClassNotFoundException {
      for (Object service : ServiceLoader.load(getServiceClass())) {
        System.out.println(service.toString());
      }
    }

    static Class<?> getServiceClass() throws ClassNotFoundException {
      // Get the Service class without accessing it directly. Accessing it directly would cause a
      // compilation error due the the class being absent. This can be avoided by adding -dontwarn,
      // but then R8 don't report a warning for the incorrect resource in META-INF/services, which
      // this is trying to test.
      return Class.forName(
          "com.android.tools.r8.rewrite.serviceloaders.MissingServiceClassTest$Service");
    }
  }

  public interface Service {}

  public static class ServiceImpl implements Service {

    @Override
    public String toString() {
      return "Hello world!";
    }
  }
}
