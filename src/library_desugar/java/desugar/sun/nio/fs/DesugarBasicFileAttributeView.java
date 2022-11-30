// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugar.sun.nio.fs;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Map;

public class DesugarBasicFileAttributeView {

  public DesugarBasicFileAttributeView(Path path) {}

  public BasicFileAttributes readAttributes() throws IOException {
    return null;
  }

  public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime)
      throws IOException {}

  public Map<String, Object> readAttributes(String[] requested) throws IOException {
    return null;
  }
}
