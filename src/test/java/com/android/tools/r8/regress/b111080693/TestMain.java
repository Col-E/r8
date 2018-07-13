// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b111080693;

import com.android.tools.r8.regress.b111080693.b.RecyclerView;
import com.android.tools.r8.regress.b111080693.b.RecyclerView.Adapter;

public class TestMain {
  static final class TestAdapter extends Adapter {
    TestAdapter() {
    }
  }

  public static void main(String[] args) {
    TestAdapter adapter = new TestAdapter();
    RecyclerView view = new RecyclerView();
    view.setAdapter(adapter);
    adapter.notifyDataSetChanged();
  }
}
