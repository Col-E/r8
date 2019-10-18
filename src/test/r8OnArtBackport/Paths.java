// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.nio.file;

import com.android.tools.r8.MockedPath;

public final class Paths {

  private Paths() {}

  public static Path get(String first, String... more) {
    return MockedPath.of(first, more);
  }
}
