// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.internal.matchers.ThrowableMessageMatcher.hasMessage;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassHierarchyVerifier;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.Test;

public class NestTreeShakeJarVerificationTest extends NestCompilationBase {

  @Test
  public void buildAndTreeShakeFromDeployJar() throws Exception {
    AndroidApp app =
        runAndCheckVerification(
            CompilerUnderTest.R8,
            CompilationMode.RELEASE,
            null,
            ImmutableList.of(BASE + PG_CONF, BASE + PG_CONF_NO_OPT),
            null,
            ImmutableList.of(BASE + DEPLOY_JAR));


    // Check that all non-abstract classes implement the abstract methods from their super types.
    // This is a sanity check for the tree shaking and minification.
    try {
      CodeInspector inspector = new CodeInspector(app);
      new ClassHierarchyVerifier(inspector, false).run();
    } catch (AssertionError error) {
      // TODO(b/115705526): Remove try-catch when bug is fixed.
      assertThat(error, hasMessage(containsString("Non-abstract class")));
      assertThat(error, hasMessage(containsString("must implement method")));
      return;
    }

    Assert.fail("Unreachable");
  }
}
