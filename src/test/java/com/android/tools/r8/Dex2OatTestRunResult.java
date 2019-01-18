// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;

public class Dex2OatTestRunResult extends TestRunResult<Dex2OatTestRunResult> {

  public Dex2OatTestRunResult(AndroidApp app, ProcessResult result) {
    super(app, result);
  }

  @Override
  protected Dex2OatTestRunResult self() {
    return this;
  }

  public Dex2OatTestRunResult assertNoVerificationErrors() {
    assertSuccess();
    Matcher<? super String> matcher =
        CoreMatchers.not(CoreMatchers.containsString("Verification error"));
    assertThat(
        errorMessage("Run dex2oat produced verification errors.", matcher.toString()),
        getStdErr(),
        matcher);
    return self();
  }
}
