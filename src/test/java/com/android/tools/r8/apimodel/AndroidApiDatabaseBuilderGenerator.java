// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.utils.FunctionUtils.ignoreArgument;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.F_SAME1;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.POP;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.apimodel.AndroidApiVersionsXmlParser.ParsedApiClass;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.DescriptorUtils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

public class AndroidApiDatabaseBuilderGenerator extends TestBase {

  public static String generatedMainDescriptor() {
    return descriptor(AndroidApiDatabaseBuilderTemplate.class).replace("Template", "");
  }

  public static ClassReference ANDROID_API_LEVEL =
      Reference.classFromBinaryName("com/android/tools/r8/utils/AndroidApiLevel");
  public static ClassReference ANDROID_API_CLASS =
      Reference.classFromBinaryName("com/android/tools/r8/androidapi/AndroidApiClass");
  public static ClassReference TRAVERSAL_CONTINUATION =
      Reference.classFromBinaryName("com/android/tools/r8/utils/TraversalContinuation");

  /**
   * Generate the classes needed for looking up api level of references in the android.jar.
   *
   * <p>For each api class we generate a from AndroidApiDatabaseClassTemplate, extending
   * AndroidApiClass, such that all members can be traversed. Looking up the class reference
   * directly in one method will generate to much code and would probably be inefficient so we first
   * do a case on the package and then do a check on the simple name.
   *
   * <p>We therefore create a class file for each package, based on
   * AndroidApiDatabasePackageTemplate, that will do the dispatch to create a new
   * AndroidApiDatabaseClass for class in the package.
   *
   * <p>We then have a single entry-point, AndroidApiDatabaseBuilder, based on
   * AndroidApiDatabaseBuilderTemplate, that will do the dispatch to AndroidApiDatabasePackage based
   * on the package name.
   */
  public static void generate(List<ParsedApiClass> apiClasses, BiConsumer<String, byte[]> consumer)
      throws Exception {
    Map<String, List<ParsedApiClass>> packageToClassesMap = new HashMap<>();
    List<String> packages =
        apiClasses.stream()
            .map(
                apiClass -> {
                  String packageName =
                      DescriptorUtils.getPackageNameFromDescriptor(
                          apiClass.getClassReference().getDescriptor());
                  packageToClassesMap
                      .computeIfAbsent(packageName, ignoreArgument(ArrayList::new))
                      .add(apiClass);
                  return packageName;
                })
            .sorted()
            .distinct()
            .collect(Collectors.toList());

    for (ParsedApiClass apiClass : apiClasses) {
      consumer.accept(
          getApiClassDescriptor(apiClass),
          transformer(AndroidApiDatabaseClassTemplate.class)
              .setClassDescriptor(getApiClassDescriptor(apiClass))
              .addMethodTransformer(getInitTransformer(apiClass))
              .addMethodTransformer(getApiLevelTransformer(apiClass))
              .addMethodTransformer(getGetMemberCountTransformer(apiClass))
              .addMethodTransformer(getVisitFieldsTransformer(apiClass))
              .addMethodTransformer(getVisitMethodsTransformer(apiClass))
              .removeMethods(MethodPredicate.onName("placeHolderForInit"))
              .removeMethods(MethodPredicate.onName("placeHolderForGetApiLevel"))
              .removeMethods(MethodPredicate.onName("placeHolderForGetMemberCount"))
              .removeMethods(MethodPredicate.onName("placeHolderForVisitFields"))
              .removeMethods(MethodPredicate.onName("placeHolderForVisitMethods"))
              .computeMaxs()
              .transform(ClassWriter.COMPUTE_MAXS));
    }

    for (String pkg : packages) {
      consumer.accept(
          getPackageBuilderDescriptor(pkg),
          transformer(AndroidApiDatabasePackageTemplate.class)
              .setClassDescriptor(getPackageBuilderDescriptor(pkg))
              .addMethodTransformer(getBuildClassTransformer(packageToClassesMap.get(pkg)))
              .removeMethods(MethodPredicate.onName("placeHolder"))
              .computeMaxs()
              .transform(ClassWriter.COMPUTE_MAXS));
    }

    consumer.accept(
        generatedMainDescriptor(),
        transformer(AndroidApiDatabaseBuilderTemplate.class)
            .setClassDescriptor(generatedMainDescriptor())
            .addMethodTransformer(getVisitApiClassesTransformer(apiClasses))
            .addMethodTransformer(getBuildPackageTransformer(packages))
            .removeMethods(MethodPredicate.onName("placeHolderForVisitApiClasses"))
            .removeMethods(MethodPredicate.onName("placeHolderForBuildClass"))
            .computeMaxs()
            .transform(ClassWriter.COMPUTE_MAXS));
  }

  private static String getPackageBuilderDescriptor(String pkg) {
    return DescriptorUtils.javaTypeToDescriptor(
        AndroidApiDatabasePackageTemplate.class
            .getTypeName()
            .replace("Template", "ForPackage_" + pkg.replace(".", "_")));
  }

  private static String getApiClassDescriptor(ParsedApiClass apiClass) {
    return DescriptorUtils.javaTypeToDescriptor(
        AndroidApiDatabaseClassTemplate.class
            .getTypeName()
            .replace(
                "Template",
                "ForClass_" + apiClass.getClassReference().getTypeName().replace(".", "_")));
  }

  // The transformer below changes AndroidApiDatabaseClassTemplate.<init> from:
  //     super(Reference.classFromDescriptor(placeHolderForInit()));
  // into
  //     super(Reference.classFromDescriptor("<class-descriptor>"));
  private static MethodTransformer getInitTransformer(ParsedApiClass apiClass) {
    return replaceCode(
        "placeHolderForInit",
        transformer -> {
          transformer.visitLdcInsn(apiClass.getClassReference().getDescriptor());
        });
  }

  // The transformer below changes AndroidApiDatabaseClassTemplate.getApiLevel from:
  //     return placeHolderForGetApiLevel();
  // into
  //    return AndroidApiLevel.getAndroidApiLevel(<apiLevel>);
  private static MethodTransformer getApiLevelTransformer(ParsedApiClass apiClass) {
    return replaceCode(
        "placeHolderForGetApiLevel",
        transformer -> {
          transformer.visitLdcInsn(apiClass.getApiLevel().getLevel());
          transformer.visitMethodInsn(
              INVOKESTATIC,
              ANDROID_API_LEVEL.getBinaryName(),
              "getAndroidApiLevel",
              "(I)" + ANDROID_API_LEVEL.getDescriptor(),
              false);
        });
  }

  // The transformer below changes AndroidApiDatabaseClassTemplate.getMemberCount from:
  //     return placeHolderForGetMemberCount();
  // into
  //    return <memberCount>;
  private static MethodTransformer getGetMemberCountTransformer(ParsedApiClass apiClass) {
    return replaceCode(
        "placeHolderForGetMemberCount",
        transformer -> transformer.visitLdcInsn(apiClass.getTotalMemberCount()));
  }

  // The transformer below changes AndroidApiDatabaseClassTemplate.visitFields from:
  //     placeHolder();
  //     return TraversalContinuation.CONTINUE;
  // into
  //    TraversalContinuation s1 = visitField("field1", "descriptor1", apiLevel1, visitor)
  //    if (s1.shouldBreak()) {
  //       return s1;
  //    }
  //    TraversalContinuation s2 = visitField("field2", "descriptor2", apiLevel2, visitor)
  //    if (s2.shouldBreak()) {
  //       return s2;
  //    }
  //    ...
  //    return TraversalContinuation.CONTINUE;
  private static MethodTransformer getVisitFieldsTransformer(ParsedApiClass apiClass) {
    return replaceCode(
        "placeHolderForVisitFields",
        transformer -> {
          apiClass.visitFieldReferences(
              (apiLevel, references) -> {
                references.forEach(
                    reference -> {
                      transformer.visitVarInsn(ALOAD, 0);
                      transformer.visitLdcInsn(reference.getFieldName());
                      transformer.visitLdcInsn(reference.getFieldType().getDescriptor());
                      transformer.visitLdcInsn(apiLevel.getLevel());
                      transformer.visitVarInsn(ALOAD, 1);
                      transformer.visitMethodInsn(
                          INVOKEVIRTUAL,
                          ANDROID_API_CLASS.getBinaryName(),
                          "visitField",
                          "(Ljava/lang/String;"
                              + "Ljava/lang/String;"
                              + "I"
                              + "Ljava/util/function/BiFunction;)"
                              + TRAVERSAL_CONTINUATION.getDescriptor(),
                          false);
                      // Note that instead of storing the result here, we dup it on the stack.
                      transformer.visitInsn(DUP);
                      transformer.visitMethodInsn(
                          INVOKEVIRTUAL,
                          TRAVERSAL_CONTINUATION.getBinaryName(),
                          "shouldBreak",
                          "()Z",
                          false);
                      Label label = new Label();
                      transformer.visitJumpInsn(IFEQ, label);
                      transformer.visitInsn(ARETURN);
                      transformer.visitLabel(label);
                      transformer.visitFrame(
                          F_SAME1,
                          0,
                          new Object[] {},
                          1,
                          new Object[] {TRAVERSAL_CONTINUATION.getBinaryName()});
                      // The pop here is needed to remove the dupped value in the case we do not
                      // return.
                      transformer.visitInsn(POP);
                    });
              });
        });
  }

  // The transformer below changes AndroidApiDatabaseClassTemplate.visitMethods from:
  //     placeHolderForVisitMethods();
  //     return TraversalContinuation.CONTINUE;
  // into
  //    TraversalContinuation s1 = visitMethod(
  //      "method1", new String[] { "param11", ... , "param1X" }, null/return1, apiLevel1, visitor)
  //    if (s1.shouldBreak()) {
  //       return s1;
  //    }
  //    TraversalContinuation s1 = visitMethod(
  //      "method2", new String[] { "param21", ... , "param2X" }, null/return2, apiLevel2, visitor)
  //    if (s2.shouldBreak()) {
  //       return s2;
  //    }
  //    ...
  //    return TraversalContinuation.CONTINUE;
  private static MethodTransformer getVisitMethodsTransformer(ParsedApiClass apiClass) {
    return replaceCode(
        "placeHolderForVisitMethods",
        transformer -> {
          apiClass.visitMethodReferences(
              (apiLevel, references) -> {
                references.forEach(
                    reference -> {
                      transformer.visitVarInsn(ALOAD, 0);
                      transformer.visitLdcInsn(reference.getMethodName());
                      List<TypeReference> formalTypes = reference.getFormalTypes();
                      transformer.visitLdcInsn(formalTypes.size());
                      transformer.visitTypeInsn(ANEWARRAY, binaryName(String.class));
                      for (int i = 0; i < formalTypes.size(); i++) {
                        transformer.visitInsn(DUP);
                        transformer.visitLdcInsn(i);
                        transformer.visitLdcInsn(formalTypes.get(i).getDescriptor());
                        transformer.visitInsn(AASTORE);
                      }
                      if (reference.getReturnType() != null) {
                        transformer.visitLdcInsn(reference.getReturnType().getDescriptor());
                      } else {
                        transformer.visitInsn(ACONST_NULL);
                      }
                      transformer.visitLdcInsn(apiLevel.getLevel());
                      transformer.visitVarInsn(ALOAD, 1);
                      transformer.visitMethodInsn(
                          INVOKEVIRTUAL,
                          ANDROID_API_CLASS.getBinaryName(),
                          "visitMethod",
                          "(Ljava/lang/String;"
                              + "[Ljava/lang/String;Ljava/lang/String;"
                              + "I"
                              + "Ljava/util/function/BiFunction;)"
                              + TRAVERSAL_CONTINUATION.getDescriptor(),
                          false);
                      // Note that instead of storing the result here, we dup it on the stack.
                      transformer.visitInsn(DUP);
                      transformer.visitMethodInsn(
                          INVOKEVIRTUAL,
                          TRAVERSAL_CONTINUATION.getBinaryName(),
                          "shouldBreak",
                          "()Z",
                          false);
                      Label label = new Label();
                      transformer.visitJumpInsn(IFEQ, label);
                      transformer.visitInsn(ARETURN);
                      transformer.visitLabel(label);
                      transformer.visitFrame(
                          F_SAME1,
                          0,
                          new Object[] {},
                          1,
                          new Object[] {TRAVERSAL_CONTINUATION.getBinaryName()});
                      // The pop here is needed to remove the dupped value in the case we do not
                      // return.
                      transformer.visitInsn(POP);
                    });
              });
        });
  }

  // The transformer below changes AndroidApiDatabasePackageTemplate.buildClass from:
  //    placeHolder();
  //    return null;
  // into
  //    if ("<simple_class1>".equals(className)) {
  //      return new AndroidApiClassForClass_class_name1();
  //    }
  //    if ("<simple_class2>".equals(className)) {
  //      return new AndroidApiClassForClass_class_name2();
  //    }
  //    ...
  //    return null;
  private static MethodTransformer getBuildClassTransformer(List<ParsedApiClass> classesForPackage)
      throws NoSuchMethodException {
    Method equals = Object.class.getMethod("equals", Object.class);
    return replaceCode(
        "placeHolder",
        transformer -> {
          classesForPackage.forEach(
              apiClass -> {
                transformer.visitLdcInsn(
                    DescriptorUtils.getSimpleClassNameFromDescriptor(
                        apiClass.getClassReference().getDescriptor()));
                transformer.visitVarInsn(ALOAD, 0);
                transformer.visitMethodInsn(
                    INVOKEVIRTUAL,
                    binaryName(String.class),
                    equals.getName(),
                    methodDescriptor(equals),
                    false);
                Label label = new Label();
                transformer.visitJumpInsn(IFEQ, label);
                String binaryName =
                    DescriptorUtils.getBinaryNameFromDescriptor(getApiClassDescriptor(apiClass));
                transformer.visitTypeInsn(NEW, binaryName);
                transformer.visitInsn(DUP);
                transformer.visitMethodInsn(INVOKESPECIAL, binaryName, "<init>", "()V", false);
                transformer.visitInsn(ARETURN);
                transformer.visitLabel(label);
                transformer.visitFrame(Opcodes.F_SAME, 0, new Object[] {}, 0, new Object[0]);
              });
        });
  }

  // The transformer below changes AndroidApiDatabaseBuilderTemplate.buildClass from:
  //    String descriptor = classReference.getDescriptor();
  //    String packageName = DescriptorUtils.getPackageNameFromDescriptor(descriptor);
  //    String simpleClassName = DescriptorUtils.getSimpleClassNameFromDescriptor(descriptor);
  //    placeHolderForBuildClass();
  //    return null;
  // into
  //    String descriptor = classReference.getDescriptor();
  //    String packageName = DescriptorUtils.getPackageNameFromDescriptor(descriptor);
  //    String simpleClassName = DescriptorUtils.getSimpleClassNameFromDescriptor(descriptor);
  //    if ("<package_name1>".equals(packageName)) {
  //      return AndroidApiClassForPackage_package_name1(simpleClassName);
  //    }
  //    if ("<package_name2>".equals(simpleClassName)) {
  //      return AndroidApiClassForPackage_package_name2(simpleClassName);
  //    }
  //    ...
  //    return null;
  private static MethodTransformer getBuildPackageTransformer(List<String> packages)
      throws NoSuchMethodException {
    Method equals = String.class.getMethod("equals", Object.class);
    Method buildClass =
        AndroidApiDatabasePackageTemplate.class.getMethod("buildClass", String.class);
    return replaceCode(
        "placeHolderForBuildClass",
        transformer -> {
          packages.forEach(
              pkg -> {
                transformer.visitLdcInsn(pkg);
                transformer.visitVarInsn(ALOAD, 2);
                transformer.visitMethodInsn(
                    INVOKEVIRTUAL,
                    binaryName(String.class),
                    equals.getName(),
                    methodDescriptor(equals),
                    false);
                Label label = new Label();
                transformer.visitJumpInsn(IFEQ, label);
                transformer.visitVarInsn(ALOAD, 3);
                transformer.visitMethodInsn(
                    INVOKESTATIC,
                    DescriptorUtils.getBinaryNameFromDescriptor(getPackageBuilderDescriptor(pkg)),
                    buildClass.getName(),
                    methodDescriptor(buildClass),
                    false);
                transformer.visitInsn(ARETURN);
                transformer.visitLabel(label);
                transformer.visitFrame(
                    Opcodes.F_FULL,
                    4,
                    new Object[] {
                      binaryName(ClassReference.class),
                      binaryName(String.class),
                      binaryName(String.class),
                      binaryName(String.class)
                    },
                    0,
                    new Object[0]);
              });
        });
  }

  // The transformer below changes AndroidApiDatabaseBuilderTemplate.buildClass from:
  //    placeHolderForVisitApiClasses();
  // into
  //    classDescriptorConsumer.accept("<descriptor_class_1>");
  //    classDescriptorConsumer.accept("<descriptor_class_2>");
  //    ...
  //    return null;
  private static MethodTransformer getVisitApiClassesTransformer(List<ParsedApiClass> apiClasses) {
    return replaceCode(
        "placeHolderForVisitApiClasses",
        transformer -> {
          apiClasses.forEach(
              apiClass -> {
                transformer.visitVarInsn(ALOAD, 0);
                transformer.visitLdcInsn(apiClass.getClassReference().getDescriptor());
                transformer.visitMethodInsn(
                    INVOKEINTERFACE,
                    binaryName(Consumer.class),
                    "accept",
                    "(Ljava/lang/Object;)V",
                    true);
              });
        });
  }

  private static MethodTransformer replaceCode(
      String placeholderName, Consumer<MethodTransformer> consumer) {
    return new MethodTransformer() {

      @Override
      public void visitMaxs(int maxStack, int maxLocals) {
        super.visitMaxs(-1, maxLocals);
      }

      @Override
      public void visitMethodInsn(
          int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (name.equals(placeholderName)) {
          consumer.accept(this);
        } else {
          super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
      }
    };
  }
}
