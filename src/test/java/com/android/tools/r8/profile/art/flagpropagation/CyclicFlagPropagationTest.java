// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.flagpropagation;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexItemFactory.ObjectMembers;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.profile.art.ArtProfileMethodRule;
import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfoImpl.Builder;
import com.android.tools.r8.profile.rewriting.ProfileAdditions.NestedMethodRuleAdditionsGraph;
import com.google.common.collect.ImmutableList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CyclicFlagPropagationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    NestedMethodRuleAdditionsGraph<ArtProfileMethodRule, ArtProfileMethodRule.Builder> graph =
        new NestedMethodRuleAdditionsGraph<>();
    ObjectMembers objectMembers = new DexItemFactory().objectMembers;

    // Add an edge from equals() -> hashCode().
    graph.recordMethodRuleInfoFlagsLargerThan(objectMembers.hashCode, objectMembers.equals);

    // Add an edge from hashCode() -> finalize().
    graph.recordMethodRuleInfoFlagsLargerThan(objectMembers.finalize, objectMembers.hashCode);

    // Add an edge from hashCode() -> toString().
    graph.recordMethodRuleInfoFlagsLargerThan(objectMembers.toString, objectMembers.hashCode);

    // Add an edge from toString() -> equals().
    graph.recordMethodRuleInfoFlagsLargerThan(objectMembers.equals, objectMembers.toString);

    // Verify we detect the cycle.
    assertThrows(AssertionError.class, graph::verifyNoCycles);

    // Verify we detect the cycle when starting from equals(), hashCode(), and toString().
    assertThrows(
        AssertionError.class, () -> graph.verifyNoCyclesStartingFrom(objectMembers.equals));
    assertThrows(
        AssertionError.class, () -> graph.verifyNoCyclesStartingFrom(objectMembers.hashCode));
    assertThrows(
        AssertionError.class, () -> graph.verifyNoCyclesStartingFrom(objectMembers.toString));

    // Verify that flag propagation works.
    List<DexMethod> methods =
        ImmutableList.of(
            objectMembers.equals,
            objectMembers.finalize,
            objectMembers.hashCode,
            objectMembers.toString);
    Map<DexMethod, ArtProfileMethodRule.Builder> methodRuleBuilders = new IdentityHashMap<>();
    for (DexMethod method : methods) {
      methodRuleBuilders.put(method, ArtProfileMethodRule.builder());
    }
    methodRuleBuilders.get(objectMembers.equals).acceptMethodRuleInfoBuilder(Builder::setIsStartup);
    graph.propagateMethodRuleInfoFlags(methodRuleBuilders);
    for (DexMethod method : methods) {
      assertTrue(methodRuleBuilders.get(method).build().getMethodRuleInfo().isStartup());
    }
  }
}
