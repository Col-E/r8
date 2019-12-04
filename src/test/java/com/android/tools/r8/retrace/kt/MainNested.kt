// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package retrace

// Some spaces to better see if retrace is working as expected.


fun main(args: Array<String>) {
  nestedInline {
    throw Exception("Never get's here")
  }
}

