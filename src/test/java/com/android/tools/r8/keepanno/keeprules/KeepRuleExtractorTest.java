// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.keeprules;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.testsource.KeepClassAndDefaultConstructorSource;
import com.android.tools.r8.keepanno.testsource.KeepSourceEdges;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepRuleExtractorTest extends TestBase {

  private static final Class<?> SOURCE = KeepClassAndDefaultConstructorSource.class;
  private static final String EXPECTED = KeepSourceEdges.getExpected(SOURCE);
  private static final Path KEEP_ANNO_PATH =
      Paths.get(ToolHelper.BUILD_DIR, "classes", "java", "keepanno");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public KeepRuleExtractorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    List<String> rules = getKeepRulesForClass(SOURCE);
    testForR8(parameters.getBackend())
        .addClasspathFiles(KEEP_ANNO_PATH)
        .addProgramClassesAndInnerClasses(SOURCE)
        .addKeepRules(rules)
        .addKeepMainRule(SOURCE)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), SOURCE)
        .assertSuccessWithOutput(EXPECTED);
  }

  private List<String> getKeepRulesForClass(Class<?> clazz) throws IOException {
    Set<KeepEdge> keepEdges = KeepEdgeReader.readKeepEdges(ToolHelper.getClassAsBytes(clazz));
    List<String> rules = new ArrayList<>();
    KeepRuleExtractor extractor = new KeepRuleExtractor(rules::add);
    keepEdges.forEach(extractor::extract);
    return rules;
  }
}
