// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.StringConsumer.ForwardingConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanBox;
import com.google.common.collect.ImmutableList;
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

  @Test
  public void testTheTestBuilder() throws Exception {
    String mapping =
        testForR8(Backend.DEX)
            .addProgramClasses(Main.class)
            .setMinApi(AndroidApiLevel.B)
            .addKeepMainRule(Main.class)
            .compile()
            .getProguardMap();
    // TODO(b/202076520): The identity mapping content should at minimum include the header info.
    assertEquals("", mapping);
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
    // TODO(b/202076520): The identity mapping should we written to the file.
    assertFalse(Files.exists(mappingPath));
  }

  @Test
  public void testStringConsumer() throws Exception {
    BooleanBox consumerWasCalled = new BooleanBox(false);
    R8.run(
        R8Command.builder()
            .addProgramFiles(ToolHelper.getClassFileForTestClass(Main.class))
            .addProguardConfiguration(
                ImmutableList.of(keepMainProguardConfiguration(Main.class)), Origin.unknown())
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setProguardMapConsumer(
                new ForwardingConsumer(null) {
                  @Override
                  public void finished(DiagnosticsHandler handler) {
                    consumerWasCalled.set(true);
                  }
                })
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .build());
    // TODO(b/202076520): The identity mapping should at least still signal finish to the consumer.
    assertFalse(consumerWasCalled.get());
  }

  // Compiling this program with a keep main will result in an identity mapping for the residual
  // program. The (identity) mapping should still be created and emitted to the client.
  static class Main {

    public static void main(String[] args) {
      System.out.println("Hello world!");
    }
  }
}
