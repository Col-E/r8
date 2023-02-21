// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.serviceloaders;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.DataResourceConsumerForTesting;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.io.ByteStreams;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// Test where a service implementation class is missing.
@RunWith(Parameterized.class)
public class MissingServiceImplementationClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MissingServiceImplementationClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Box<DataResourceConsumerForTesting> dataResourceConsumer = new Box<>();
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(TestClass.class, Service.class)
            .addDontWarn(ServiceImpl.class)
            .addKeepMainRule(TestClass.class)
            .addKeepClassAndMembersRulesWithAllowObfuscation(Service.class)
            .addDataEntryResources(
                DataEntryResource.fromBytes(
                    StringUtils.lines(ServiceImpl.class.getTypeName()).getBytes(),
                    AppServices.SERVICE_DIRECTORY_NAME + Service.class.getTypeName(),
                    Origin.unknown()))
            .addOptionsModification(
                options -> {
                  dataResourceConsumer.set(
                      new DataResourceConsumerForTesting(options.dataResourceConsumer));
                  options.dataResourceConsumer = dataResourceConsumer.get();
                })
            .setMinApi(parameters)
            .compile();

    CodeInspector inspector = compileResult.inspector();
    ClassSubject serviceClassSubject = inspector.clazz(Service.class);
    assertThat(serviceClassSubject, isPresent());

    // Verify that ServiceImpl was not removed from META-INF/services/[...]Service.
    inspectResource(
        dataResourceConsumer.get().get("META-INF/services/" + serviceClassSubject.getFinalName()));

    // Execution fails since META-INF/services/[...]Service is referring to a missing class.
    compileResult
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(ServiceConfigurationError.class);

    // Verify that it is still possible to inject a new implementation of Service after the
    // compilation.
    testForR8(parameters.getBackend())
        .addProgramClasses(ServiceImpl.class)
        .addClasspathClasses(Service.class)
        .addKeepAllClassesRule()
        .addApplyMapping(compileResult.getProguardMap())
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(
            transformServiceDeclarationInProgram(
                compileResult.writeToZip(),
                AppServices.SERVICE_DIRECTORY_NAME + serviceClassSubject.getFinalName(),
                ServiceImpl.class.getTypeName()))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspectResource(List<String> contents) {
    assertNotNull(contents);
    assertEquals(1, contents.size());
    assertEquals(ServiceImpl.class.getTypeName(), contents.get(0));
  }

  private Path transformServiceDeclarationInProgram(Path program, String fileName, String contents)
      throws Exception {
    Path newProgram = temp.newFolder().toPath().resolve("program.jar");
    try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(newProgram))) {
      try (ZipInputStream inputStream = new ZipInputStream(Files.newInputStream(program))) {
        ZipEntry next = inputStream.getNextEntry();
        while (next != null) {
          outputStream.putNextEntry(new ZipEntry(next.getName()));
          if (next.getName().equals(fileName)) {
            outputStream.write(contents.getBytes());
          } else {
            outputStream.write(ByteStreams.toByteArray(inputStream));
          }
          outputStream.closeEntry();
          next = inputStream.getNextEntry();
        }
      }
    }
    return newProgram;
  }

  static class TestClass {

    public static void main(String[] args) {
      for (Object object : ServiceLoader.load(getServiceClass())) {
        if (object instanceof Service) {
          Service service = (Service) object;
          service.greet();
        }
      }
    }

    static Class<?> getServiceClass() {
      return System.currentTimeMillis() >= 0 ? Service.class : Object.class;
    }
  }

  public interface Service {

    void greet();
  }

  public static class ServiceImpl implements Service {

    @Override
    public void greet() {
      System.out.println("Hello world!");
    }
  }
}
