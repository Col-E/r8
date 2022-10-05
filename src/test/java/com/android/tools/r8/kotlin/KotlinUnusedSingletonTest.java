// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinUnusedSingletonTest extends AbstractR8KotlinTestBase {

  @Parameters(name = "{0}, {1}, allowAccessModification: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values());
  }

  private static final String printlnSignature =
      "void java.io.PrintStream.println(java.lang.Object)";

  public KotlinUnusedSingletonTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean allowAccessModification) {
    super(parameters, kotlinParameters, allowAccessModification);
  }

  @Test
  public void b110196118() throws Exception {
    final String mainClassName = "unused_singleton.MainKt";
    final String moduleName = "unused_singleton.TestModule";
    runTest("unused_singleton", mainClassName)
        .inspect(
            inspector -> {
              ClassSubject main = inspector.clazz(mainClassName);
              assertThat(main, isPresent());

              MethodSubject mainMethod = main.mainMethod();
              assertThat(mainMethod, isPresent());

              // The const-string from provideGreeting() has been propagated.
              assertTrue(
                  mainMethod
                      .iterateInstructions(i -> i.isConstString("Hello", JumboStringMode.ALLOW))
                      .hasNext());

              // The method provideGreeting() is no longer being invoked -- i.e., we have been able
              // to determine that the class initialization of the enclosing class is trivial.
              ClassSubject module = inspector.clazz(moduleName);
              // TODO(b/179897889): Should probably check for module being present.
              assertThat(main, isPresent());
              assertEquals(
                  0,
                  mainMethod
                      .streamInstructions()
                      .filter(InstructionSubject::isInvoke)
                      .map(i -> i.getMethod().toSourceString())
                      .filter(
                          invokedMethod ->
                              !invokedMethod.equals(checkParameterIsNotNullSignature)
                                  && !invokedMethod.equals(printlnSignature)
                                  && !invokedMethod.equals(
                                      throwParameterIsNotNullExceptionSignature))
                      .count());

              // The field `INSTANCE` has been removed entirely.
              FieldSubject instance = module.uniqueFieldWithOriginalName("INSTANCE");
              assertThat(instance, not(isPresent()));

              // The class initializer is no longer there.
              MethodSubject clinit = module.clinit();
              assertThat(clinit, not(isPresent()));

              // Also, the instance initializer is no longer there, since it is only reachable from
              // the class initializer.
              MethodSubject init = module.init(ImmutableList.of());
              assertThat(init, not(isPresent()));
            });
  }
}
