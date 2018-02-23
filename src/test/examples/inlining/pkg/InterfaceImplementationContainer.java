// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package inlining.pkg;

import inlining.IFace;

public class InterfaceImplementationContainer {
  private static class IFaceImplementation implements IFace {
    @Override
    public int foo() {
      return 42;
    }
  }

  public static IFace getIFace() {
    return new IFaceImplementation();
  }
}
