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
import com.android.tools.r8.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.keepanno.asm.KeepEdgeWriter;
import com.android.tools.r8.keepanno.asm.KeepEdgeWriter.AnnotationVisitorInterface;
import com.android.tools.r8.keepanno.ast.AnnotationConstants;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Edge;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.processor.KeepEdgeProcessor;
import com.android.tools.r8.keepanno.testsource.KeepClassAndDefaultConstructorSource;
import com.android.tools.r8.keepanno.testsource.KeepDependentFieldSource;
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
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.AnnotationVisitor;

@Ignore("b/248408342: These test break on r8lib builds because of src&test using ASM classes.")
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

  public static Path getKeepAnnoPath() {
    // TODO(b/270105162): This changes when new gradle setup is default.
    if (ToolHelper.isNewGradleSetup()) {
      return Paths.get(System.getenv("KEEP_ANNO_JAVAC_BUILD_DIR").split(File.pathSeparator)[0]);
    } else {
      return Paths.get(ToolHelper.BUILD_DIR, "classes", "java", "keepanno");
    }
  }

  private static List<Class<?>> getTestClasses() {
    return ImmutableList.of(
        KeepClassAndDefaultConstructorSource.class,
        KeepFieldSource.class,
        KeepDependentFieldSource.class);
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
            .addClasspathFiles(getKeepAnnoPath())
            .addClassNames(Collections.singletonList(typeName(source)))
            .addClasspathFiles(Paths.get(ToolHelper.BUILD_DIR, "classes", "java", "test"))
            .addClasspathFiles(ToolHelper.DEPS)
            .compile();

    CodeInspector inspector = new CodeInspector(out);
    checkSynthesizedKeepEdgeClass(inspector, out);
    // The source is added as a classpath name but not part of the compilation unit output.
    assertThat(inspector.clazz(source), isAbsent());

    testForJvm(parameters)
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
            .addClasspathFiles(getKeepAnnoPath())
            .addClasspathFiles(ToolHelper.DEPS)
            .compile();
    testForJvm(parameters)
        .addProgramFiles(out)
        .run(parameters.getRuntime(), source)
        .assertSuccessWithOutput(getExpected())
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(source), isPresent());
              checkSynthesizedKeepEdgeClass(inspector, out);
            });
  }

  public static List<byte[]> getInputClassesWithoutKeepAnnotations(Collection<Class<?>> classes)
      throws Exception {
    List<byte[]> transformed = new ArrayList<>(classes.size());
    for (Class<?> clazz : classes) {
      transformed.add(
          transformer(clazz).removeAnnotations(AnnotationConstants::isKeepAnnotation).transform());
    }
    return transformed;
  }

  /** Wrapper to bridge ASM visitors when using the r8lib compiled version of the keepanno lib. */
  private AnnotationVisitorInterface wrap(AnnotationVisitor visitor) {
    if (visitor == null) {
      return null;
    }
    return new AnnotationVisitorInterface() {
      @Override
      public int version() {
        return KeepEdgeReader.ASM_VERSION;
      }

      @Override
      public void visit(String name, Object value) {
        visitor.visit(name, value);
      }

      @Override
      public void visitEnum(String name, String descriptor, String value) {
        visitor.visitEnum(name, descriptor, value);
      }

      @Override
      public AnnotationVisitorInterface visitAnnotation(String name, String descriptor) {
        AnnotationVisitor v = visitor.visitAnnotation(name, descriptor);
        return v == visitor ? this : wrap(v);
      }

      @Override
      public AnnotationVisitorInterface visitArray(String name) {
        AnnotationVisitor v = visitor.visitArray(name);
        return v == visitor ? this : wrap(v);
      }

      @Override
      public void visitEnd() {
        visitor.visitEnd();
      }
    };
  }

  @Test
  public void testAsmReader() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    List<KeepEdge> expectedEdges = KeepSourceEdges.getExpectedEdges(source);
    ClassReference clazz = Reference.classFromClass(source);
    // Original bytes of the test class.
    byte[] original = ToolHelper.getClassAsBytes(source);
    // Strip out all the annotations to ensure they are actually added again.
    byte[] stripped =
        getInputClassesWithoutKeepAnnotations(Collections.singletonList(source)).get(0);
    // Manually add in the expected edges again.
    byte[] readded =
        transformer(stripped, clazz)
            .addClassTransformer(
                new ClassTransformer() {

                  @Override
                  public void visitEnd() {
                    for (KeepEdge edge : expectedEdges) {
                      KeepEdgeWriter.writeEdge(
                          edge, (desc, visible) -> wrap(super.visitAnnotation(desc, visible)));
                    }
                    super.visitEnd();
                  }
                })
            .transform();

    // Read the edges from each version.
    List<KeepDeclaration> originalEdges = KeepEdgeReader.readKeepEdges(original);
    List<KeepDeclaration> strippedEdges = KeepEdgeReader.readKeepEdges(stripped);
    List<KeepDeclaration> readdedEdges = KeepEdgeReader.readKeepEdges(readded);

    // The edges are compared to the "expected" ast to ensure we don't hide failures in reading or
    // writing.
    assertEquals(Collections.emptyList(), strippedEdges);
    assertEquals(expectedEdges, originalEdges);
    assertEquals(expectedEdges, readdedEdges);
  }

  @Test
  public void testExtractAndRun() throws Exception {
    testForR8(parameters.getBackend())
        .enableExperimentalKeepAnnotations()
        .addProgramClassesAndInnerClasses(source)
        .addKeepMainRule(source)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), source)
        .assertSuccessWithOutput(getExpected());
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
    List<KeepDeclaration> keepEdges = KeepEdgeReader.readKeepEdges(bytes);
    assertEquals(KeepSourceEdges.getExpectedEdges(source), keepEdges);
  }
}
