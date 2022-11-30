// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugar.sun.nio.fs;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

class DesugarAndroidBasicFileAttributeView extends DesugarBasicFileAttributeView {

  private final Path path;

  public DesugarAndroidBasicFileAttributeView(Path path) {
    super(path);
    this.path = path;
  }

  @Override
  public BasicFileAttributes readAttributes() throws IOException {
    path.getFileSystem().provider().checkAccess(path);
    return super.readAttributes();
  }

  @Override
  public Map<String, Object> readAttributes(String[] requested) throws IOException {
    path.getFileSystem().provider().checkAccess(path);
    return super.readAttributes(requested);
  }
}
