// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.sample.split;

import android.app.Activity;
import android.os.Bundle;
import com.android.tools.r8.sample.split.R;
import com.android.tools.r8.sample.split.SplitClass;

public class R8Activity extends Activity {
  private int res = 0;

  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setTheme(android.R.style.Theme_Light);
    setContentView(R.layout.main);
    // Currently this is split up into 100 iterations to be able to better see
    // the impact of the jit on later versions of art.
    long total = 0;
    for (int i = 0; i < 100; i++) {
      total += benchmarkCall();
    }
    System.out.println("Total: " + total);
  }

  public long benchmarkCall() {
    SplitClass split = new SplitClass(3);
    long start = System.nanoTime();
    for (int i = 0; i < 1000; i++) {
      // Ensure no dead code elimination.
      res = split.calculate(i);
    }
    long finish = System.nanoTime();
    long timeElapsed = finish - start;
    System.out.println("Took: " + timeElapsed);
    return timeElapsed;
  }
}
