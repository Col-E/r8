// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.utils.Pair;
import java.util.List;

public class DesugarTestRunResult
    extends TestRunResultCollection<DesugarTestConfiguration, DesugarTestRunResult> {

  public static DesugarTestRunResult create(
      List<Pair<DesugarTestConfiguration, TestRunResult<?>>> runs) {
    assert !runs.isEmpty();
    return new DesugarTestRunResult(runs);
  }

  private DesugarTestRunResult(List<Pair<DesugarTestConfiguration, TestRunResult<?>>> runs) {
    super(runs);
  }

  @Override
  DesugarTestRunResult self() {
    return this;
  }
}
