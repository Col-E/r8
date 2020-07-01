// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NonVirtualOverrideTest extends TestBase {

  private final TestParameters parameters;
  private final boolean enableClassInlining;
  private final boolean enableVerticalClassMerging;

  static class Dimensions {

    private final Backend backend;
    private final boolean enableClassInlining;
    private final boolean enableVerticalClassMerging;

    public Dimensions(
        Backend backend, boolean enableClassInlining, boolean enableVerticalClassMerging) {
      this.backend = backend;
      this.enableClassInlining = enableClassInlining;
      this.enableVerticalClassMerging = enableVerticalClassMerging;
    }

    @Override
    public int hashCode() {
      return Objects.hash(backend, enableClassInlining, enableVerticalClassMerging);
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Dimensions)) {
        return false;
      }
      Dimensions other = (Dimensions) o;
      return this.backend == other.backend
          && this.enableClassInlining == other.enableClassInlining
          && this.enableVerticalClassMerging == other.enableVerticalClassMerging;
    }
  }

  @Parameterized.Parameters(name = "Backend: {0}, class inlining: {1}, vertical class merging: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  public NonVirtualOverrideTest(
      TestParameters parameters, boolean enableClassInlining, boolean enableVerticalClassMerging) {
    this.parameters = parameters;
    this.enableClassInlining = enableClassInlining;
    this.enableVerticalClassMerging = enableVerticalClassMerging;
  }

  @ClassRule public static TemporaryFolder staticTemp = ToolHelper.getTemporaryFolderForTest();

  private static Function<Boolean, String> expectedResults =
      memoizeFunction(NonVirtualOverrideTest::getExpectedResult);

  private static Function<Dimensions, R8TestCompileResult> compilationResults =
      memoizeFunction(NonVirtualOverrideTest::compile);

  public static String getExpectedResult(boolean isOldVm) throws Exception {
    if (isOldVm) {
      return String.join(
          System.lineSeparator(),
          "In A.m1()",
          "In A.m2()",
          "In A.m3()",
          "In A.m4()",
          "In C.m1()",
          "In A.m2()",
          "In C.m3()",
          "In A.m4()",
          "In A.m1()", // With Java: Caught IllegalAccessError when calling B.m1()
          "In A.m3()", // With Java: Caught IncompatibleClassChangeError when calling B.m3()
          "In C.m1()", // With Java: Caught IllegalAccessError when calling B.m1()
          "In C.m3()", // With Java: Caught IncompatibleClassChangeError when calling B.m3()
          "In C.m1()",
          "In C.m3()",
          "");
    } else {
      Path referenceJar = staticTemp.getRoot().toPath().resolve("input.jar");
      ArchiveConsumer inputConsumer = new ArchiveConsumer(referenceJar);
      inputConsumer.accept(
          ByteDataView.of(NonVirtualOverrideTestClassDump.dump()),
          DescriptorUtils.javaTypeToDescriptor(NonVirtualOverrideTestClass.class.getName()),
          null);
      inputConsumer.accept(
          ByteDataView.of(ADump.dump()),
          DescriptorUtils.javaTypeToDescriptor(A.class.getName()),
          null);
      inputConsumer.accept(
          ByteDataView.of(BDump.dump()),
          DescriptorUtils.javaTypeToDescriptor(B.class.getName()),
          null);
      inputConsumer.accept(
          ByteDataView.of(CDump.dump()),
          DescriptorUtils.javaTypeToDescriptor(C.class.getName()),
          null);
      inputConsumer.finished(null);

      ProcessResult javaResult =
          ToolHelper.runJava(referenceJar, NonVirtualOverrideTestClass.class.getName());
      assertEquals(javaResult.exitCode, 0);
      return javaResult.stdout;
    }
  }

  public static boolean isDexVmBetween5_1_1and7_0_0(TestParameters parameters) {
    if (!parameters.isDexRuntime()) {
      return false;
    }
    Version version = parameters.getRuntime().asDex().getVm().getVersion();
    return version.isOlderThanOrEqual(Version.V7_0_0) && version.isAtLeast(Version.V5_1_1);
  }

  public static R8TestCompileResult compile(Dimensions dimensions) throws Exception {
    return testForR8(staticTemp, dimensions.backend)
        .addProgramClassFileData(
            NonVirtualOverrideTestClassDump.dump(), ADump.dump(), BDump.dump(), CDump.dump())
        .addKeepMainRule(NonVirtualOverrideTestClass.class)
        .addOptionsModification(
            options -> {
              options.enableClassInlining = dimensions.enableClassInlining;
              options.enableVerticalClassMerging = dimensions.enableVerticalClassMerging;
              options.testing.validInliningReasons = ImmutableSet.of(Reason.FORCE);
            })
        .setMinApi(AndroidApiLevel.B)
        .compile();
  }

  @Test
  public void test() throws Exception {
    // Run the program on Art after is has been compiled with R8.
    String referenceResult =
        expectedResults.apply(!enableClassInlining && isDexVmBetween5_1_1and7_0_0(parameters));
    R8TestCompileResult compiled =
        compilationResults.apply(
            new Dimensions(
                parameters.getBackend(), enableClassInlining, enableVerticalClassMerging));
    compiled
        .run(parameters.getRuntime(), NonVirtualOverrideTestClass.class)
        .assertSuccessWithOutput(referenceResult);

    // Check that B is present and that it doesn't contain the unused private method m2.
    if (!enableClassInlining && !enableVerticalClassMerging) {
      CodeInspector inspector = compiled.inspector();
      ClassSubject classSubject = inspector.clazz(B.class.getName());
      assertThat(classSubject, isPresentAndRenamed());
      assertThat(classSubject.method("void", "m1", ImmutableList.of()), isPresent());
      assertThat(classSubject.method("void", "m2", ImmutableList.of()), not(isPresent()));
      assertThat(classSubject.method("void", "m3", ImmutableList.of()), isPresent());
      assertThat(classSubject.method("void", "m4", ImmutableList.of()), not(isPresent()));
    }
  }
}
