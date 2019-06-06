// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.interfacebridge;

public class OuterClass {

  public interface Callback extends Task.OnTaskFinishedListener<String> {
    @Override
    void onTaskFinished(String result);
  }

  private static class ConcreteTask extends Task<String> {

    public ConcreteTask(Callback listener) {
      super(listener);
    }

    @Override
    public String doInBackground() {
      return "FOO";
    }
  }

  public static void startTask(Callback callback) {
    new ConcreteTask(callback).execute();
  }
}
