// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.util;

public class ConversionRuntimeException extends RuntimeException {

  public ConversionRuntimeException(String message) {
    super(message);
  }

  public static RuntimeException exception(String type, Object suffix) {
    throw new ConversionRuntimeException("Unsupported " + type + " :" + suffix);
  }
}
