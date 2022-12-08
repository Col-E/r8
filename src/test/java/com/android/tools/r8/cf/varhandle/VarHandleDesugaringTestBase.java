// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.varhandle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.examples.jdk9.VarHandle;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class VarHandleDesugaringTestBase extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        // Running on 4.0.4 and 4.4.4 needs to be checked. Output seems correct, but at the
        // same time there are VFY errors on stderr.
        .withDexRuntimesStartingFromExcluding(Version.V4_4_4)
        .withAllApiLevels()
        .build();
  }

  protected abstract String getMainClass();

  protected String getKeepRules() {
    return "";
  }

  protected abstract List<String> getJarEntries();

  protected abstract String getExpectedOutputForReferenceImplementation();

  protected String getExpectedOutputForArtImplementation() {
    return getExpectedOutputForReferenceImplementation();
  }

  // TODO(b/247076137): Remove this when all tests can run with desugaring.
  protected boolean getTestWithDesugaring() {
    return false;
  }

  @Test
  public void testReference() throws Throwable {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramFiles(VarHandle.jar())
        .run(parameters.getRuntime(), getMainClass())
        .assertSuccessWithOutput(getExpectedOutputForReferenceImplementation());
  }

  private List<byte[]> getProgramClassFileData() {
    return getJarEntries().stream()
        .map(
            entry -> {
              try {
                return ZipUtils.readSingleEntry(VarHandle.jar(), entry);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })
        .collect(Collectors.toList());
  }

  private boolean willDesugarVarHandle() {
    return parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(AndroidApiLevel.T);
  }

  private void inspect(CodeInspector inspector) {
    IntBox unsafeCompareAndSwapInt = new IntBox();
    IntBox unsafeCompareAndSwapLong = new IntBox();
    IntBox unsafeCompareAndSwapObject = new IntBox();
    DexString compareAndSwapInt = inspector.getFactory().createString("compareAndSwapInt");
    DexString compareAndSwapLong = inspector.getFactory().createString("compareAndSwapLong");
    DexString compareAndSwapObject = inspector.getFactory().createString("compareAndSwapObject");
    // Right now we only expect one backport coming out of DesugarVarHandle - the backport with
    // forwarding of Unsafe.compareAndSwapObject.
    MethodReference firstBackportFromDesugarVarHandle =
        SyntheticItemsTestUtils.syntheticBackportWithForwardingMethod(
            Reference.classFromDescriptor(DexItemFactory.desugarVarHandleDescriptorString),
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
    inspector.forAllClasses(
        clazz -> {
          clazz.forAllMethods(
              method -> {
                method
                    .instructions()
                    .forEach(
                        instruction -> {
                          if (instruction.isInvoke()) {
                            DexMethod target = instruction.getMethod();
                            if (target.getHolderType() == inspector.getFactory().unsafeType) {
                              if (target.getName() == compareAndSwapInt
                                  || target.getName() == compareAndSwapLong) {
                                // All compareAndSwapInt and compareAndSwapLong stay on
                                // DesugarVarHandle.
                                assertSame(
                                    clazz.getDexProgramClass().getType(),
                                    inspector.getFactory().desugarVarHandleType);
                                unsafeCompareAndSwapInt.incrementIf(
                                    target.getName() == compareAndSwapInt);
                                unsafeCompareAndSwapLong.incrementIf(
                                    target.getName() == compareAndSwapLong);
                              } else if (target.getName() == compareAndSwapObject) {
                                // compareAndSwapObject is not on DesugarVarHandle - it must be
                                // backported.
                                assertNotSame(
                                    clazz.getDexProgramClass().getType(),
                                    inspector.getFactory().desugarVarHandleType);
                                assertEquals(
                                    clazz.getFinalReference(),
                                    firstBackportFromDesugarVarHandle.getHolderClass());
                                assertEquals(1, clazz.allMethods().size());
                                assertEquals(
                                    firstBackportFromDesugarVarHandle,
                                    clazz.allMethods().iterator().next().getFinalReference());
                                unsafeCompareAndSwapObject.increment();
                              }
                            }
                          }
                        });
              });
        });
    if (willDesugarVarHandle()) {
      assertEquals(2, unsafeCompareAndSwapInt.get());
      assertEquals(3, unsafeCompareAndSwapLong.get());
      assertEquals(1, unsafeCompareAndSwapObject.get());
    } else {
      assertEquals(0, unsafeCompareAndSwapInt.get());
      assertEquals(0, unsafeCompareAndSwapLong.get());
      assertEquals(0, unsafeCompareAndSwapObject.get());
    }
  }

  // TODO(b/247076137: Also turn on VarHandle desugaring for R8 tests.
  @Test
  public void testD8() throws Throwable {
    assumeTrue(parameters.isDexRuntime());
    if (getTestWithDesugaring()) {
      testForD8(parameters.getBackend())
          .addProgramClassFileData(getProgramClassFileData())
          .setMinApi(parameters.getApiLevel())
          .addOptionsModification(options -> options.enableVarHandleDesugaring = true)
          .run(parameters.getRuntime(), getMainClass())
          .applyIf(
              parameters.isDexRuntime()
                  && parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V4_4_4),
              // TODO(b/247076137): Running on 4.0.4 and 4.4.4 needs to be checked. Output seems
              // correct, but at the same time there are VFY errors on stderr.
              r -> r.assertFailureWithErrorThatThrows(NoSuchFieldException.class),
              r ->
                  r.assertSuccessWithOutput(
                      parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.T)
                              && parameters
                                  .getDexRuntimeVersion()
                                  .isNewerThanOrEqual(Version.V13_0_0)
                          ? getExpectedOutputForArtImplementation()
                          : getExpectedOutputForReferenceImplementation()))
          .inspect(this::inspect);
    } else {
      testForD8(parameters.getBackend())
          .addProgramClassFileData(getProgramClassFileData())
          .setMinApi(parameters.getApiLevel())
          .run(parameters.getRuntime(), getMainClass())
          .applyIf(
              // VarHandle is available from Android 9, even though it was not a public API until
              // 13.
              parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V7_0_0),
              r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class),
              parameters.getApiLevel().isLessThan(AndroidApiLevel.P)
                  || parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V8_1_0),
              r -> r.assertFailure(),
              r -> r.assertSuccessWithOutput(getExpectedOutputForArtImplementation()));
    }
  }

  // TODO(b/247076137: Also turn on VarHandle desugaring for R8 tests.
  @Test
  public void testR8() throws Throwable {
    testForR8(parameters.getBackend())
        .applyIf(
            parameters.isDexRuntime(),
            // Use android.jar from Android T to get the VarHandle type.
            b -> b.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T)),
            // Use system JDK to have references types including StringConcatFactory.
            b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
        .addProgramClassFileData(getProgramClassFileData())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(getMainClass())
        .addKeepRules(getKeepRules())
        .applyIf(
            parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(AndroidApiLevel.O),
            R8TestBuilder::allowDiagnosticWarningMessages)
        .run(parameters.getRuntime(), getMainClass())
        .applyIf(
            // VarHandle is available from Android 9, even though it was not a public API until 13.
            parameters.isDexRuntime()
                && parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V7_0_0),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class),
            parameters.isDexRuntime()
                && (parameters.getApiLevel().isLessThan(AndroidApiLevel.P)
                    || parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V8_1_0)),
            r -> r.assertFailure(),
            r ->
                r.assertSuccessWithOutput(
                    parameters.isCfRuntime()
                        ? getExpectedOutputForReferenceImplementation()
                        : getExpectedOutputForArtImplementation()));
  }
}
