// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.proguard.configuration;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.TestBase;
import java.util.List;
import org.junit.Test;

public class UnusedKeepRuleTest extends TestBase {

  @Test
  public void test() throws Exception {
    testForR8(Backend.DEX)
        .addKeepRules("-keep class NotPresent")
        .addOptionsModification(
            options -> options.testing.reportUnusedProguardConfigurationRules = true)
        .allowDiagnosticMessages()
        .allowUnusedProguardConfigurationRules()
        .compile()
        .inspectDiagnosticMessages(
            messages -> {
              messages.assertOnlyInfos();
              List<Diagnostic> infos = messages.getInfos();
              assertEquals(1, infos.size());

              Diagnostic info = infos.get(0);
              assertThat(
                  info.getDiagnosticMessage(),
                  containsString("Proguard configuration rule does not match anything"));
            });
  }
}
