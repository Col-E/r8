// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_MINIMAL;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

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
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Jdk11Jsr166Tests extends DesugaredLibraryTestBase {

  private static Path jsr166Suite;
  private static Path jsr166SuitePreN;

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
        //   and present only in ART runtimes.
        getTestParameters()
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .withApiLevel(AndroidApiLevel.N)
            .build(),
        ImmutableList.of(JDK11_MINIMAL, JDK11, JDK11_PATH),
        ImmutableSet.of(D8_L8DEBUG, D8_L8SHRINK));
  }

  @BeforeClass
  public static void compileJsr166Suite() throws Exception {
    // Build the JSR166 test suite.
    Path jsr166SuiteClasses = getStaticTemp().newFolder("jsr166SuiteClasses").toPath();

    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addClasspathFiles(Paths.get(ToolHelper.THIRD_PARTY_DIR + "junit/junit-4.13-beta-2.jar"))
        .addSourceFiles(
            Files.walk(
                    Paths.get(
                        ToolHelper.THIRD_PARTY_DIR
                            + "openjdk/jdk-11-test/java/util/concurrent/tck"))
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .collect(Collectors.toList()))
        .setOutputPath(jsr166SuiteClasses)
        .compile();
    Path jars = jsr166Suite = getStaticTemp().newFolder("jars").toPath();
    jsr166Suite = jars.resolve("jsr166Suite.jar");
    ZipUtils.zip(jsr166Suite, jsr166SuiteClasses);
    // Transform the test suite to be able to run on pre N devices.
    jsr166SuitePreN = jars.resolve("jsr166SuitePreN.jar");
    makeTransformedJar(jsr166Suite, jsr166SuitePreN);
  }

  public static void makeTransformedJar(Path in, Path out) throws IOException {
    ZipUtils.map(
        in,
        out,
        (entry, bytes) ->
            entry.getName().equals("ConcurrentLinkedDequeTest.class")
                ? transformConcurrentLinkedDequeTestForPreN(bytes)
                : bytes);
  }

  public static byte[] transformConcurrentLinkedDequeTestForPreN(
      byte[] concurrentLinkedDequeTestClassBytes) {
    // Remove methods using java.util.concurrent.CompletableFuture and
    // java.util.concurrent.atomic.LongAdder introduced as API level 24.
    return transformer(
            concurrentLinkedDequeTestClassBytes,
            Reference.classFromDescriptor("LConcurrentLinkedDequeTest;"))
        .removeMethods(
            (access, name, descriptor, signature, exceptions) ->
                name.contains("testBug8188900")
                    || name.contains("testBug8188900_reverse")
                    || name.contains("testBug8189387"))
        .transform();
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

  void runTest(List<TestInfo> toRun) throws Exception {
    DesugaredLibraryTestCompileResult<?> compileResult =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addProgramFiles(Paths.get("third_party/junit/junit-4.13-beta-2.jar"))
            .addProgramFiles(
                parameters.getDexRuntimeVersion().isOlderThan(Version.V7_0_0)
                    ? jsr166SuitePreN
                    : jsr166Suite)
            .compile()
            .inspectL8(this::inspect)
            .withArt6Plus64BitsLib();
    for (TestInfo testInfo : toRun) {
      SingleTestRunResult<?> result =
          compileResult.run(parameters.getRuntime(), testInfo.getName());
      assertTrue(
          "Failure in " + testInfo.getName() + "\n" + result,
          result.getStdOut().startsWith(testInfo.getStartsWith()));
    }
  }

  private static class TestInfo {
    private final String name;
    private final String startsWith;

    public TestInfo(String name, String startsWith) {
      this.name = name;
      this.startsWith = startsWith;
    }

    public String getName() {
      return name;
    }

    public String getStartsWith() {
      return startsWith;
    }
  }

  @Test
  @Ignore("b/267483394")
  public void test() throws Exception {
    runTest(
        ImmutableList.of(
            new TestInfo("ConcurrentLinkedQueueTest", "OK (38 tests)"),
            new TestInfo(
                "ConcurrentLinkedDequeTest",
                parameters.getDexRuntimeVersion().isOlderThan(Version.V7_0_0)
                    ? "OK (62 tests)"
                    : "OK (65 tests)")));
  }
}
