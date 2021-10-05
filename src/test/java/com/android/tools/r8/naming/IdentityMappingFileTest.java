// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IdentityMappingFileTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public IdentityMappingFileTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private void checkIdentityMappingContent(String mapping) {
    assertThat(mapping, containsString("# compiler: R8"));
    assertThat(mapping, containsString("# compiler_version: "));
    assertThat(mapping, containsString("# min_api: 1"));
    assertThat(mapping, containsString("# compiler_hash: "));
    assertThat(mapping, containsString("# common_typos_disable"));
    assertThat(mapping, containsString("# {\"id\":\"com.android.tools.r8.mapping\",\"version\":"));
    assertThat(mapping, containsString("# pg_map_id: "));
    assertThat(mapping, containsString("# pg_map_hash: SHA-256 "));
    // Check the mapping is the identity, e.g., only comments are defined.
    // Note, this could change if the mapping is ever changed to be complete, in which case the
    // mapping will have actual identity mappings.
    for (String line : StringUtils.splitLines(mapping)) {
      assertThat(line, startsWith("#"));
    }
  }

  @Test
  public void testTheTestBuilder() throws Exception {
    String mapping =
        testForR8(Backend.DEX)
            .addProgramClasses(Main.class)
            .setMinApi(AndroidApiLevel.B)
            .addKeepMainRule(Main.class)
            .compile()
            .getProguardMap();
    checkIdentityMappingContent(mapping);
  }

  @Test
  public void testFileOutput() throws Exception {
    Path mappingPath = temp.newFolder().toPath().resolve("mapping.map");
    R8.run(
        R8Command.builder()
            .addProgramFiles(ToolHelper.getClassFileForTestClass(Main.class))
            .addProguardConfiguration(
                ImmutableList.of(keepMainProguardConfiguration(Main.class)), Origin.unknown())
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setProguardMapOutputPath(mappingPath)
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .build());
    assertTrue(Files.exists(mappingPath));
    checkIdentityMappingContent(FileUtils.readTextFile(mappingPath, StandardCharsets.UTF_8));
  }

  @Test
  public void testStringConsumer() throws Exception {
    BooleanBox consumerWasCalled = new BooleanBox(false);
    StringBuilder mappingContent = new StringBuilder();
    R8.run(
        R8Command.builder()
            .addProgramFiles(ToolHelper.getClassFileForTestClass(Main.class))
            .addProguardConfiguration(
                ImmutableList.of(keepMainProguardConfiguration(Main.class)), Origin.unknown())
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setProguardMapConsumer(
                new StringConsumer() {
                  @Override
                  public void accept(String string, DiagnosticsHandler handler) {
                    mappingContent.append(string);
                  }

                  @Override
                  public void finished(DiagnosticsHandler handler) {
                    consumerWasCalled.set(true);
                  }
                })
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .build());
    assertTrue(consumerWasCalled.get());
    checkIdentityMappingContent(mappingContent.toString());
  }

  // Compiling this program with a keep main will result in an identity mapping for the residual
  // program. The (identity) mapping should still be created and emitted to the client.
  static class Main {

    public static void main(String[] args) {
      System.out.println("Hello world!");
    }
  }
}
