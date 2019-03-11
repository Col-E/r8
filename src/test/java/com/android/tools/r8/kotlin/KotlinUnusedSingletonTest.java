// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.function.Consumer;
import org.junit.Test;

public class KotlinUnusedSingletonTest extends AbstractR8KotlinTestBase {
  private Consumer<InternalOptions> optionsModifier =
      o -> {
        o.enableTreeShaking = true;
        o.enableMinification = false;
      };

  public KotlinUnusedSingletonTest(
      KotlinTargetVersion targetVersion, boolean allowAccessModification) {
    super(targetVersion, allowAccessModification);
  }

  @Test
  public void b110196118() throws Exception {
    final String mainClassName = "unused_singleton.MainKt";
    final String moduleName = "unused_singleton.TestModule";
    runTest("unused_singleton", mainClassName, optionsModifier, app -> {
      CodeInspector inspector = new CodeInspector(app);
      ClassSubject main = inspector.clazz(mainClassName);
      assertThat(main, isPresent());
      MethodSubject mainMethod = main.mainMethod();
      assertThat(mainMethod, isPresent());
      // const-string of provideGreeting() is propagated.
      Iterator<InstructionSubject> it =
          mainMethod.iterateInstructions(i -> i.isConstString("Hello", JumboStringMode.ALLOW));
      assertTrue(it.hasNext());
      // But, static call is still there, since it may trigger class initialization.
      ClassSubject module = inspector.clazz(moduleName);
      assertThat(main, isPresent());
      MethodSubject provideGreetingMethod = module.uniqueMethodWithName("provideGreeting");
      assertThat(mainMethod, invokesMethod(provideGreetingMethod));

      // field `INSTANCE` is shrunk.
      FieldSubject instance = module.uniqueFieldWithName("INSTANCE");
      assertThat(instance, not(isPresent()));
      // TODO(b/110196118): remaining new-instance and invoke-direct <init> could be shrunk.
      // TODO(b/110196118): then, trivial---empty---<clinit> could be shrunk.
      MethodSubject clinit = module.clinit();
      assertThat(clinit, isPresent());
      // TODO(b/110196118): if the instantiation in <clinit> is gone, <init> is unreachable and can
      // be removed by TreePruner.
      MethodSubject init = module.init(ImmutableList.of());
      assertThat(init, isPresent());
    });
  }
}
