// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class GlobalSyntheticsResourceFile implements GlobalSyntheticsResourceProvider {

  private final Path file;
  private final Origin origin;

  public GlobalSyntheticsResourceFile(Path file) {
    this.file = file;
    this.origin = new PathOrigin(file);
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public InputStream getByteStream() throws ResourceException {
    try {
      return Files.newInputStream(file);
    } catch (IOException e) {
      throw new ResourceException(origin, e);
    }
  }
}
