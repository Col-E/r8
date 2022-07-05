// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import java.io.File;
import java.io.IOException;

public abstract class InstrumentationServer {

  public static InstrumentationServer getInstance() {
    return InstrumentationServerImpl.getInstance();
  }

  public abstract void writeToFile(File file) throws IOException;
}
