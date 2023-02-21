// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.rewrite.ServiceLoaderRewritingTest.MainRunner;
import com.android.tools.r8.rewrite.ServiceLoaderRewritingTest.Service;
import com.android.tools.r8.rewrite.ServiceLoaderRewritingTest.ServiceImpl;
import com.android.tools.r8.rewrite.ServiceLoaderRewritingTest.ServiceImpl2;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ServiceLoaderRewritingLineSeparatorTest extends TestBase {

  private final TestParameters parameters;
  private final Separator lineSeparator;

  private final String EXPECTED_OUTPUT =
      StringUtils.lines("Hello World!", "Hello World!", "Hello World!");

  enum Separator {
    WINDOWS,
    LINUX;

    public String getSeparator() {
      switch (this) {
        case WINDOWS:
          return "\r\n";
        case LINUX:
          return "\n";
        default:
          assert false;
      }
      return null;
    }
  }

  @Parameters(name = "{0}, separator: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), Separator.values());
  }

  public ServiceLoaderRewritingLineSeparatorTest(TestParameters parameters, Separator separator) {
    this.parameters = parameters;
    this.lineSeparator = separator;
  }

  @Test
  public void testRewritingWithMultipleWithLineSeparator()
      throws IOException, CompilationFailedException, ExecutionException {
    Path path = temp.newFile("out.zip").toPath();
    testForR8(parameters.getBackend())
        .addInnerClasses(ServiceLoaderRewritingTest.class)
        .addKeepMainRule(MainRunner.class)
        .setMinApi(parameters)
        .addDataEntryResources(
            DataEntryResource.fromBytes(
                StringUtils.join(
                        lineSeparator.getSeparator(),
                        ServiceImpl.class.getTypeName(),
                        ServiceImpl2.class.getTypeName())
                    .getBytes(),
                "META-INF/services/" + Service.class.getTypeName(),
                Origin.unknown()))
        .compile()
        .writeToZip(path)
        .run(parameters.getRuntime(), MainRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT + StringUtils.lines("Hello World 2!"))
        .inspect(
            inspector -> {
              // Check that we have actually rewritten the calls to ServiceLoader.load.
              assertEquals(0, getServiceLoaderLoads(inspector));
            });

    // Check that we have removed the service configuration from META-INF/services.
    ZipFile zip = new ZipFile(path.toFile());
    assertNull(zip.getEntry("META-INF/services"));
  }

  private static long getServiceLoaderLoads(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(MainRunner.class);
    assertTrue(classSubject.isPresent());
    return classSubject.allMethods().stream()
        .mapToLong(
            method ->
                method
                    .streamInstructions()
                    .filter(ServiceLoaderRewritingLineSeparatorTest::isServiceLoaderLoad)
                    .count())
        .sum();
  }

  private static boolean isServiceLoaderLoad(InstructionSubject instruction) {
    return instruction.isInvokeStatic()
        && instruction.getMethod().qualifiedName().contains("ServiceLoader.load");
  }
}
