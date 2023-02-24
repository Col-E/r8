// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.DirectoryConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ProguardDirectoryInputsTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public ProguardDirectoryInputsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test(expected = CompilationFailedException.class)
  public void testInJars() throws Exception {
    test("injars");
  }

  @Test(expected = CompilationFailedException.class)
  public void testLibraryJars() throws Exception {
    test("libraryjars");
  }

  private void test(String option) throws Exception {
    Path directory = temp.newFolder().toPath();
    {
      DirectoryConsumer consumer = new DirectoryConsumer(directory);
      consumer.accept(
          ByteDataView.of(ToolHelper.getClassAsBytes(OtherClass.class)),
          DescriptorUtils.javaTypeToDescriptor(OtherClass.class.getTypeName()),
          null);
      consumer.finished(null);
    }
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addProgramClasses(TestClass.class)
          .addClasspath(directory)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED);
    }

    Origin origin =
        new Origin(Origin.root()) {
          @Override
          public String part() {
            return "pg-test-input";
          }
        };

    String prefix = "-" + option + " ";
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .apply(
            b -> {
              b.getBuilder()
                  .addProguardConfiguration(
                      Collections.singletonList(prefix + directory.toAbsolutePath()), origin);
            })
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertOnlyErrors();
              diagnostics.assertErrorsCount(1);
              Diagnostic diagnostic = diagnostics.getErrors().get(0);
              assertThat(
                  diagnostic.getDiagnosticMessage(), containsString("Unexpected input type"));
              assertEquals(origin, diagnostic.getOrigin());
              TextPosition position = (TextPosition) diagnostic.getPosition();
              assertEquals(1, position.getLine());
              assertEquals(prefix.length() + 1, position.getColumn());
            });
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }

  static class OtherClass {

    public void foo() {
      System.out.println("foo");
    }
  }
}
