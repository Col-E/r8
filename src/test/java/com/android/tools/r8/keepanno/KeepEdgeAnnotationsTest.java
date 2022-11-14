// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.JavaCompilerTool;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.keepanno.annotations.KeepConstants.Edge;
import com.android.tools.r8.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.keepanno.asm.KeepEdgeWriter;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractor;
import com.android.tools.r8.keepanno.processor.KeepEdgeProcessor;
import com.android.tools.r8.keepanno.testsource.KeepClassAndDefaultConstructorSource;
import com.android.tools.r8.keepanno.testsource.KeepFieldSource;
import com.android.tools.r8.keepanno.testsource.KeepSourceEdges;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.AnnotationVisitor;

@RunWith(Parameterized.class)
public class KeepEdgeAnnotationsTest extends TestBase {

  private static class ParamWrapper {
    private final Class<?> clazz;
    private final TestParameters params;

    public ParamWrapper(Class<?> clazz, TestParameters params) {
      this.clazz = clazz;
      this.params = params;
    }

    @Override
    public String toString() {
      return clazz.getSimpleName() + ", " + params.toString();
    }
  }

  private static final Path KEEP_ANNO_PATH =
      Paths.get(ToolHelper.BUILD_DIR, "classes", "java", "keepanno");

  private static List<Class<?>> getTestClasses() {
    return ImmutableList.of(KeepClassAndDefaultConstructorSource.class, KeepFieldSource.class);
  }

  private final TestParameters parameters;
  private final Class<?> source;

  @Parameterized.Parameters(name = "{0}")
  public static List<ParamWrapper> data() {
    TestParametersCollection params =
        getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
    return getTestClasses().stream()
        .flatMap(c -> params.stream().map(p -> new ParamWrapper(c, p)))
        .collect(Collectors.toList());
  }

  public KeepEdgeAnnotationsTest(ParamWrapper wrapper) {
    this.parameters = wrapper.params;
    this.source = wrapper.clazz;
  }

  private String getExpected() {
    return KeepSourceEdges.getExpected(source);
  }

  @Test
  public void testProcessorClassfiles() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    Path out =
        JavaCompilerTool.create(parameters.getRuntime().asCf(), temp)
            .addAnnotationProcessors(typeName(KeepEdgeProcessor.class))
            .addClasspathFiles(KEEP_ANNO_PATH)
            .addClassNames(Collections.singletonList(typeName(source)))
            .addClasspathFiles(Paths.get(ToolHelper.BUILD_DIR, "classes", "java", "test"))
            .addClasspathFiles(ToolHelper.DEPS)
            .compile();

    CodeInspector inspector = new CodeInspector(out);
    checkSynthesizedKeepEdgeClass(inspector, out);
    // The source is added as a classpath name but not part of the compilation unit output.
    assertThat(inspector.clazz(source), isAbsent());

    testForJvm()
        .addProgramClassesAndInnerClasses(source)
        .addProgramFiles(out)
        .run(parameters.getRuntime(), source)
        .assertSuccessWithOutput(getExpected());
  }

  @Test
  public void testProcessorJavaSource() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    Path out =
        JavaCompilerTool.create(parameters.getRuntime().asCf(), temp)
            .addSourceFiles(ToolHelper.getSourceFileForTestClass(source))
            .addAnnotationProcessors(typeName(KeepEdgeProcessor.class))
            .addClasspathFiles(KEEP_ANNO_PATH)
            .addClasspathFiles(ToolHelper.DEPS)
            .compile();
    testForJvm()
        .addProgramFiles(out)
        .run(parameters.getRuntime(), source)
        .assertSuccessWithOutput(getExpected())
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(source), isPresent());
              checkSynthesizedKeepEdgeClass(inspector, out);
            });
  }

  @Test
  public void testAsmReader() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    Set<KeepEdge> expectedEdges = KeepSourceEdges.getExpectedEdges(source);
    ClassReference clazz = Reference.classFromClass(source);
    // Original bytes of the test class.
    byte[] original = ToolHelper.getClassAsBytes(source);
    // Strip out all the annotations to ensure they are actually added again.
    byte[] stripped =
        transformer(source)
            .addClassTransformer(
                new ClassTransformer() {
                  @Override
                  public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    // Ignore all input annotations.
                    return null;
                  }
                })
            .transform();
    // Manually add in the expected edges again.
    byte[] readded =
        transformer(stripped, clazz)
            .addClassTransformer(
                new ClassTransformer() {

                  @Override
                  public void visitEnd() {
                    for (KeepEdge edge : expectedEdges) {
                      KeepEdgeWriter.writeEdge(edge, super::visitAnnotation);
                    }
                    super.visitEnd();
                  }
                })
            .transform();

    // Read the edges from each version.
    Set<KeepEdge> originalEdges = KeepEdgeReader.readKeepEdges(original);
    Set<KeepEdge> strippedEdges = KeepEdgeReader.readKeepEdges(stripped);
    Set<KeepEdge> readdedEdges = KeepEdgeReader.readKeepEdges(readded);

    // The edges are compared to the "expected" ast to ensure we don't hide failures in reading or
    // writing.
    assertEquals(Collections.emptySet(), strippedEdges);
    assertEquals(expectedEdges, originalEdges);
    assertEquals(expectedEdges, readdedEdges);
  }

  @Test
  public void testExtractAndRun() throws Exception {
    List<String> rules = getKeepRulesForClass(source);
    testForR8(parameters.getBackend())
        .addClasspathFiles(KEEP_ANNO_PATH)
        .addProgramClassesAndInnerClasses(source)
        .addKeepRules(rules)
        .addKeepMainRule(source)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), source)
        .assertSuccessWithOutput(getExpected());
  }

  private List<String> getKeepRulesForClass(Class<?> clazz) throws IOException {
    Set<KeepEdge> keepEdges = KeepEdgeReader.readKeepEdges(ToolHelper.getClassAsBytes(clazz));
    List<String> rules = new ArrayList<>();
    KeepRuleExtractor extractor = new KeepRuleExtractor(rules::add);
    keepEdges.forEach(extractor::extract);
    return rules;
  }

  private void checkSynthesizedKeepEdgeClass(CodeInspector inspector, Path data)
      throws IOException {
    String synthesizedEdgesClassName =
        KeepEdgeProcessor.getClassTypeNameForSynthesizedEdges(source.getTypeName());
    ClassSubject synthesizedEdgesClass = inspector.clazz(synthesizedEdgesClassName);
    assertThat(synthesizedEdgesClass, isPresent());
    assertThat(synthesizedEdgesClass.annotation(Edge.CLASS.getTypeName()), isPresent());
    String entry = ZipUtils.zipEntryNameForClass(synthesizedEdgesClass.getFinalReference());
    byte[] bytes = ZipUtils.readSingleEntry(data, entry);
    Set<KeepEdge> keepEdges = KeepEdgeReader.readKeepEdges(bytes);
    assertEquals(KeepSourceEdges.getExpectedEdges(source), keepEdges);
  }
}
