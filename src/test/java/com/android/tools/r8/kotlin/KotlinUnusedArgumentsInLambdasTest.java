// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KotlinUnusedArgumentsInLambdasTest extends AbstractR8KotlinTestBase {

  @Parameterized.Parameters(name = "{0}, {1}, allowAccessModification: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values());
  }

  public KotlinUnusedArgumentsInLambdasTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean allowAccessModification) {
    super(parameters, kotlinParameters, allowAccessModification);
  }

  @Test
  public void testMergingKStyleLambdasAfterUnusedArgumentRemoval() throws Exception {
    final String mainClassName = "unused_arg_in_lambdas_kstyle.MainKt";
    runTest("unused_arg_in_lambdas_kstyle", mainClassName)
        .inspect(
            inspector ->
                inspector.forAllClasses(
                    classSubject -> {
                      if (classSubject.getOriginalDescriptor().contains("$ks")) {
                        MethodSubject init = classSubject.init(ImmutableList.of("int"));
                        assertThat(init, isPresent());
                        // Arity 2 should appear.
                        assertTrue(init.iterateInstructions(i -> i.isConstNumber(2)).hasNext());

                        MethodSubject invoke = classSubject.uniqueMethodWithOriginalName("invoke");
                        assertThat(invoke, isPresent());
                        assertEquals(2, invoke.getMethod().getReference().proto.parameters.size());
                      }
                    }));
  }

  @Test
  public void testMergingJStyleLambdasAfterUnusedArgumentRemoval() throws Exception {
    final String mainClassName = "unused_arg_in_lambdas_jstyle.MainKt";
    runTest("unused_arg_in_lambdas_jstyle", mainClassName)
        .inspect(
            inspector ->
                inspector.forAllClasses(
                    classSubject -> {
                      if (classSubject.getOriginalDescriptor().contains("$js")) {
                        MethodSubject get = classSubject.uniqueMethodWithOriginalName("get");
                        assertThat(get, isPresent());
                        assertEquals(3, get.getMethod().getReference().proto.parameters.size());
                      }
                    }));
  }
}
