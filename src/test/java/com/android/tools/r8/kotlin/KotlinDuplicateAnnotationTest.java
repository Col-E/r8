// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import java.util.function.Consumer;
import org.junit.Test;

public class KotlinDuplicateAnnotationTest extends AbstractR8KotlinTestBase {
  private Consumer<InternalOptions> optionsModifier =
    o -> {
      o.enableTreeShaking = true;
      o.enableMinification = false;
      o.enableInlining = false;
    };

  public KotlinDuplicateAnnotationTest(
      KotlinTargetVersion targetVersion, boolean allowAccessModification) {
    super(targetVersion, allowAccessModification);
  }

  @Test
  public void testDuplicateAnnotation() throws Exception {
    final String mainClassName = "duplicate_annotation.MainKt";
    try {
      runTest(
          "duplicate_annotation",
          mainClassName,
          StringUtils.lines(
              "-keep,allowobfuscation @interface **.TestAnnotation",
              "-keepattributes *Annotations*"
          ),
          optionsModifier,
          null);
    } catch (CompilationFailedException e) {
      assertThat(e.getCause().getMessage(), containsString("Unsorted annotation set"));
      assertThat(e.getCause().getMessage(), containsString("TestAnnotation"));
    }
  }

}
