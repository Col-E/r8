// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.origin;

import java.nio.file.Path;

/** Path component in an origin description. */
public class PathOrigin extends Origin {
  private final Path path;

  public PathOrigin(Path path, Origin parent) {
    super(parent);
    assert path != null;
    this.path = path;
  }

  @Override
  public String part() {
    return path.toString();
  }
}
