// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class NoDesugaredTypesInSignatures {

  private int field;

  public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
    // Code where references to VarHandle and MethodHandles$Lookup is only in code and not in any
    // signature. javac will still add references in InnerClasses attributes.
    VarHandle varHandle =
        MethodHandles.lookup()
            .findVarHandle(NoDesugaredTypesInSignatures.class, "field", int.class);

    NoDesugaredTypesInSignatures instance = new NoDesugaredTypesInSignatures();
    System.out.println((int) varHandle.get(instance));
  }
}
