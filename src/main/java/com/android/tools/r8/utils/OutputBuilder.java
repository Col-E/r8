// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.origin.Origin;
import java.io.Closeable;
import java.nio.file.Path;

public interface OutputBuilder extends Closeable {
  char NAME_SEPARATOR = '/';

  void open();

  void addDirectory(String name, DiagnosticsHandler handler);

  void addFile(String name, DataEntryResource content, DiagnosticsHandler handler);

  void addFile(String name, byte[] content, DiagnosticsHandler handler);

  Path getPath();

  Origin getOrigin();
}
