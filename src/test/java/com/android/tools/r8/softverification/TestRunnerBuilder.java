// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.softverification;

import static com.android.tools.r8.utils.DescriptorUtils.getDescriptorFromClassBinaryName;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.Dex2OatTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.softverification.TestRunner.Measure;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.transformers.ClassFileTransformer.FieldPredicate;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * This runner produces a benchmark jar that can be used to investigate the time penalty that soft-
 * verification errors have on the runtime of an app. It will build a set of different tests:
 *
 * <pre>
 * - CheckCast
 * - InstanceOf
 * - TypeReference
 * - NewInstance
 * - StaticField
 * - StaticMethod
 * - InstanceField
 * - InstanceMethod
 * </pre>
 *
 * where for each test, there is a reference to either a missing class, existing class with missing
 * members or a full class with all definitions. Each test column can be run by invoking the
 * corresponding test runner:
 *
 * <p>TestRunner_MissingClass, TestRunner_MissingMember, TestRunner_FoundClass
 *
 * <p>To test in a setting with a studio project, modify ANDROID_STUDIO_LIB_PATH to point to an
 * android project. A reference android project can be found at:
 * /google/data/ro/teams/r8/deps/DexVerificationSample.tar.gz
 */
@RunWith(Parameterized.class)
public class TestRunnerBuilder extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntime(Version.V9_0_0)
        .withApiLevel(AndroidApiLevel.M)
        .build();
  }

  private static final Path ANDROID_STUDIO_LIB_PATH =
      Paths.get("path-to-project/app/libs/library.jar");

  private static final int COUNT = 400;

  private static final List<Class<?>> referenceClasses =
      ImmutableList.of(
          MissingClass.class, MissingMember.class, FoundClass.class, MissingSuperType.class);
  private static final List<Class<?>> testClasses =
      ImmutableList.of(
          TestCheckCast.class,
          TestInstanceOf.class,
          TestTypeReference.class,
          TestNewInstance.class,
          TestStaticField.class,
          TestStaticFieldWithOtherMembers.class,
          TestStaticMethod.class,
          TestInstanceField.class,
          TestInstanceFieldWithOtherMembers.class,
          TestInstanceMethod.class,
          TestHashCode.class,
          TestTryCatch.class);
  private static final Collection<String> testClassBinaryNames =
      ImmutableSet.copyOf(ListUtils.map(testClasses, TestBase::binaryName));

  private static void buildJar(Path path) throws Exception {
    ZipBuilder builder = ZipBuilder.builder(path);
    builder.addFilesRelative(
        ToolHelper.getClassPathForTests(), ToolHelper.getClassFileForTestClass(Measure.class));
    for (Class<?> clazz : referenceClasses) {
      String postFix = clazz.getSimpleName();
      int classCounter = 0;
      for (int i = 0; i < COUNT; i++) {
        for (Class<?> testClass : testClasses) {
          addClass(builder, testClass, clazz, postFix, i, classCounter++);
        }
      }
      if (clazz != MissingClass.class) {
        for (int i = 0; i < classCounter; i++) {
          String binaryName = binaryName(clazz) + "_" + i;
          ClassFileTransformer transformer =
              transformer(clazz).setClassDescriptor(getDescriptorFromClassBinaryName(binaryName));
          if (clazz == MissingMember.class) {
            transformer.removeMethods(MethodPredicate.all()).removeFields(FieldPredicate.all());
          }
          builder.addBytes(binaryName + ".class", transformer.transform());
        }
      }
      String runnerClass = binaryName(TestRunner.class) + "_" + postFix;
      builder.addBytes(
          runnerClass + ".class",
          transformer(TestRunner.class)
              .setClassDescriptor(getDescriptorFromClassBinaryName(runnerClass))
              .addMethodTransformer(
                  new MethodTransformer() {

                    @Override
                    public void visitMaxs(int maxStack, int maxLocals) {
                      super.visitMaxs(-1, maxLocals);
                    }

                    @Override
                    public void visitMethodInsn(
                        int opcode,
                        String owner,
                        String name,
                        String descriptor,
                        boolean isInterface) {
                      if (!testClassBinaryNames.contains(owner)) {
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        return;
                      }
                      for (int i = 0; i < COUNT; i++) {
                        super.visitMethodInsn(
                            opcode, owner + "_" + postFix + "_" + i, name, descriptor, isInterface);
                      }
                    }
                  })
              .transform());
    }
    builder.build();
  }

  private static void addClass(
      ZipBuilder builder,
      Class<?> clazz,
      Class<?> classReference,
      String postFix,
      int index,
      int referenceIndex)
      throws IOException {
    String binaryName = binaryName(clazz) + "_" + postFix + "_" + index;
    String referenceBinaryName = binaryName(classReference) + "_" + referenceIndex;
    builder.addBytes(
        binaryName + ".class",
        transformer(clazz)
            .setClassDescriptor(getDescriptorFromClassBinaryName(binaryName))
            .replaceClassDescriptorInMembers(
                descriptor(MissingClass.class),
                getDescriptorFromClassBinaryName(referenceBinaryName))
            .replaceClassDescriptorInMethodInstructions(
                descriptor(MissingClass.class),
                getDescriptorFromClassBinaryName(referenceBinaryName))
            .transformTryCatchBlock(
                "run",
                (start, end, handler, type, visitor) -> {
                  visitor.visitTryCatchBlock(start, end, handler, referenceBinaryName);
                })
            .transform());
  }

  @Test
  public void buildTest() throws Exception {
    Path benchmarkJar = temp.newFile("library.jar").toPath();
    buildJar(benchmarkJar);
    D8TestCompileResult compileResult =
        testForD8(parameters.getBackend())
            .setMinApi(parameters)
            .addProgramFiles(benchmarkJar)
            .compile();
    Dex2OatTestRunResult dex2OatTestRunResult = compileResult.runDex2Oat(parameters.getRuntime());
    dex2OatTestRunResult.assertSoftVerificationErrors();
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Building jar and placing in " + ANDROID_STUDIO_LIB_PATH);
    buildJar(ANDROID_STUDIO_LIB_PATH);
  }
}
