// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.errors.Unreachable;

public enum Visibility {
  PUBLIC,
  PROTECTED,
  PRIVATE,
  PACKAGE_PRIVATE;

  public boolean isPackagePrivate() {
    return this == PACKAGE_PRIVATE;
  }

  public boolean isPrivate() {
    return this == PRIVATE;
  }

  public boolean isProtected() {
    return this == PROTECTED;
  }

  public boolean isPublic() {
    return this == PUBLIC;
  }

  @Override
  public String toString() {
    switch (this) {
      case PUBLIC:
        return "public";

      case PROTECTED:
        return "protected";

      case PRIVATE:
        return "private";

      case PACKAGE_PRIVATE:
        return "package-private";

      default:
        throw new Unreachable("Unexpected visibility");
    }
  }
}
