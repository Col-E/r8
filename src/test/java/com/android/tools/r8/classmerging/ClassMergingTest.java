// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationException;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.shaking.ProguardRuleParserException;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OutputMode;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ClassMergingTest {

  private static final Path EXAMPLE_JAR = Paths.get(ToolHelper.EXAMPLES_BUILD_DIR)
      .resolve("classmerging.jar");
  private static final Path EXAMPLE_KEEP = Paths.get(ToolHelper.EXAMPLES_DIR)
      .resolve("classmerging").resolve("keep-rules.txt");
  private static final Path DONT_OPTIMIZE = Paths.get(ToolHelper.EXAMPLES_DIR)
      .resolve("classmerging").resolve("keep-rules-dontoptimize.txt");

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  private void configure(InternalOptions options) {
    options.skipClassMerging = false;
  }

  private void runR8(Path proguardConfig, Consumer<InternalOptions> optionsConsumer)
      throws IOException, ProguardRuleParserException, ExecutionException, CompilationException,
      CompilationFailedException {
    ToolHelper.runR8(
        R8Command.builder()
            .setOutput(Paths.get(temp.getRoot().getCanonicalPath()), OutputMode.DexIndexed)
            .addProgramFiles(EXAMPLE_JAR)
            .addProguardConfigurationFiles(proguardConfig)
            .setDisableMinification(true)
            .build(),
        optionsConsumer);
    inspector = new DexInspector(
        Paths.get(temp.getRoot().getCanonicalPath()).resolve("classes.dex"));
  }

  private DexInspector inspector;

  private final List<String> CAN_BE_MERGED = ImmutableList.of(
      "classmerging.GenericInterface",
      "classmerging.GenericAbstractClass",
      "classmerging.Outer$SuperClass",
      "classmerging.SuperClass"
  );

  @Test
  public void testClassesHaveBeenMerged() throws Exception {
    runR8(EXAMPLE_KEEP, this::configure);
    // GenericInterface should be merged into GenericInterfaceImpl.
    for (String candidate : CAN_BE_MERGED) {
      assertFalse(inspector.clazz(candidate).isPresent());
    }
    assertTrue(inspector.clazz("classmerging.GenericInterfaceImpl").isPresent());
    assertTrue(inspector.clazz("classmerging.GenericInterfaceImpl").isPresent());
    assertTrue(inspector.clazz("classmerging.Outer$SubClass").isPresent());
    assertTrue(inspector.clazz("classmerging.SubClass").isPresent());
  }

  @Test
  public void testClassesShouldNotMerged() throws Exception {
    runR8(DONT_OPTIMIZE, null);
    for (String candidate : CAN_BE_MERGED) {
      assertTrue(inspector.clazz(candidate).isPresent());
    }
  }

  @Test
  public void testConflictWasDetected() throws Exception {
    runR8(EXAMPLE_KEEP, this::configure);
    assertTrue(inspector.clazz("classmerging.ConflictingInterface").isPresent());
    assertTrue(inspector.clazz("classmerging.ConflictingInterfaceImpl").isPresent());
  }

  @Test
  public void testSuperCallWasDetected() throws Exception {
    runR8(EXAMPLE_KEEP, this::configure);
    assertTrue(inspector.clazz("classmerging.SuperClassWithReferencedMethod").isPresent());
    assertTrue(inspector.clazz("classmerging.SubClassThatReferencesSuperMethod").isPresent());
  }

}
