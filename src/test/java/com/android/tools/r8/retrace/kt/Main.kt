// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package retrace

// Adding a few spaces to better see where the debug information is positioned.

fun main(args: Array<String>) {
  inlineExceptionStatic {
    throw Exception("Never get's here")
  }
}
