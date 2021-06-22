// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static com.google.common.base.Predicates.alwaysFalse;
import static com.google.common.base.Predicates.alwaysTrue;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignatureContextBuilder.TypeParameterContext;
import com.android.tools.r8.graph.GenericSignaturePartialTypeArgumentApplier;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.BiPredicateUtils;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSignaturePartialTypeArgumentApplierTest extends TestBase {

  private final TestParameters parameters;
  private final DexItemFactory itemFactory = new DexItemFactory();
  private final DexType objectType = itemFactory.objectType;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public GenericSignaturePartialTypeArgumentApplierTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testVariablesInOuterPosition() throws Exception {
    runTest(
            ImmutableMap.of("T", objectType, "R", objectType),
            Collections.emptySet(),
            BiPredicateUtils.alwaysFalse(),
            alwaysTrue(),
            "(TT;)TR;",
            "(Ljava/lang/Object;)Ljava/lang/Object;")
        .assertNoMessages();
  }

  @Test
  public void testVariablesInInnerPosition() throws Exception {
    runTest(
            ImmutableMap.of("T", objectType, "R", objectType),
            Collections.emptySet(),
            BiPredicateUtils.alwaysFalse(),
            alwaysTrue(),
            "(LList<TT;>;)LList<TR;>;",
            "(LList<Ljava/lang/Object;>;)LList<Ljava/lang/Object;>;")
        .assertNoMessages();
  }

  @Test
  public void testRemovingPrunedLink() throws Exception {
    runTest(
            Collections.emptyMap(),
            Collections.emptySet(),
            BiPredicateUtils.alwaysTrue(),
            alwaysTrue(),
            "(LFoo<Ljava/lang/String;>.Bar<Ljava/lang/Integer;>;)"
                + "LFoo<Ljava/lang/String;>.Bar<Ljava/lang/Integer;>;",
            "(LFoo$Bar<Ljava/lang/Integer;>;)LFoo$Bar<Ljava/lang/Integer;>;")
        .assertNoMessages();
  }

  @Test
  public void testRemovedGenericArguments() throws Exception {
    runTest(
            Collections.emptyMap(),
            Collections.emptySet(),
            BiPredicateUtils.alwaysTrue(),
            alwaysFalse(),
            "(LFoo<Ljava/lang/String;>;)LFoo<Ljava/lang/String;>.Bar<Ljava/lang/Integer;>;",
            "(LFoo;)LFoo$Bar;")
        .assertNoMessages();
  }

  private TestDiagnosticMessages runTest(
      Map<String, DexType> substitutions,
      Set<String> liveVariables,
      BiPredicate<DexType, DexType> removedLink,
      Predicate<DexType> hasFormalTypeParameters,
      String initialSignature,
      String expectedRewrittenSignature)
      throws Exception {
    AppView<AppInfo> appView = computeAppView(AndroidApp.builder().build());
    GenericSignaturePartialTypeArgumentApplier argumentApplier =
        GenericSignaturePartialTypeArgumentApplier.build(
            appView,
            TypeParameterContext.empty()
                .addPrunedSubstitutions(substitutions)
                .addLiveParameters(liveVariables),
            removedLink,
            hasFormalTypeParameters);
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    MethodTypeSignature methodTypeSignature =
        argumentApplier.visitMethodSignature(
            GenericSignature.parseMethodSignature(
                "foo", initialSignature, Origin.unknown(), itemFactory, diagnosticsHandler));
    diagnosticsHandler.assertNoMessages();
    String rewrittenSignature =
        argumentApplier.visitMethodSignature(methodTypeSignature).toString();
    assertEquals(expectedRewrittenSignature, rewrittenSignature);
    GenericSignature.parseMethodSignature(
        "foo", rewrittenSignature, Origin.unknown(), itemFactory, diagnosticsHandler);
    return diagnosticsHandler;
  }
}
