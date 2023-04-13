// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AtomicReferenceArrayTest extends AbstractBackportTest {

  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withDexRuntimesStartingFromExcluding(Version.V4_0_4)
        .withCfRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public AtomicReferenceArrayTest(TestParameters parameters) {
    super(parameters, AtomicReferenceArray.class, Main.class);

    ignoreInvokes("<init>");

    // java.util.concurrent.atomic.AtomicReferenceArray issue is on API 31, see b/211646483.
    registerTarget(AndroidApiLevel.Sv2, 3);
  }

  public static class Main extends MiniAssert {
    public volatile String field;

    public static void main(String[] args) {
      AtomicReferenceArray<String> reference = new AtomicReferenceArray<>(new String[1]);
      assertTrue(reference.compareAndSet(0, null, "A"));
      assertTrue(reference.compareAndSet(0, "A", "B"));
      assertFalse(reference.compareAndSet(0, "A", "B"));
    }
  }
}
