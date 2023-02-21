// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static junit.framework.TestCase.assertNull;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.zip.ZipFile;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ServiceLoaderClassLoaderRewritingTest extends TestBase {

  private final TestParameters parameters;
  private final String EXPECTED_OUTPUT = StringUtils.lines("Hello World!");

  public interface Service {

    void print();
  }

  public static class ServiceImpl implements Service {

    @Override
    public void print() {
      System.out.println("Hello World!");
    }
  }

  public static class MainRunner {

    public static void main(String[] args) {
      run1();
    }

    @NeverInline
    public static void run1() {
      ClassLoader classLoader = Service.class.getClassLoader();
      checkNotNull(classLoader);
      for (Service x : ServiceLoader.load(Service.class, classLoader)) {
        x.print();
      }
    }

    @NeverInline
    public static void checkNotNull(ClassLoader classLoader) {
      if (classLoader == null) {
        throw new NullPointerException("ClassLoader should not be null");
      }
    }
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ServiceLoaderClassLoaderRewritingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRewritings() throws Exception {
    Path path = temp.newFile("out.zip").toPath();
    testForR8(parameters.getBackend())
        .addInnerClasses(ServiceLoaderClassLoaderRewritingTest.class)
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
        .inspect(this::verifyNoServiceLoaderLoads)
        .run(parameters.getRuntime(), MainRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);

    // Check that we have removed the service configuration from META-INF/services.
    ZipFile zip = new ZipFile(path.toFile());
    assertNull(zip.getEntry("META-INF/services/" + Service.class.getTypeName()));
  }

  private void verifyNoServiceLoaderLoads(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(MainRunner.class);
    assertTrue(classSubject.isPresent());
    classSubject.forAllMethods(
        method -> MatcherAssert.assertThat(method, not(invokesMethodWithName("load"))));
  }
}
