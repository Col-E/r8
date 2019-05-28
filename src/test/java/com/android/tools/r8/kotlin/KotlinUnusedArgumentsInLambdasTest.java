// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.function.Consumer;
import org.junit.Ignore;
import org.junit.Test;

public class KotlinUnusedArgumentsInLambdasTest extends AbstractR8KotlinTestBase {
  private Consumer<InternalOptions> optionsModifier =
    o -> {
      o.enableInlining = true;
      o.enableLambdaMerging = true;
      o.enableArgumentRemoval = true;
      o.enableUnusedArgumentRemoval = true;
    };

  public KotlinUnusedArgumentsInLambdasTest(
      KotlinTargetVersion targetVersion, boolean allowAccessModification) {
    super(targetVersion, allowAccessModification);
  }

  @Ignore("b/133379765")
  @Test
  public void testMergingKStyleLambdasAndReprocessing() throws Exception {
    final String mainClassName = "unused_arg_in_lambdas_kstyle.MainKt";
    runTest("unused_arg_in_lambdas_kstyle", mainClassName, optionsModifier, app -> {
      CodeInspector inspector = new CodeInspector(app);
      inspector.forAllClasses(classSubject -> {
        if (classSubject.getOriginalDescriptor().contains("$ks")) {
          MethodSubject init = classSubject.init(ImmutableList.of("int"));
          assertThat(init, isPresent());
          // Arity 1 should appear.
          assertTrue(init.iterateInstructions(i -> i.isConstNumber(1)).hasNext());
        }
      });
    });
  }
}
