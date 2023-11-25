// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.mappingcompose;

import static com.android.tools.r8.mappingcompose.ComposeTestHelpers.doubleToSingleQuote;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.PartitionMapConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.retrace.MappingPartition;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.retrace.PartitionMappingSupplier;
import com.android.tools.r8.retrace.PartitionedToProguardMappingConverter;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.FileUtils;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a regression test for b/297927590. */
@RunWith(Parameterized.class)
public class ComposeUnknownJsonD8Test extends TestBase {

  @Parameter() public TestParameters parameters;

  private static final String CUSTOM_DATA = "# {'id':'custom_info','base64/deflate':''}";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withMinimumApiLevel().build();
  }

  private void testD8(ThrowableConsumer<D8TestBuilder> testBuilder) throws Exception {
    Path inputMap = temp.newFolder().toPath().resolve("input.map");
    FileUtils.writeTextFile(
        inputMap,
        CUSTOM_DATA,
        "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
        "X -> X:");
    testForD8(parameters.getBackend())
        .addProgramClasses(A.class)
        .setMinApi(parameters)
        .addOptionsModification(
            options ->
                assertTrue(options.mappingComposeOptions().enableExperimentalMappingComposition))
        .apply(b -> b.getBuilder().setProguardMapInputFile(inputMap))
        .apply(testBuilder)
        .allowStdoutMessages()
        .collectStdout()
        .compile()
        .assertStdoutThatMatches(containsString("Info: Could not find a handler for custom_info"));
  }

  @Test
  public void testD8CompositionMappingFile() throws Exception {
    StringBuilder mappingComposed = new StringBuilder();
    testD8(
        builder ->
            builder
                .getBuilder()
                .setProguardMapConsumer((string, handler) -> mappingComposed.append(string)));
    assertThat(doubleToSingleQuote(mappingComposed.toString()), containsString(CUSTOM_DATA));
  }

  @Test
  public void testD8CompositionPartition() throws Exception {
    Box<byte[]> metadataBytes = new Box<>();
    testD8(
        builder ->
            builder
                .getBuilder()
                .setPartitionMapConsumer(
                    new PartitionMapConsumer() {
                      @Override
                      public void acceptMappingPartition(MappingPartition mappingPartition) {}

                      @Override
                      public void acceptMappingPartitionMetadata(
                          MappingPartitionMetadata mappingPartitionMetadata) {
                        metadataBytes.set(mappingPartitionMetadata.getBytes());
                      }
                    }));
    StringBuilder mappingComposed = new StringBuilder();
    PartitionedToProguardMappingConverter.builder()
        .setPartitionMappingSupplier(
            PartitionMappingSupplier.builder()
                .setMetadata(metadataBytes.get())
                .setMappingPartitionFromKeySupplier(key -> new byte[0])
                .build())
        .setConsumer((string, handler) -> mappingComposed.append(string))
        .build()
        .run();
    assertThat(doubleToSingleQuote(mappingComposed.toString()), containsString(CUSTOM_DATA));
  }

  public static class A {}
}
