// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package multidex002;

public class AnnotatedMethodParameter {

  public void foo(@AnnotationWithEnum3(AnnotationWithEnum3.Value3.VAL3_2) String val) {
  }
}
