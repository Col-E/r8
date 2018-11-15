// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.codeinspector.CfInstructionSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.DexInstructionSubject;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundFieldSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.junit.Ignore;
import org.junit.Test;

// A comparator that inputs two apks you want to investigate, along with mappings if available.
// This will help you walk through apks in a fancy IDE debugging mode.
public class AppComparator extends TestBase {
  // Update the following path strings to point to apks you are inspecting.
  private static final String PATH_0 = "/path/to/workspace";
  private static final String PATH_1 = PATH_0 + "/R8.apk";
  private static final String PATH_2 = PATH_0 + "/proguard.apk";
  private static final String MAP_1 = PATH_0 + "/mapping-R8.txt";
  private static final String MAP_2 = PATH_0 + "/mapping-proguard.txt";

  private static final boolean allowMissingClassInApp2 = true;

  private AndroidApp loadApp(String path) {
    AndroidApp.Builder builder = AndroidApp.builder();
    builder.addProgramFile(Paths.get(path));
    return builder.build();
  }

  @Ignore("Comment out this to run locally.")
  @Test
  public void identicalTest_specificMethods() throws Exception {
    AndroidApp app1 = loadApp(PATH_1);
    AndroidApp app2 = loadApp(PATH_2);

    CodeInspector inspect1 = new CodeInspector(app1, Paths.get(MAP_1));
    CodeInspector inspect2 = new CodeInspector(app2, Paths.get(MAP_2));

    // Define your own tester to pick methods to inspect.
    Predicate<DexEncodedMethod> methodTester = encodedMethod -> {
      return encodedMethod.method.name.toString().equals("run")
          && encodedMethod.method.getArity() == 0;
    };

    inspect1.forAllClasses(clazz1 -> {
      clazz1.forAllMethods(method1 -> {
        if (methodTester.test(method1.getMethod())) {
          ClassSubject clazz2 = inspect2.clazz(clazz1.getOriginalName());
          if (!clazz2.isPresent()) {
            String classNotFound =
                String.format("Class %s not found in app2", clazz1.getOriginalName());
            if (allowMissingClassInApp2) {
              System.out.println(classNotFound);
              return;
            }
            assertThat(classNotFound, clazz2, isPresent());
          }
          MethodSubject method2 = clazz2.method(method1.getOriginalSignature());
          if (!method2.isPresent()) {
            method2 = clazz2.method(method1.getFinalSignature());
          }
          if (!method2.isPresent()) {
            assertThat(String.format("Method %s not found in app2", method1.getFinalSignature()),
                method2, isPresent());
          }
          if (method1.getMethod().shouldNotHaveCode()) {
            assertTrue(method2.getMethod().shouldNotHaveCode());
            return;
          }
          if (!identicalCode(method1, method2)) {
            System.out.println("Found different method body: ");
            System.out.println(method1.getMethod().codeToString());
            System.out.println(method2.getMethod().codeToString());
          }
        }
      });
    });
  }

  @Ignore("Comment out this to run locally.")
  @Test
  public void identicalTest_wholeApp() throws Exception {
    AndroidApp app1 = loadApp(PATH_1);
    AndroidApp app2 = loadApp(PATH_2);

    CodeInspector inspect1 = new CodeInspector(app1);
    CodeInspector inspect2 = new CodeInspector(app2);

    class Pair<T> {
      private T first;
      private T second;

      private void set(boolean selectFirst, T value) {
        if (selectFirst) {
          first = value;
        } else {
          second = value;
        }
      }
    }

    // Collect all classes from both inspectors, indexed by finalDescriptor.
    Map<String, Pair<FoundClassSubject>> allClasses = new HashMap<>();

    BiConsumer<CodeInspector, Boolean> collectClasses = (inspector, selectFirst) -> {
      inspector.forAllClasses(
          clazz -> {
            String finalDescriptor = clazz.getFinalDescriptor();
            allClasses.compute(
                finalDescriptor,
                (k, v) -> {
                  if (v == null) {
                    v = new Pair<>();
                  }
                  v.set(selectFirst, clazz);
                  return v;
                });
          });
    };

    collectClasses.accept(inspect1, true);
    collectClasses.accept(inspect2, false);

    for (Map.Entry<String, Pair<FoundClassSubject>> classEntry : allClasses.entrySet()) {
      String className = classEntry.getKey();
      FoundClassSubject class1 = classEntry.getValue().first;
      FoundClassSubject class2 = classEntry.getValue().second;

      assert class1 != null || class2 != null;

      assertNotNull(String.format("Class %s is missing from the first app.", className), class1);
      assertNotNull(String.format("Class %s is missing from the second app.", className), class2);

      // Collect all fields for this class from both apps.
      Map<FieldSignature, Pair<FoundFieldSubject>> allFields = new HashMap<>();

      BiConsumer<FoundClassSubject, Boolean> collectFields = (classSubject, selectFirst) -> {
        classSubject.forAllFields(
            f -> {
              FieldSignature fs = f.getFinalSignature();
              allFields.compute(
                  fs,
                  (k, v) -> {
                    if (v == null) {
                      v = new Pair<>();
                    }
                    v.set(selectFirst, f);
                    return v;
                  });
            });
      };

      collectFields.accept(class1, true);
      collectFields.accept(class2, false);

      for (Map.Entry<FieldSignature, Pair<FoundFieldSubject>> fieldEntry : allFields.entrySet()) {
        FieldSignature signature = fieldEntry.getKey();
        FoundFieldSubject field1 = fieldEntry.getValue().first;
        FoundFieldSubject field2 = fieldEntry.getValue().second;
        assert field1 != null || field2 != null;

        assertNotNull(
            String.format(
                "Field %s of class %s is missing from the first app.", signature, className),
            field1);
        assertNotNull(
            String.format(
                "Field %s of class %s is missing from the second app.", signature, className),
            field2);
      }

      // Collect all methods for this class from both apps.
      Map<MethodSignature, Pair<FoundMethodSubject>> allMethods = new HashMap<>();

      BiConsumer<FoundClassSubject, Boolean> collectMethods = (classSubject, selectFirst) -> {
        classSubject.forAllMethods(
            m -> {
              MethodSignature fs = m.getFinalSignature();
              allMethods.compute(
                  fs,
                  (k, v) -> {
                    if (v == null) {
                      v = new Pair<>();
                    }
                    v.set(selectFirst, m);
                    return v;
                  });
            });
      };

      collectMethods.accept(class1, true);
      collectMethods.accept(class2, false);

      for (Map.Entry<MethodSignature, Pair<FoundMethodSubject>> methodEntry :
          allMethods.entrySet()) {
        MethodSignature signature = methodEntry.getKey();
        FoundMethodSubject method1 = methodEntry.getValue().first;
        FoundMethodSubject method2 = methodEntry.getValue().second;
        assert method1 != null || method2 != null;

        assertNotNull(
            String.format(
                "Method %s of class %s is missing from the first app.", signature, className),
            method1);
        assertNotNull(
            String.format(
                "Method %s of class %s is missing from the second app.", signature, className),
            method2);

        if (method1.getMethod().shouldNotHaveCode()) {
          assertTrue(method2.getMethod().shouldNotHaveCode());
          continue;
        }

        // Even compare every single instruction. Adjust for your own purpose or comment out.
        assertTrue(identicalCode(method1, method2));
      }
    }
  }

  private boolean identicalCode(MethodSubject method1, MethodSubject method2) {
    Iterator<InstructionSubject> it1 = method1.iterateInstructions();
    Iterator<InstructionSubject> it2 = method2.iterateInstructions();
    while (it1.hasNext()) {
      assertTrue(it2.hasNext());
      InstructionSubject instr1 = it1.next();
      InstructionSubject instr2 = it2.next();
      assertEquals(
          instr1 instanceof DexInstructionSubject,
          instr2 instanceof DexInstructionSubject);
      if (instr1 instanceof DexInstructionSubject) {
        assert instr2 instanceof DexInstructionSubject;
        DexInstructionSubject dexInstr1 = (DexInstructionSubject) instr1;
        DexInstructionSubject dexInstr2 = (DexInstructionSubject) instr2;
        if (!dexInstr1.equals(dexInstr2)) {
          return false;
        }
      } else {
        assert instr1 instanceof CfInstructionSubject;
        assert instr2 instanceof CfInstructionSubject;
        CfInstructionSubject cfInstr1 = (CfInstructionSubject) instr1;
        CfInstructionSubject cfInstr2 = (CfInstructionSubject) instr2;
        if (!cfInstr1.equals(cfInstr2)) {
          return false;
        }
      }
    }
    return true;
  }
}
