// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package identifiernamestring;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class R {

  static Field findField(Class clazz, String name) throws NoSuchFieldException {
    return clazz.getDeclaredField(name);
  }

  static Method findMethod(Class clazz, String name, Class[] params) throws NoSuchMethodException {
    return clazz.getDeclaredMethod(name, params);
  }

}
