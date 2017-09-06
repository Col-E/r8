// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package desugaringwithmissingclasstest5;

import desugaringwithmissingclasslib1.A;
import desugaringwithmissingclasslib1.A2;
import desugaringwithmissingclasslib4.C2;

public class ImplementMethodsWithDefault extends C2 implements A, A2 {
  @Override
  public String foo() {
    return "ImplementMethodsWithDefault";
  }
}
