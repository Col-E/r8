// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipFile;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ServiceLoaderMultipleCallsSameMethodTest extends TestBase {

  private final TestParameters parameters;
  private final String EXPECTED_OUTPUT = StringUtils.lines("Hello World!", "Hello World!");

  public interface Service {

    void print();
  }

  public static class ServiceImpl implements Service {

    @Override
    public void print() {
      System.out.println("Hello World!");
    }
  }

  public static class ServiceImpl2 implements Service {

    @Override
    public void print() {
      System.out.println("Hello World 2!");
    }
  }

  public static class MainRunner {

    public static void main(String[] args) {
      run1();
    }

    @NeverInline
    public static void run1() {
      ClassLoader classLoader = Service.class.getClassLoader();
      for (Service x : ServiceLoader.load(Service.class, classLoader)) {
        x.print();
      }
      for (Service x : ServiceLoader.load(Service.class, classLoader)) {
        x.print();
      }
    }
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ServiceLoaderMultipleCallsSameMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRewritings() throws IOException, CompilationFailedException, ExecutionException {
    Path path = temp.newFile("out.zip").toPath();
    testForR8(parameters.getBackend())
        .addInnerClasses(ServiceLoaderMultipleCallsSameMethodTest.class)
        .addKeepMainRule(MainRunner.class)
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .addDataEntryResources(
            DataEntryResource.fromBytes(
                StringUtils.lines(ServiceImpl.class.getTypeName()).getBytes(),
                "META-INF/services/" + Service.class.getTypeName(),
                Origin.unknown()))
        .compile()
        .writeToZip(path)
        .run(parameters.getRuntime(), MainRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        // Check that we have actually rewritten the calls to ServiceLoader.load.
        .inspect(this::verifyNoServiceLoaderLoads)
        .inspect(this::verifyNoClassLoaders)
        .inspect(
            inspector -> {
              // Check the synthesize service loader method is a single shared method.
              // Due to minification we just check there is only a single synthetic class with a
              // single static method.
              boolean found = false;
              for (FoundClassSubject clazz : inspector.allClasses()) {
                if (clazz.isSynthetic()) {
                  assertFalse(found);
                  found = true;
                  assertEquals(1, clazz.allMethods().size());
                  clazz.forAllMethods(m -> assertTrue(m.isStatic()));
                }
              }
            });

    // Check that we have removed the service configuration from META-INF/services.
    ZipFile zip = new ZipFile(path.toFile());
    assertNull(zip.getEntry("META-INF/services/" + Service.class.getTypeName()));
  }

  private void verifyNoServiceLoaderLoads(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(MainRunner.class);
    Assert.assertTrue(classSubject.isPresent());
    classSubject.forAllMethods(
        method -> MatcherAssert.assertThat(method, not(invokesMethodWithName("load"))));
  }

  private void verifyNoClassLoaders(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(MainRunner.class);
    Assert.assertTrue(classSubject.isPresent());
    classSubject.forAllMethods(
        method -> MatcherAssert.assertThat(method, not(invokesMethodWithName("getClassLoader"))));
  }
}
