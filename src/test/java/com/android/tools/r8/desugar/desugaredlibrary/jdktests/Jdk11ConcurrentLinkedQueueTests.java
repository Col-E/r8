// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.getTestNGMainRunner;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.testNGPath;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.testNGSupportProgramFiles;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_MINIMAL;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.DesugaredLibraryTestCompileResult;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Jdk11ConcurrentLinkedQueueTests extends DesugaredLibraryTestBase {

  private static final String WHITEBOX = "WhiteBox";

  private static Path[] JDK_11_CONCURRENT_LINKED_QUEUE_TEST_CLASS_FILES;

  // JDK 11 test constants.
  private static final Path JDK_11_CONCURRENT_LINKED_QUEUE_JAVA_DIR =
      Paths.get(ToolHelper.JDK_11_TESTS_DIR + "java/util/concurrent/ConcurrentLinkedQueue");
  private static final Path[] JDK_11_CONCURRENT_LINKED_QUEUE_JAVA_FILES =
      new Path[] {JDK_11_CONCURRENT_LINKED_QUEUE_JAVA_DIR.resolve(WHITEBOX + JAVA_EXTENSION)};

  @Parameter(0)
  public static TestParameters parameters;

  @Parameter(1)
  public static LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameter(2)
  public static CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        // TODO(134732760): Support Dalvik VMs, currently fails because libjavacrypto is required
        // and present only in ART runtimes.
        getTestParameters()
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .withApiLevel(AndroidApiLevel.N)
            .build(),
        ImmutableList.of(JDK11_MINIMAL, JDK11, JDK11_PATH),
        ImmutableSet.of(D8_L8DEBUG, D8_L8SHRINK));
  }

  @BeforeClass
  public static void compileConcurrentLinkedQueueClasses() throws Exception {
    // Build test constants.
    Path jdk11ConcurrentLinkedQueueTestsDir =
        getStaticTemp().newFolder("ConcurrentLinkedQueue").toPath();
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addClasspathFiles(testNGPath())
        .addSourceFiles(JDK_11_CONCURRENT_LINKED_QUEUE_JAVA_FILES)
        .setOutputPath(jdk11ConcurrentLinkedQueueTestsDir)
        .compile();
    JDK_11_CONCURRENT_LINKED_QUEUE_TEST_CLASS_FILES =
        new Path[] {jdk11ConcurrentLinkedQueueTestsDir.resolve(WHITEBOX + CLASS_EXTENSION)};
  }

  private void inspect(CodeInspector inspector) {
    // Right now we only expect one backport coming out of DesugarVarHandle - the backport with
    // forwarding of Unsafe.compareAndSwapObject.
    MethodReference firstBackportFromDesugarVarHandle =
        SyntheticItemsTestUtils.syntheticBackportWithForwardingMethod(
            Reference.classFromDescriptor("Lj$/com/android/tools/r8/DesugarVarHandle;"),
            0,
            Reference.method(
                Reference.classFromDescriptor("Lsun/misc/Unsafe;"),
                "compareAndSwapObject",
                ImmutableList.of(
                    Reference.typeFromDescriptor("Ljava/lang/Object;"),
                    Reference.LONG,
                    Reference.typeFromDescriptor("Ljava/lang/Object;"),
                    Reference.typeFromDescriptor("Ljava/lang/Object;")),
                Reference.BOOL));

    assertThat(
        inspector.clazz(
            DescriptorUtils.descriptorToJavaType(DexItemFactory.varHandleDescriptorString)),
        not(isPresent()));
    assertThat(
        inspector.clazz(
            DescriptorUtils.descriptorToJavaType(
                DexItemFactory.methodHandlesLookupDescriptorString)),
        not(isPresent()));
    assertThat(
        inspector.clazz(
            "j$." + DescriptorUtils.descriptorToJavaType(DexItemFactory.varHandleDescriptorString)),
        not(isPresent()));
    assertThat(
        inspector.clazz(
            "j$."
                + DescriptorUtils.descriptorToJavaType(
                    DexItemFactory.methodHandlesLookupDescriptorString)),
        not(isPresent()));
    assertThat(
        inspector.clazz(
            DescriptorUtils.descriptorToJavaType(DexItemFactory.desugarVarHandleDescriptorString)),
        not(isPresent()));
    assertThat(
        inspector.clazz(
            DescriptorUtils.descriptorToJavaType(
                DexItemFactory.desugarMethodHandlesLookupDescriptorString)),
        not(isPresent()));

    boolean usesNativeVarHandle =
        parameters.asDexRuntime().getVersion().isNewerThanOrEqual(Version.V13_0_0)
            && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.T);
    assertThat(
        inspector.clazz(
            "j$."
                + DescriptorUtils.descriptorToJavaType(
                    DexItemFactory.desugarVarHandleDescriptorString)),
        usesNativeVarHandle ? not(isPresent()) : isPresent());
    assertThat(
        inspector.clazz(firstBackportFromDesugarVarHandle.getHolderClass()),
        usesNativeVarHandle ? not(isPresent()) : isPresent());
    // Currently DesugarMethodHandlesLookup this is fully inlined by R8.
    assertThat(
        inspector.clazz(
            "j$."
                + DescriptorUtils.descriptorToJavaType(
                    DexItemFactory.desugarMethodHandlesLookupDescriptorString)),
        usesNativeVarHandle || compilationSpecification.isL8Shrink()
            ? not(isPresent())
            : isPresent());
  }

  void runTest(List<String> toRun) throws Exception {
    // Skip test with minimal configuration before API level 24, as the test use stream.
    assumeTrue(
        libraryDesugaringSpecification != JDK11_MINIMAL
            || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N));

    String verbosity = "2";
    DesugaredLibraryTestCompileResult<?> compileResult =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addProgramFiles(JDK_11_CONCURRENT_LINKED_QUEUE_TEST_CLASS_FILES)
            .addProgramFiles(testNGSupportProgramFiles())
            .addProgramClassFileData(getTestNGMainRunner())
            // The WhiteBox test is using VarHandle and MethodHandles.privateLookupIn to inspect the
            // internal state of the implementation, so desugaring is needed for the program here.
            .addOptionsModification(options -> options.enableVarHandleDesugaring = true)
            .compile()
            .inspectL8(this::inspect)
            .withArt6Plus64BitsLib();
    for (String success : toRun) {
      SingleTestRunResult<?> result =
          compileResult.run(parameters.getRuntime(), "TestNGMainRunner", verbosity, success);
      assertTrue(
          "Failure in " + success + "\n" + result,
          result.getStdOut().contains(StringUtils.lines(success + ": SUCCESS")));
    }
  }

  @Test
  @Ignore("b/267483394")
  public void testWhiteBox() throws Exception {
    runTest(ImmutableList.of("WhiteBox"));
  }
}
