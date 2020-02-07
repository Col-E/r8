// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.sample.simple;

import android.app.Activity;
import android.os.Bundle;
import com.android.tools.r8.sample.simple.R;
import java.util.ArrayList;
import android.content.res.Resources;


public class R8Activity extends Activity {
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setTheme(android.R.style.Theme_Light);
    setContentView(R.layout.main);
    System.out.println(R.string.referenced_from_code);
    useResources();
  }


  public void useResources() {
    // Add usage of resource identifiers with ID's that would never
    // exist in aapt generated R class (so that we can swap them out).
    ArrayList<Integer> resources = new ArrayList();
    resources.add(0x7fDEAD01);
    resources.add(0x7fDEAD02);
    resources.add(0x7fDEAD03);
    resources.add(0x7fDEAD04);
    resources.add(0x7fDEAD05);
    resources.add(0x7fDEAD06);
    resources.add(0x7fDEAD07);
    resources.add(0x7fDEAD08);
    resources.add(0x7fDEAD09);
    resources.add(0x7fDEAD0a);
    for (int id : resources) {
      try {
        getResources().getResourceName(id);
      } catch (Resources.NotFoundException e) {
        // Do nothing
      }
    }
  }
}
