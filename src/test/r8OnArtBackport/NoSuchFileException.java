// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.nio.file;

import java.io.IOException;

public class NoSuchFileException extends IOException {
  public NoSuchFileException(String file) {
    super(file);
  }
}
