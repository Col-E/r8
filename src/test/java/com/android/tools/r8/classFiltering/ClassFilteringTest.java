// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classFiltering;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ClassFilteringTest extends TestBase {
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public ClassFilteringTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNoFiltering() throws Exception {
    List<Class<?>> input =
        ImmutableList.of(TestClass.class, TestClass.Remove.class, TestClass.Keep.class);

    // Run a test with normal providers, verify nothing is removed.
    testForD8()
        .addProgramClasses(input)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput("Keep Remove ");
  }

  @Test
  public void testFilterByChecksum() throws Exception {
    // Step #1: Build the inputs with checksum encoded.
    final Path output =
        testForD8()
            .setMinApi(parameters)
            .addProgramClasses(TestClass.class, TestClass.Remove.class, TestClass.Keep.class)
            .setIncludeClassesChecksum(true)
            .compile()
            .writeToZip();

    // Step #2: "Re-compile" the output dex with a filter that removes a class by checksum.

    // Remember the check some of Remove.class to search for in the ProgramResourceProvider
    final long crc = ToolHelper.getClassByteCrc(TestClass.Remove.class);

    testForD8()
        .addProgramFiles(output)
        .setMinApi(parameters)
        .apply(
            b ->
                b.getBuilder()
                    .setDexClassChecksumFilter(
                        (classDescriptor, checksum) -> !checksum.equals(crc)))
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput("Keep No Remove ");
  }

  @Test
  public void testDexMergingWithChecksum() throws Exception {
    // Step #1: Build the dex file seperately as an incremental build tools usually do.
    Set<Long> keepCrcs = Sets.newHashSet();
    Path[] dexInput = new Path[] {
        buildDex(TestClass.class,true, keepCrcs),
        buildDex(TestClass.Keep.class,true, keepCrcs),
        buildDex(TestClass.Remove.class, true, null)};

    // Step #2: Now use D8 as a merging tool.
    final Path merged =
        testForD8()
            .setMinApi(parameters)
            .addProgramFiles(dexInput)
            .setIncludeClassesChecksum(true)
            .compile()
            .writeToZip();

    // Try it with and without checksum. Both should yield identical result.
    testForD8()
        .addProgramFiles(merged)
        .setMinApi(parameters)
        .apply(
            b ->
                b.getBuilder()
                    .setDexClassChecksumFilter(
                        (classDescriptor, checksum) -> keepCrcs.contains(checksum)))
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput("Keep No Remove ");

    testForD8()
        .addProgramFiles(merged)
        .setMinApi(parameters)
        .apply(
            b ->
                b.getBuilder()
                    .setDexClassChecksumFilter(
                        (classDescriptor, checksum) -> keepCrcs.contains(checksum)))
        .setIncludeClassesChecksum(true)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput("Keep No Remove ");
  }

  @Test
  public void testDexMergingWithChecksumMissing() throws Exception {
    // Step #1: Build the dex file separately as an incremental build tools usually do but this time
    // make one of the dex file missing checksum information.
    Path[] dexInput =
        new Path[] {
          buildDex(TestClass.class, true, null),
          buildDex(TestClass.Keep.class, true, null),
          buildDex(TestClass.Remove.class, false, null)
        };

    // Step #2: Now use D8 as a merging tool and verify that the compilation fails as expected.
    try {
      testForD8()
          .setMinApi(parameters)
          .addProgramFiles(dexInput)
          .setIncludeClassesChecksum(true)
          .compile()
          .writeToZip();
      Assert.fail("Compilation should fail.");
    } catch (CompilationFailedException failure) {
      Assert.assertTrue(failure.getCause().getMessage(), failure.getCause().getMessage().contains(
          "has no checksum information while checksum encoding is requested"));
    }
  }

  @Test
  public void testDexFilePerClassFilteringOutput() throws Exception {
    // Step #1: Build the program pretending to be multidex files with DexPerClass.
    final Path outZip =
        testForD8()
            .setMinApi(parameters)
            .addProgramClasses(TestClass.class, TestClass.Keep.class, TestClass.Remove.class)
            .setIncludeClassesChecksum(true)
            .setOutputMode(OutputMode.DexFilePerClass)
            .compile()
            .writeToZip();

    // Step #2: Verify that the checksums are present and filtering is working as expected.
    final long crc = ToolHelper.getClassByteCrc(TestClass.Remove.class);
    testForD8()
        .addProgramFiles(outZip)
        .apply(b -> b.getBuilder().setDexClassChecksumFilter((desc, checksum) -> checksum != crc))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput("Keep No Remove ");
  }

  @Test
  public void testLambdaChecksum() throws Exception {
    final Path output =
        testForD8()
            .setMinApi(parameters)
            .addProgramClasses(TestDesugar.class, TestDesugar.Consumer.class)
            .setIncludeClassesChecksum(true)
            .compile()
            .writeToZip();

    List<String> classesWithChecksum = Lists.newArrayList();
    testForD8()
        .addProgramFiles(output)
        .setIncludeClassesChecksum(true)
        .apply(
            b ->
                b.getBuilder()
                    .setDexClassChecksumFilter(
                        (classDescriptor, checksum) -> {
                          classesWithChecksum.add(classDescriptor);
                          return true;
                        }))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestDesugar.class)
        .assertSuccessWithOutput("TestDesugar.consume");

    // Make sure the provider saw all 3 checksums (class, innerclass and the lambda class).
    Assert.assertEquals(3, classesWithChecksum.size());
  }

  /**
   * Builds a given class into a dex file specified by output.
   *
   * @param checksum If true, the dex file will be encoded with checkout information.
   * @param crcCollection If not null, add the CRC of the .class file to a collection after
   * compilation.
   * @return The CRC of the .class class file after compilation.
   */
  private Path buildDex(Class c, boolean checksum, Collection<Long> crcCollection)
      throws IOException, CompilationFailedException {
    if (crcCollection != null) {
      crcCollection.add(ToolHelper.getClassByteCrc(c));
    }
    return testForD8()
        .setMinApi(parameters)
        .addProgramClasses(c)
        .setIncludeClassesChecksum(checksum)
        .compile()
        .writeToZip();
  }
}
