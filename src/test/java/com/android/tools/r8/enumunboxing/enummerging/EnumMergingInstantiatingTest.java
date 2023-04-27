// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing.enummerging;

import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoReturnTypeStrengthening;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.enumunboxing.EnumUnboxingTestBase;
import com.android.tools.r8.utils.DescriptorUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumMergingInstantiatingTest extends EnumUnboxingTestBase {

  private static Collection<byte[]> PROGRAM_CLASSES_DATA;

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  @BeforeClass
  public static void setup() throws IOException {
    PROGRAM_CLASSES_DATA = tranformedInputs();
  }

  public EnumMergingInstantiatingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_CLASSES_DATA)
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(InstantiatingEnum.class))
        .enableInliningAnnotations()
        .enableAlwaysInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoReturnTypeStrengtheningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("AC/DC", "AC/DC", "AC/DC");
  }

  private static String getNewDescriptorForName(String name, String descriptor) {
    String descr = DescriptorUtils.javaTypeToDescriptor(InstantiatingEnum.class.getTypeName());
    String prefix = descr.substring(0, descr.length() - 1);
    if (name.equals("A")) {
      return prefix + "$1;";
    }
    if (name.equals("D")) {
      return prefix + "$2;";
    }
    return descriptor;
  }

  public static Collection<byte[]> tranformedInputs() throws IOException {
    Collection<Path> classFilesForInnerClasses =
        ToolHelper.getClassFilesForInnerClasses(EnumMergingInstantiatingTest.class);
    ArrayList<byte[]> bytes = new ArrayList<>();
    for (Path classFilesForInnerClass : classFilesForInnerClasses) {
      bytes.add(transform(classFilesForInnerClass));
    }
    return bytes;
  }

  public static byte[] transform(Path path) throws IOException {
    return transformer(path, null)
        .changeFieldType(
            name -> name.equals("A") || name.equals("D"),
            EnumMergingInstantiatingTest::getNewDescriptorForName)
        .transform();
  }

  public static class C {

    @AlwaysInline
    public static C dispatch(InstantiatingEnum theEnum) {
      // Only after inlining can the invoke be respecialized to each of the subtype.
      return theEnum.newEntry();
    }
  }

  @NoHorizontalClassMerging
  public static class AC extends C {

    @Override
    public String toString() {
      return "AC";
    }
  }

  @NoHorizontalClassMerging
  public static class DC extends C {

    @Override
    public String toString() {
      return "DC";
    }
  }

  enum InstantiatingEnum {
    A {
      @NeverInline
      @Override
      public C newEntry() {
        return new AC();
      }

      @NeverInline
      @Override
      public C newEntryThroughDispatch() {
        return C.dispatch(this);
      }
    },
    D {
      @NeverInline
      @Override
      public C newEntry() {
        return new DC();
      }

      @NeverInline
      @Override
      public C newEntryThroughDispatch() {
        return C.dispatch(this);
      }
    };

    @NeverInline
    public abstract C newEntry();

    @NeverInline
    public abstract C newEntryThroughDispatch();
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(getAC() + "/" + getDC());
      System.out.println(getC(InstantiatingEnum.A) + "/" + getC(InstantiatingEnum.D));
      System.out.println(
          InstantiatingEnum.A.newEntryThroughDispatch()
              + "/"
              + InstantiatingEnum.D.newEntryThroughDispatch());
    }

    @NeverInline
    @NoReturnTypeStrengthening
    private static C getAC() {
      return InstantiatingEnum.A.newEntry();
    }

    @NeverInline
    @NoReturnTypeStrengthening
    private static C getDC() {
      return InstantiatingEnum.D.newEntry();
    }

    @NeverInline
    private static C getC(InstantiatingEnum e) {
      return e.newEntry();
    }
  }
}
