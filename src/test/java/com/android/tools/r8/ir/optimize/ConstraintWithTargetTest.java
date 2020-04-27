// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.AndroidApp;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConstraintWithTargetTest extends TestBase {

  private static DexItemFactory factory;
  private static AppView<AppInfoWithLiveness> appView;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private final TestParameters parameters;

  public ConstraintWithTargetTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @BeforeClass
  public static void makeAppInfo() throws Exception {
    AndroidApp app = AndroidApp.builder().addLibraryFiles(ToolHelper.getJava8RuntimeJar()).build();
    appView = computeAppViewWithLiveness(app);
    factory = appView.dexItemFactory();
  }

  private ConstraintWithTarget never() {
    return ConstraintWithTarget.NEVER;
  }

  private ConstraintWithTarget always() {
    return ConstraintWithTarget.ALWAYS;
  }

  private ConstraintWithTarget element(Constraint constraint, DexType type) {
    return new ConstraintWithTarget(constraint, type);
  }

  private ConstraintWithTarget meet(ConstraintWithTarget e1, ConstraintWithTarget e2) {
    return ConstraintWithTarget.meet(e1, e2, appView);
  }

  @Test
  public void meetNeverIsNever() {
    assertEquals(never(),
        meet(never(), always()));
    assertEquals(never(),
        meet(always(), never()));
    assertEquals(never(),
        meet(never(), element(Constraint.SAMECLASS, factory.objectType)));
  }

  @Test
  public void meetAlwaysIsUnit() {
    ConstraintWithTarget o = element(Constraint.SUBCLASS, factory.objectType);
    assertEquals(o,
        meet(always(), o));
    assertEquals(o,
        meet(o, always()));
  }

  @Test
  public void withSameTarget() {
    DexType s = factory.createType("Ljava/lang/String;");
    ConstraintWithTarget c0 = element(Constraint.SAMECLASS, s);
    ConstraintWithTarget c1 = element(Constraint.PACKAGE, s);
    ConstraintWithTarget c2 = element(Constraint.SUBCLASS, s);
    assertEquals(c0,
        meet(c1, c0));
    assertEquals(c0,
        meet(c0, c2));
    assertEquals(c1,
        meet(c2, c1));
  }

  @Test
  public void withDifferentTarget() {
    DexType s = factory.createType("Ljava/lang/String;");
    DexType b = factory.createType("Ljava/lang/StringBuilder;");
    ConstraintWithTarget c1 = element(Constraint.SAMECLASS, s);
    ConstraintWithTarget c2 = element(Constraint.SAMECLASS, b);
    assertEquals(never(),
        meet(c1, c2));

    ConstraintWithTarget c0 = element(Constraint.PACKAGE, factory.objectType);
    assertEquals(c1,
        meet(c0, c1));
    assertEquals(c2,
        meet(c0, c2));

    c0 = element(Constraint.SUBCLASS, factory.objectType);
    assertEquals(c1,
        meet(c0, c1));
    assertEquals(c2,
        meet(c0, c2));

    c1 = element(Constraint.PACKAGE, s);
    c2 = element(Constraint.PACKAGE, b);
    assertEquals(c1,
        meet(c0, c1));
    assertEquals(c2,
        meet(c0, c2));
    assertEquals(c1,
        meet(c1, c2));
    assertEquals(c2,
        meet(c2, c1));

    DexType t = factory.createType("Ljava/lang/reflect/Type;");
    DexType c = factory.createType("Ljava/lang/Class;");
    c1 = element(Constraint.SUBCLASS, t);
    c2 = element(Constraint.SUBCLASS, c);
    assertEquals(c2,
        meet(c1, c2));
    assertEquals(c2,
        meet(c2, c1));
  }


  @Test
  public void b111080693() {
    ConstraintWithTarget c1 =
        element(Constraint.SUBCLASS, factory.createType("Ljava/lang/Class;"));
    ConstraintWithTarget c2 =
        element(Constraint.PACKAGE, factory.createType("Ljava/lang/reflect/Type;"));
    assertEquals(never(),
        meet(c1, c2));
  }

}
