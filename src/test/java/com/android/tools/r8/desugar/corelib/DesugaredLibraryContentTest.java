// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugaredLibraryContentTest extends CoreLibDesugarTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DesugaredLibraryContentTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDesugaredLibraryContent() throws Exception {
    CodeInspector inspector = new CodeInspector(buildDesugaredLibrary(parameters.getApiLevel()));
    inspector
        .allClasses()
        .forEach(
            clazz ->
                assertTrue(
                    clazz.getOriginalName().startsWith("j$.")
                        || clazz
                            .getOriginalName()
                            .contains(BackportedMethodRewriter.UTILITY_CLASS_NAME_PREFIX)));
    assertThat(inspector.clazz("j$.time.Clock"), isPresent());
    // Above N the following classes are removed instead of being desugared.
    if (parameters.getApiLevel().getLevel() >= AndroidApiLevel.N.getLevel()) {
      assertFalse(inspector.clazz("j$.util.Optional").isPresent());
      assertFalse(inspector.clazz("j$.util.function.Function").isPresent());
      return;
    }
    assertThat(inspector.clazz("j$.util.Optional"), isPresent());
    assertThat(inspector.clazz("j$.util.function.Function"), isPresent());
  }
}
