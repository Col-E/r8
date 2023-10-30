// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.threading.providers.blocking;

import com.android.tools.r8.threading.ThreadingModule;
import com.android.tools.r8.threading.ThreadingModuleProvider;

public class ThreadingModuleBlockingProvider implements ThreadingModuleProvider {

  @Override
  public ThreadingModule create() {
    return new ThreadingModuleBlocking();
  }
}
