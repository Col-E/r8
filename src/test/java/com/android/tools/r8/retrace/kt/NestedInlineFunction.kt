// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package retrace

// Some space to distinguish line number with Inlinefunction numbers.

inline fun nestedInline(f: () -> Unit) {
  println("in nestedInline")
  inlineExceptionStatic(f)
  println("will never be printed")
}

inline fun nestedInlineOnFirstLine(f: () -> Unit) {
  inlineExceptionStatic(f)
}
