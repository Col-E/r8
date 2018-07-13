// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.dexinspector.Matchers.isPresent;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.dexinspector.ClassSubject;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.List;
import org.junit.Test;

class L1 {
  private final String x;

  private L1(String x) {
    this.x = x;
  }

  private L1() {
    this("private_x");
  }

  static L1 create() {
    return new L1();
  }

  L1(int i) {
    this(String.valueOf(i));
  }

  @Override
  public String toString() {
    return x;
  }
}

class L2_1 extends L1 {
  private String y;

  private L2_1() {
    this(21);
    this.y = "private_L2_1_y";
  }

  L2_1(int i) {
    super(i);
    this.y = "L2_1_y";
  }

  private L2_1(String y) {
    this(21);
    this.y = y;
  }

  static L2_1 create(String y) {
    return new L2_1(y);
  }

  @Override
  public String toString() {
    return super.toString() + "_" + y;
  }
}

class L2_2 extends L1 {
  private String y;

  private L2_2(int i) {
    super(i);
    this.y = "private_L2_2_y";
  }

  L2_2(String y) {
    this(22);
    this.y = y;
  }

  static L2_1 create() {
    return new L2_1(22);
  }

  @Override
  public String toString() {
    return super.toString() + "_" + y;
  }
}

class L3_1 extends L2_1 {
  private final String z;

  private L3_1(int i) {
    this(String.valueOf(i));
  }

  private L3_1(String z) {
    super(31);
    this.z = z;
  }

  static L3_1 create(int i) {
    return new L3_1(i);
  }

  @Override
  public String toString() {
    return super.toString() + "_" + z;
  }
}

class L3_2 extends L2_2 {
  private String z;

  private L3_2() {
    super("private_L3_2_y");
    this.z = "private_L3_2_z";
  }

  private L3_2(int i) {
    super(String.valueOf(i));
    this.z = "private_L3_2_z" + "_" + i;
  }

  L3_2(String z) {
    this(32);
    this.z = z;
  }

  static L3_2 create(String z) {
    return new L3_2(z);
  }

  @Override
  public String toString() {
    return super.toString() + "_" + z;
  }
}

class CtorTestMain {
  public static void main(String[] args) {
    System.out.println(L1.create());
    System.out.println(L2_1.create("main_y"));
    System.out.println(L2_2.create());
    System.out.println(L3_1.create(41));
    System.out.println(L3_2.create("main_z"));
  }
}

public final class ConstructorRelaxationTest extends AccessRelaxationTestBase {
  private static final String INIT= "<init>";
  private static final List<Class> CLASSES =
      ImmutableList.of(L1.class, L2_1.class, L2_2.class, L3_1.class, L3_2.class);

  @Test
  public void test() throws Exception {
    Class mainClass = CtorTestMain.class;
    R8Command.Builder builder =
        loadProgramFiles(Iterables.concat(CLASSES, ImmutableList.of(mainClass)));
    builder.addProguardConfiguration(
        ImmutableList.of(
            "-keep class " + mainClass.getCanonicalName() + "{",
            "  public static void main(java.lang.String[]);",
            "}",
            "",
            "-keep class *.L* {",
            "  <init>(...);",
            "}",
            "",
            "-dontobfuscate",
            "-allowaccessmodification"
        ),
        Origin.unknown());

    AndroidApp app = ToolHelper.runR8(builder.build(), options -> {
      options.enableInlining = false;
      options.enableClassMerging = false;
    });
    compareJvmAndArt(app, mainClass);

    DexInspector dexInspector = new DexInspector(app);
    for (Class clazz : CLASSES) {
      ClassSubject classSubject = dexInspector.clazz(clazz);
      assertThat(classSubject, isPresent());
      classSubject.getDexClass().forEachMethod(m -> {
        assertTrue(!m.isInstanceInitializer() || m.isPublicMethod());
      });
    }
  }

}
