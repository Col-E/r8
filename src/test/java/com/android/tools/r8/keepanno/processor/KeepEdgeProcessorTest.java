// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.processor;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.JavaCompilerTool;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.keepanno.annotations.KeepConstants.Edge;
import com.android.tools.r8.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.testsource.KeepClassAndDefaultConstructorSource;
import com.android.tools.r8.keepanno.testsource.KeepSourceEdges;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepEdgeProcessorTest extends TestBase {

  private static final Path KEEP_ANNO_PATH =
      Paths.get(ToolHelper.BUILD_DIR, "classes", "java", "keepanno");
  private static final Class<?> SOURCE = KeepClassAndDefaultConstructorSource.class;
  private static final String EXPECTED = KeepSourceEdges.getExpected(SOURCE);

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultCfRuntime().build();
  }

  public KeepEdgeProcessorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testClassfile() throws Exception {
    Path out =
        JavaCompilerTool.create(parameters.getRuntime().asCf(), temp)
            .addAnnotationProcessors(typeName(KeepEdgeProcessor.class))
            .addClasspathFiles(KEEP_ANNO_PATH)
            .addClassNames(Collections.singletonList(typeName(SOURCE)))
            .addClasspathFiles(Paths.get(ToolHelper.BUILD_DIR, "classes", "java", "test"))
            .addClasspathFiles(ToolHelper.DEPS)
            .compile();

    CodeInspector inspector = new CodeInspector(out);
    checkSynthesizedKeepEdgeClass(inspector, out);
    // The source is added as a classpath name but not part of the compilation unit output.
    assertThat(inspector.clazz(SOURCE), isAbsent());

    testForJvm()
        .addProgramClasses(SOURCE, KeepClassAndDefaultConstructorSource.A.class)
        .addProgramFiles(out)
        .run(parameters.getRuntime(), SOURCE)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testJavaSource() throws Exception {
    Path out =
        JavaCompilerTool.create(parameters.getRuntime().asCf(), temp)
            .addSourceFiles(ToolHelper.getSourceFileForTestClass(SOURCE))
            .addAnnotationProcessors(typeName(KeepEdgeProcessor.class))
            .addClasspathFiles(KEEP_ANNO_PATH)
            .addClasspathFiles(ToolHelper.DEPS)
            .compile();

    testForJvm()
        .addProgramFiles(out)
        .run(parameters.getRuntime(), SOURCE)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(SOURCE), isPresent());
              checkSynthesizedKeepEdgeClass(inspector, out);
            });
  }

  private void checkSynthesizedKeepEdgeClass(CodeInspector inspector, Path data)
      throws IOException {
    String synthesizedEdgesClassName =
        KeepEdgeProcessor.getClassTypeNameForSynthesizedEdges(SOURCE.getTypeName());
    ClassSubject synthesizedEdgesClass = inspector.clazz(synthesizedEdgesClassName);
    assertThat(synthesizedEdgesClass, isPresent());
    assertThat(synthesizedEdgesClass.annotation(Edge.CLASS.getTypeName()), isPresent());
    String entry = ZipUtils.zipEntryNameForClass(synthesizedEdgesClass.getFinalReference());
    byte[] bytes = ZipUtils.readSingleEntry(data, entry);
    Set<KeepEdge> keepEdges = KeepEdgeReader.readKeepEdges(bytes);
    assertEquals(KeepSourceEdges.getExpectedEdges(SOURCE), keepEdges);
  }
}
