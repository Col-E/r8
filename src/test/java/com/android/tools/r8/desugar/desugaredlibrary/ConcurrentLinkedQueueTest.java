// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_MINIMAL;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConcurrentLinkedQueueTest extends DesugaredLibraryTestBase {

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
            .build(),
        ImmutableList.of(JDK11_MINIMAL, JDK11, JDK11_PATH),
        SPECIFICATIONS_WITH_CF2CF);
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

  @Test
  @Ignore("b/267483394")
  public void test() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(Executor.class)
        .compile()
        .inspectL8(this::inspect)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Executor {

    public static void main(String[] args) {
      Queue<String> queue = new ConcurrentLinkedQueue<>();
      queue.add("Hello, world!");
      System.out.println(queue.poll());
    }
  }
}
