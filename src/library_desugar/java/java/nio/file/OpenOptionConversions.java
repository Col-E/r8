// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.nio.file;

import static java.util.ConversionRuntimeException.exception;

public class OpenOptionConversions {
  public static java.nio.file.OpenOption convert(j$.nio.file.OpenOption option) {
    if (option == null) {
      return null;
    }
    if (option instanceof j$.nio.file.StandardOpenOption) {
      return j$.nio.file.StandardOpenOption.wrap_convert((j$.nio.file.StandardOpenOption) option);
    }
    if (option instanceof j$.nio.file.LinkOption) {
      return j$.nio.file.LinkOption.wrap_convert((j$.nio.file.LinkOption) option);
    }
    throw exception("java.nio.file.OpenOption", option);
  }

  public static j$.nio.file.OpenOption convert(java.nio.file.OpenOption option) {
    if (option == null) {
      return null;
    }
    if (option instanceof java.nio.file.StandardOpenOption) {
      return j$.nio.file.StandardOpenOption.wrap_convert((java.nio.file.StandardOpenOption) option);
    }
    if (option instanceof java.nio.file.LinkOption) {
      return j$.nio.file.LinkOption.wrap_convert((java.nio.file.LinkOption) option);
    }
    throw exception("java.nio.file.OpenOption", option);
  }
}
