// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.sample.simple;

import android.app.Activity;
import android.os.Bundle;
import com.android.tools.r8.sample.simple.R;

public class R8Activity extends Activity {
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setTheme(android.R.style.Theme_Light);
    setContentView(R.layout.main);
  }
}
