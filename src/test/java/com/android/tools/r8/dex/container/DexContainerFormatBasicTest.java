// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.container;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.maindexlist.MainDexListTests;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class DexContainerFormatBasicTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static Path inputA;
  private static Path inputB;

  @BeforeClass
  public static void generateTestApplications() throws Throwable {
    // Build two applications in different packages both with required multidex due to number
    // of methods.
    inputA = getStaticTemp().getRoot().toPath().resolve("application_a.jar");
    inputB = getStaticTemp().getRoot().toPath().resolve("application_b.jar");

    generateApplication(inputA, "a", 10);
    generateApplication(inputB, "b", 10);
  }

  @Test
  public void testNonContainerD8() throws Exception {
    Path outputA =
        testForD8(Backend.DEX)
            .addProgramFiles(inputA)
            .setMinApi(AndroidApiLevel.L)
            .compile()
            .writeToZip();
    assertEquals(2, unzipContent(outputA).size());

    Path outputB =
        testForD8(Backend.DEX)
            .addProgramFiles(inputB)
            .setMinApi(AndroidApiLevel.L)
            .compile()
            .writeToZip();
    assertEquals(2, unzipContent(outputB).size());

    Path outputMerged =
        testForD8(Backend.DEX)
            .addProgramFiles(outputA, outputB)
            .setMinApi(AndroidApiLevel.L)
            .compile()
            .writeToZip();
    assertEquals(4, unzipContent(outputMerged).size());
  }

  @Test
  public void testD8Experiment() throws Exception {
    Path outputFromDexing =
        testForD8(Backend.DEX)
            .addProgramFiles(inputA)
            .setMinApi(AndroidApiLevel.L)
            .addOptionsModification(
                options -> options.getTestingOptions().dexContainerExperiment = true)
            .compile()
            .writeToZip();
    List<byte[]> dexFromDexing = unzipContent(outputFromDexing);
    assertEquals(1, dexFromDexing.size());
  }

  @Test
  public void testD8Experiment2() throws Exception {
    Path outputA =
        testForD8(Backend.DEX)
            .addProgramFiles(inputA)
            .setMinApi(AndroidApiLevel.L)
            .addOptionsModification(
                options -> options.getTestingOptions().dexContainerExperiment = true)
            .compile()
            .writeToZip();
    assertEquals(1, unzipContent(outputA).size());

    Path outputB =
        testForD8(Backend.DEX)
            .addProgramFiles(inputB)
            .setMinApi(AndroidApiLevel.L)
            .addOptionsModification(
                options -> options.getTestingOptions().dexContainerExperiment = true)
            .compile()
            .writeToZip();
    assertEquals(1, unzipContent(outputB).size());
  }

  private List<byte[]> unzipContent(Path zip) throws IOException {
    List<byte[]> result = new ArrayList<>();
    ZipUtils.iter(zip, (entry, inputStream) -> result.add(ByteStreams.toByteArray(inputStream)));
    return result;
  }

  private static void generateApplication(Path applicationJar, String rootPackage, int methodCount)
      throws Throwable {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (int i = 0; i < 10000; ++i) {
      String pkg = rootPackage + "." + (i % 2 == 0 ? "a" : "b");
      String className = "Class" + i;
      builder.add(pkg + "." + className);
    }
    List<String> classes = builder.build();

    generateApplication(applicationJar, classes, methodCount);
  }

  private static void generateApplication(Path output, List<String> classes, int methodCount)
      throws IOException {
    ArchiveConsumer consumer = new ArchiveConsumer(output);
    for (String typename : classes) {
      String descriptor = DescriptorUtils.javaTypeToDescriptor(typename);
      byte[] bytes =
          transformer(MainDexListTests.ClassStub.class)
              .setClassDescriptor(descriptor)
              .addClassTransformer(
                  new ClassTransformer() {
                    @Override
                    public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions) {
                      // This strips <init>() too.
                      if (name.equals("methodStub")) {
                        for (int i = 0; i < methodCount; i++) {
                          MethodVisitor mv =
                              super.visitMethod(
                                  access, "method" + i, descriptor, signature, exceptions);
                          mv.visitCode();
                          mv.visitInsn(Opcodes.RETURN);
                          mv.visitMaxs(0, 0);
                          mv.visitEnd();
                        }
                      }
                      return null;
                    }
                  })
              .transform();
      consumer.accept(ByteDataView.of(bytes), descriptor, null);
    }
    consumer.finished(null);
  }

  // Simple stub/template for generating the input classes.
  public static class ClassStub {
    public static void methodStub() {}
  }
}
