// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.serviceloaders;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.io.ByteStreams;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// Test where the resource META-INF/services/Service for a service class Service is missing.
//
// The program succeeds with the expected output when META-INF/services/Service is bundled with the
// application after the compilation.
@RunWith(Parameterized.class)
public class ServiceWithoutResourceFileAtCompileTimeTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ServiceWithoutResourceFileAtCompileTimeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(TestClass.class, Service.class)
            .addKeepMainRule(TestClass.class)
            .addKeepClassAndMembersRulesWithAllowObfuscation(Service.class)
            .setMinApi(parameters)
            .compile();

    CodeInspector inspector = compileResult.inspector();
    ClassSubject serviceClassSubject = inspector.clazz(Service.class);
    assertThat(serviceClassSubject, isPresent());

    // Execution succeeds with the empty output since META-INF/services/[...]Service is missing.
    compileResult.run(parameters.getRuntime(), TestClass.class).assertSuccessWithEmptyOutput();

    // Verify that it is still possible to inject a new implementation of Service after the
    // compilation.
    testForR8(parameters.getBackend())
        .addProgramClasses(ServiceImpl.class)
        .addClasspathClasses(Service.class)
        .addKeepAllClassesRule()
        .addApplyMapping(compileResult.getProguardMap())
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(
            injectServiceDeclarationInProgram(
                compileResult.writeToZip(),
                AppServices.SERVICE_DIRECTORY_NAME + serviceClassSubject.getFinalName(),
                ServiceImpl.class.getTypeName()))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private Path injectServiceDeclarationInProgram(Path program, String fileName, String contents)
      throws Exception {
    Path newProgram = temp.newFolder().toPath().resolve("program.jar");
    try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(newProgram))) {
      try (ZipInputStream inputStream = new ZipInputStream(Files.newInputStream(program))) {
        ZipEntry next = inputStream.getNextEntry();
        while (next != null) {
          outputStream.putNextEntry(new ZipEntry(next.getName()));
          outputStream.write(ByteStreams.toByteArray(inputStream));
          outputStream.closeEntry();
          next = inputStream.getNextEntry();
        }
      }
      outputStream.putNextEntry(new ZipEntry(fileName));
      outputStream.write(contents.getBytes());
      outputStream.closeEntry();
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

    @NeverInline
    public void greet() {
      System.out.println("Hello world!");
    }
  }
}
