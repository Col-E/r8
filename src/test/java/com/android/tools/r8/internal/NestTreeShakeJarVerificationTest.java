// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Stream;
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
            options -> options.testing.allowTypeErrors = true,
            ImmutableList.of(BASE + DEPLOY_JAR));
    assertEquals(0, filterKotlinMetadata(handler.warnings).count());
    assertEquals(0, filterKotlinMetadata(handler.infos).count());
  }

  private Stream<Diagnostic> filterKotlinMetadata(List<Diagnostic> warnings) {
    return warnings.stream().filter(diagnostic -> {
      return diagnostic.getDiagnosticMessage().contains("kotlin.Metadata");
    });
  }
}
