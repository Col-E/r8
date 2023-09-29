// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableSet;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class TestBackportedNotPresentInAndroidJar extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private Set<DexMethod> expectedToAlwaysBePresentInAndroidJar(DexItemFactory factory)
      throws Exception {
    MethodReference AtomicReferenceFieldUpdater_compareAndSet =
        Reference.methodFromMethod(
            AtomicReferenceFieldUpdater.class.getDeclaredMethod(
                "compareAndSet", Object.class, Object.class, Object.class));
    assert AtomicReferenceFieldUpdater_compareAndSet.getReturnType()
        .getTypeName()
        .equals("boolean");

    MethodReference AtomicReference_compareAndSet =
        Reference.methodFromMethod(
            AtomicReference.class.getDeclaredMethod("compareAndSet", Object.class, Object.class));
    assert AtomicReference_compareAndSet.getReturnType().getTypeName().equals("boolean");

    MethodReference AtomicReferenceArray_compareAndSet =
        Reference.methodFromMethod(
            AtomicReferenceArray.class.getDeclaredMethod(
                "compareAndSet", int.class, Object.class, Object.class));
    assert AtomicReference_compareAndSet.getReturnType().getTypeName().equals("boolean");

    MethodReference BigDecimal_stripTrailingZeros =
        Reference.methodFromMethod(BigDecimal.class.getDeclaredMethod("stripTrailingZeros"));
    assert BigDecimal_stripTrailingZeros.getReturnType()
        .getTypeName()
        .equals("java.math.BigDecimal");

    return ImmutableSet.of(
        factory.createMethod(AtomicReferenceFieldUpdater_compareAndSet),
        factory.createMethod(AtomicReference_compareAndSet),
        factory.createMethod(AtomicReferenceArray_compareAndSet),
        factory.createMethod(BigDecimal_stripTrailingZeros));
  }

  @Test
  public void testBackportedMethodsPerAPILevel() throws Exception {
    for (AndroidApiLevel apiLevel : AndroidApiLevel.values()) {
      if (apiLevel == AndroidApiLevel.MASTER) {
        continue;
      }
      if (apiLevel == AndroidApiLevel.U) {
        continue;
      }
      if (apiLevel == AndroidApiLevel.T) {
        continue;
      }
      if (!ToolHelper.hasAndroidJar(apiLevel)) {
        // Only check for the android jar versions present in third_party.
        System.out.println("Skipping check for " + apiLevel);
        continue;
      }
      // Check that the backported methods for each API level are are not present in the
      // android.jar for that level.
      CodeInspector inspector = new CodeInspector(ToolHelper.getAndroidJar(apiLevel));
      InternalOptions options = new InternalOptions();
      options.setMinApiLevel(apiLevel);
      List<DexMethod> backportedMethods =
          BackportedMethodRewriter.generateListOfBackportedMethods(
              AndroidApp.builder().build(), options, ThreadUtils.getExecutorService(options));
      Set<DexMethod> alwaysPresent = expectedToAlwaysBePresentInAndroidJar(options.itemFactory);
      for (DexMethod method : backportedMethods) {
        // Two different DexItemFactories are in play, but as toSourceString is used for lookup
        // that is not an issue.
        ClassSubject clazz = inspector.clazz(method.holder.toSourceString());
        MethodSubject foundInAndroidJar =
            clazz.method(
                method.proto.returnType.toSourceString(),
                method.name.toSourceString(),
                Arrays.stream(method.proto.parameters.values)
                    .map(DexType::toSourceString)
                    .collect(Collectors.toList()));
        assertThat(
            foundInAndroidJar + " present in " + apiLevel,
            foundInAndroidJar,
            notIf(isPresent(), !alwaysPresent.contains(method)));
      }
    }
  }
}
