// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.keeprules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.JavaCompilerTool;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.processor.KeepEdgeProcessor;
import com.android.tools.r8.keepanno.testsource.KeepClassAndDefaultConstructorSource;
import com.android.tools.r8.keepanno.testsource.KeepFieldSource;
import com.android.tools.r8.keepanno.testsource.KeepSourceEdges;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ZipUtils;
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

@RunWith(Parameterized.class)
public class KeepRuleExtractorTest extends TestBase {

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

  public KeepRuleExtractorTest(ParamWrapper wrapper) {
    this.parameters = wrapper.params;
    this.source = wrapper.clazz;
  }

  @Test
  public void testProcessor() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    Path out =
        JavaCompilerTool.create(parameters.getRuntime().asCf(), temp)
            .addAnnotationProcessors(typeName(KeepEdgeProcessor.class))
            .addClasspathFiles(KEEP_ANNO_PATH)
            .addClassNames(Collections.singletonList(typeName(source)))
            .addClasspathFiles(Paths.get(ToolHelper.BUILD_DIR, "classes", "java", "test"))
            .addClasspathFiles(ToolHelper.DEPS)
            .compile();

    String synthesizedEdgesClassName =
        KeepEdgeProcessor.getClassTypeNameForSynthesizedEdges(source.getTypeName());
    String entry =
        ZipUtils.zipEntryNameForClass(Reference.classFromTypeName(synthesizedEdgesClassName));
    byte[] bytes = ZipUtils.readSingleEntry(out, entry);
    Set<KeepEdge> keepEdges = KeepEdgeReader.readKeepEdges(bytes);
    assertEquals(KeepSourceEdges.getExpectedEdges(source), keepEdges);
  }

  @Test
  public void testExtract() throws Exception {
    List<String> rules = getKeepRulesForClass(source);
    testForR8(parameters.getBackend())
        .addClasspathFiles(KEEP_ANNO_PATH)
        .addProgramClassesAndInnerClasses(source)
        .addKeepRules(rules)
        .addKeepMainRule(source)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), source)
        .assertSuccessWithOutput(KeepSourceEdges.getExpected(source));
  }

  private List<String> getKeepRulesForClass(Class<?> clazz) throws IOException {
    Set<KeepEdge> keepEdges = KeepEdgeReader.readKeepEdges(ToolHelper.getClassAsBytes(clazz));
    List<String> rules = new ArrayList<>();
    KeepRuleExtractor extractor = new KeepRuleExtractor(rules::add);
    keepEdges.forEach(extractor::extract);
    return rules;
  }
}
