// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Jdk11TimeRawTemporalTests extends Jdk11TimeAbstractTests {

  public Jdk11TimeRawTemporalTests(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    super(parameters, libraryDesugaringSpecification, compilationSpecification);
  }

  @Test
  public void testTime() throws Exception {
    testTime(RAW_TEMPORAL_SUCCESSES);
    if (parameters.getDexRuntimeVersion().isOlderThan(Version.V12_0_0)) {
      // In 12 some ISO is supported that other versions do not support.
      testTime(RAW_TEMPORAL_SUCCESSES_UP_TO_11);
    }
    // The bridge is always present with JDK11 due to partial desugaring between 26 and 33.
    // On JDK8 the bridge is absent in between 26 and 33.
    if (libraryDesugaringSpecification != JDK8
        || !parameters.getApiLevel().betweenBothIncluded(AndroidApiLevel.O, AndroidApiLevel.Sv2)) {
      testTime(RAW_TEMPORAL_SUCCESSES_IF_BRIDGE);
    }
  }
}
