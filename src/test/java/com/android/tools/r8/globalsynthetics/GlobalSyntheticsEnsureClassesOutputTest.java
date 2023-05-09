// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.globalsynthetics;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.GlobalSyntheticsGenerator;
import com.android.tools.r8.GlobalSyntheticsGeneratorCommand;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GlobalSyntheticsEnsureClassesOutputTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public GlobalSyntheticsEnsureClassesOutputTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testNumberOfClassesOnK() throws Exception {
    Path output = temp.newFolder().toPath().resolve("output.zip");
    GlobalSyntheticsGenerator.run(
        GlobalSyntheticsGeneratorCommand.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.API_DATABASE_LEVEL))
            .setMinApiLevel(AndroidApiLevel.K.getLevel())
            .setProgramConsumer(new DexIndexedConsumer.ArchiveConsumer(output))
            .build());
    CodeInspector inspector = new CodeInspector(output);
    assertEquals(1024, inspector.allClasses().size());
  }

  @Test
  public void testNumberOfClassesOnLatest() throws Exception {
    Path output = temp.newFolder().toPath().resolve("output.zip");
    GlobalSyntheticsGenerator.run(
        GlobalSyntheticsGeneratorCommand.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.API_DATABASE_LEVEL))
            .setMinApiLevel(AndroidApiLevel.LATEST.getLevel())
            .setProgramConsumer(new DexIndexedConsumer.ArchiveConsumer(output))
            .build());
    Set<String> expectedInOutput = new HashSet<>();
    // The output contains a RecordTag type that is mapped back to the original java.lang.Record by
    // our codeinspector.
    expectedInOutput.add("Ljava/lang/Record;");
    assertEquals(
        expectedInOutput,
        new CodeInspector(output)
            .allClasses().stream()
                .map(FoundClassSubject::getFinalDescriptor)
                .collect(Collectors.toSet()));
  }

  @Test
  public void testClassFileListOutput() throws Exception {
    Set<String> generatedGlobalSynthetics = Sets.newConcurrentHashSet();
    Path output = temp.newFolder().toPath().resolve("output.zip");
    runGlobalSyntheticsGenerator(
        GlobalSyntheticsGeneratorCommand.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.API_DATABASE_LEVEL))
            .setMinApiLevel(AndroidApiLevel.K.getLevel())
            .setProgramConsumer(new DexIndexedConsumer.ArchiveConsumer(output))
            .build(),
        options ->
            options.testing.globalSyntheticCreatedCallback =
                programClass -> generatedGlobalSynthetics.add(programClass.getTypeName()));
    Set<String> readGlobalSynthetics =
        new CodeInspector(output)
            .allClasses().stream().map(FoundClassSubject::getFinalName).collect(Collectors.toSet());
    assertEquals(generatedGlobalSynthetics, readGlobalSynthetics);
  }
}
