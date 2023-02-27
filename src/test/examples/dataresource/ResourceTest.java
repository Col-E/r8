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
    System.out.print("LibClass dir: " + (LibClass.getThisDir() != null) + '\n');
    System.out.print("LibClass properties: " + (LibClass.getLibClassProperties() != null) + '\n');
    System.out.print("LibClass property: " + LibClass.getLibClassProperty() + '\n');
    System.out.print("LibClass text: " + LibClass.getText() + '\n');
    System.out.print("LibClass const string: " + LibClass.getConstString() + '\n');
    System.out.print("LibClass concat string: " + LibClass.getConcatConstString() + '\n');
    System.out.print("LibClass field: " + LibClass.getConstField() + '\n');
  }
}
