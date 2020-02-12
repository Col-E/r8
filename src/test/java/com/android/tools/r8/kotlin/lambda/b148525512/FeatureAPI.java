// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda.b148525512;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class FeatureAPI {
  public static boolean hasFeature() {
    try {
      Class.forName(FeatureAPI.class.getPackage().getName() + ".FeatureKt");
    } catch (ClassNotFoundException e) {
      return false;
    }
    return true;
  }

  public static void feature(int i)
      throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
          IllegalAccessException {
    Class<?> featureKtClass = Class.forName(FeatureAPI.class.getPackage().getName() + ".FeatureKt");
    Method featureMethod = featureKtClass.getMethod("feature", int.class);
    featureMethod.invoke(null, i);
  }
}
