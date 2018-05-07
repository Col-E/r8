// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// This code is not run directly. It needs to be compiled to dex code.
// 'arithmetic.dex' is what is run.

package dataresource;

import dataresource.lib.LibClass;
import java.io.IOException;

public class ResourceTest {
  public static void main(String[] args) throws IOException {
    System.out.println("LibClass dir: " + (LibClass.getThisDir() != null));
    System.out.println("LibClass properties: " + (LibClass.getLibClassProperties() != null));
    System.out.println("LibClass property: " + LibClass.getLibClassProperty());
    System.out.println("LibClass text: " + LibClass.getText());
    System.out.println("LibClass const string: " + LibClass.getConstString());
    System.out.println("LibClass concat string: " + LibClass.getConcatConstString());
    System.out.println("LibClass field: " + LibClass.getConstField());
  }
}
