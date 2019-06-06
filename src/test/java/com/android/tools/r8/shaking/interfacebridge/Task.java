// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.interfacebridge;

public abstract class Task<Result> {

  public interface OnTaskFinishedListener<Result> {
    void onTaskFinished(Result result);
  }

  public abstract Result doInBackground();

  public void execute() {
    onPostExecute(doInBackground());
  }

  OnTaskFinishedListener<Result> mListener;

  Task(OnTaskFinishedListener<Result> listener) {
    mListener = listener;
  }

  public void onPostExecute(Result result) {
    if (mListener != null) {
      mListener.onTaskFinished(result);
    }
  }
}
