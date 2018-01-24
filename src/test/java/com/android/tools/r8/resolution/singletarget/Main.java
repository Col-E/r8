// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.singletarget;

import com.android.tools.r8.resolution.singletarget.one.AbstractTopClass;
import com.android.tools.r8.resolution.singletarget.one.InterfaceWithDefault;
import com.android.tools.r8.resolution.singletarget.one.SubSubClassOne;
import com.android.tools.r8.resolution.singletarget.one.SubSubClassTwo;
import java.util.ArrayList;
import java.util.List;

public class Main {

  public static void main(String... args) {
    SubSubClassOne actualOne = new SubSubClassOne();
    SubSubClassTwo actualTwo = new SubSubClassTwo();
    List<AbstractTopClass> instances = new ArrayList<>();
    instances.add(actualOne);
    instances.add(actualTwo);

    for (AbstractTopClass top : instances) {
      top.abstractTargetAtTop();
      top.definedInTwoSubTypes();
      top.overriddenByIrrelevantInterface();
      top.overriddenInTwoSubTypes();
      top.overridenInAbstractClassOnly();
      top.singleShadowingOverride();
      top.defaultMethod();
      top.overriddenDefault();
      try {
        top.overriddenInOtherInterface();
      } catch (IncompatibleClassChangeError e) {
        System.out.println("ICCE");
      }
    }
    for (InterfaceWithDefault iface : instances) {
      iface.defaultMethod();
      iface.overriddenDefault();
      try {
        iface.overriddenInOtherInterface();
      } catch (IncompatibleClassChangeError e) {
        System.out.println("ICCE");
      }
    }
    actualTwo.defaultMethod();
    actualTwo.overriddenDefault();
    actualTwo.overriddenInOtherInterface();
    actualOne.defaultMethod();
    actualOne.overriddenDefault();
    try {
      actualOne.overriddenInOtherInterface();
    } catch (IncompatibleClassChangeError e) {
      System.out.println("ICCE");
    }
  }
}
