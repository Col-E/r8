// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.ToolHelper.DEFAULT_PROGUARD_MAP_FILE;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase.MinifyMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.FieldAccessInstructionSubject;
import com.android.tools.r8.utils.DexInspector.FieldSubject;
import com.android.tools.r8.utils.DexInspector.FoundFieldSubject;
import com.android.tools.r8.utils.DexInspector.FoundMethodSubject;
import com.android.tools.r8.utils.DexInspector.InstructionSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.OutputMode;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShakingTest {

  private static final String ANDROID_JAR = ToolHelper.getDefaultAndroidJar();
  private static final List<Path> JAR_LIBRARIES = ListUtils.map(ImmutableList
      .of(ANDROID_JAR, ToolHelper.EXAMPLES_BUILD_DIR + "shakinglib.jar"), Paths::get);
  private static final String EMPTY_FLAGS = "src/test/proguard/valid/empty.flags";
  private static final Set<String> IGNORED_FLAGS = ImmutableSet.of(
      "examples/minification:conflict-mapping.txt",
      "examples/minification:keep-rules-apply-conflict-mapping.txt"
  );
  private static final Set<String> IGNORED = ImmutableSet.of(
      // there's no point in running those without obfuscation
      "examples/shaking1:keep-rules-repackaging.txt:DEX:NONE",
      "examples/shaking1:keep-rules-repackaging.txt:JAR:NONE",
      "examples/shaking16:keep-rules-1.txt:DEX:NONE",
      "examples/shaking16:keep-rules-1.txt:JAR:NONE",
      "examples/shaking16:keep-rules-2.txt:DEX:NONE",
      "examples/shaking16:keep-rules-2.txt:JAR:NONE",
      "examples/shaking15:keep-rules.txt:DEX:NONE",
      "examples/shaking15:keep-rules.txt:JAR:NONE",
      "examples/minifygeneric:keep-rules.txt:DEX:NONE",
      "examples/minifygeneric:keep-rules.txt:JAR:NONE",
      "examples/minifygenericwithinner:keep-rules.txt:DEX:NONE",
      "examples/minifygenericwithinner:keep-rules.txt:JAR:NONE",
      // No prebuild DEX files for AndroidN
      "examplesAndroidN/shaking:keep-rules.txt:DEX:NONE",
      "examplesAndroidN/shaking:keep-rules.txt:DEX:JAVA",
      "examplesAndroidN/shaking:keep-rules.txt:DEX:AGGRESSIVE"
  );

  private static Set<String> SKIPPED = Collections.emptySet();

  private final MinifyMode minify;

  private enum Frontend {
    DEX, JAR

  }
  private final Frontend kind;
  private final String originalDex;
  private final String programFile;
  private final String mainClass;
  private final List<String> keepRulesFiles;
  private final Consumer<DexInspector> inspection;
  private final BiConsumer<String, String> outputComparator;

  private BiConsumer<DexInspector, DexInspector> dexComparator;

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  public TreeShakingTest(String test, Frontend kind, String mainClass, List<String> keepRulesFiles,
      MinifyMode minify, Consumer<DexInspector> inspection,
      BiConsumer<String, String> outputComparator,
      BiConsumer<DexInspector, DexInspector> dexComparator) {
    this.kind = kind;
    originalDex = ToolHelper.TESTS_BUILD_DIR + test + "/classes.dex";
    if (kind == Frontend.DEX) {
      this.programFile = originalDex;
    } else {
      this.programFile = ToolHelper.TESTS_BUILD_DIR + test + ".jar";
    }
    this.mainClass = mainClass;
    this.keepRulesFiles = keepRulesFiles;
    this.inspection = inspection;
    this.minify = minify;
    this.outputComparator = outputComparator;
    this.dexComparator = dexComparator;
  }

  @Before
  public void generateTreeShakedVersion() throws Exception {
    // Generate R8 processed version without library option.
    Path out = temp.getRoot().toPath();
    boolean inline = programFile.contains("inlining");

    R8Command command =
        ToolHelper.addProguardConfigurationConsumer(
                R8Command.builder(),
                pgConfig -> {
                  pgConfig.setPrintMapping(true);
                  pgConfig.setPrintMappingFile(out.resolve(ToolHelper.DEFAULT_PROGUARD_MAP_FILE));
                  pgConfig.setOverloadAggressively(minify == MinifyMode.AGGRESSIVE);
                  pgConfig.setObfuscating(minify.isMinify());
                })
            .setOutput(out, OutputMode.DexIndexed)
            .addProgramFiles(Paths.get(programFile))
            .addProguardConfigurationFiles(ListUtils.map(keepRulesFiles, Paths::get))
            .addLibraryFiles(JAR_LIBRARIES)
            .build();
    ToolHelper.runR8(command, options -> {
      options.inlineAccessors = inline;
    });
  }

  public static void shaking1HasNoClassUnused(DexInspector inspector) {
    Assert.assertFalse(inspector.clazz("shaking1.Unused").isPresent());
    ClassSubject used = inspector.clazz("shaking1.Used");
    Assert.assertTrue(used.isPresent());
    Assert.assertTrue(
        used.method("java.lang.String", "aMethodThatIsNotUsedButKept", Collections.emptyList())
            .isPresent());
    Assert.assertTrue(used.field("int", "aStaticFieldThatIsNotUsedButKept").isPresent());
    // Rewriting of <clinit> moves the initialization of aStaticFieldThatIsNotUsedButKept
    // from <clinit> code into statics value section of the dex file.
    Assert.assertFalse(used.clinit().isPresent());
  }

  public static void shaking1IsCorrectlyRepackaged(DexInspector inspector) {
    inspector.forAllClasses(clazz -> {
      String descriptor = clazz.getFinalDescriptor();
      Assert.assertTrue(descriptor,
          DescriptorUtils.getSimpleClassNameFromDescriptor(descriptor).equals("Shaking")
              || DescriptorUtils.getPackageNameFromDescriptor(descriptor).equals("repackaged"));
    });
  }

  private static void shaking2SuperClassIsAbstract(DexInspector inspector) {
    ClassSubject clazz = inspector.clazz("shaking2.SuperClass");
    Assert.assertTrue(clazz.isAbstract());
    Assert.assertTrue(clazz.method("void", "virtualMethod", Collections.emptyList()).isAbstract());
    Assert.assertTrue(clazz.method("void", "virtualMethod2", ImmutableList
        .of("int", "int", "int", "int", "int", "int", "int", "int")).isAbstract());
  }

  public static void shaking3HasNoClassB(DexInspector inspector) {
    Assert.assertFalse(inspector.clazz("shaking3.B").isPresent());
    ClassSubject classA = inspector.clazz("shaking3.A");
    Assert.assertTrue(classA.isPresent());
    Assert.assertFalse(classA.method("void", "unused", ImmutableList.of()).isPresent());
  }

  public static void shaking3HasNoPrivateClass(DexInspector inspector) {
    Assert.assertTrue(inspector.clazz("shaking3.B").isPresent());
    Assert.assertFalse(inspector.clazz("shaking3.AnAbstractClass").isPresent());
  }

  private static void shaking5Inspection(DexInspector inspector) {
    Assert.assertFalse(inspector.clazz("shaking5.Superclass")
        .method("void", "virtualMethod", Collections.emptyList()).isPresent());
  }

  private static void hasNoPrivateMethods(DexInspector inspector) {
    inspector.forAllClasses(clazz -> clazz.forAllMethods(
        method -> Assert.assertTrue(!method.getMethod().accessFlags.isPrivate())));
  }

  private static void hasNoPublicMethodsButPrivate(DexInspector inspector) {
    inspector.forAllClasses(clazz -> clazz.forAllMethods(method -> {
      if (!method.isStatic() && !method.isFinal()) {
        Assert.assertTrue(!method.getMethod().accessFlags.isPublic());
      }
    }));
    Assert.assertTrue(inspector.clazz("shaking6.Superclass")
        .method("void", "justAMethod", Collections.emptyList()).isPresent());
  }

  private static void hasNoPrivateJustAMethod(DexInspector inspector) {
    Assert.assertFalse(
        inspector.clazz("shaking6.Superclass")
            .method("void", "justAMethod", Collections.emptyList())
            .isPresent());
    ClassSubject subclass = inspector.clazz("shaking6.Subclass");
    Assert.assertTrue(subclass.isPresent());
    Assert.assertTrue(
        subclass.method("void", "justAMethod", Collections.emptyList())
            .isPresent());
    Assert.assertTrue(
        subclass.method("void", "justAMethod", Collections.singletonList("int"))
            .isPresent());
    Assert.assertTrue(
        subclass.method("void", "justAMethod", Collections.singletonList("boolean"))
            .isPresent());
    Assert.assertFalse(
        subclass.method("int", "justAMethod", Collections.singletonList("double"))
            .isPresent());
  }

  private static void hasOnlyIntJustAMethod(DexInspector inspector) {
    Assert.assertFalse(
        inspector.clazz("shaking6.Superclass")
            .method("void", "justAMethod", Collections.emptyList())
            .isPresent());
    ClassSubject subclass = inspector.clazz("shaking6.Subclass");
    Assert.assertTrue(subclass.isPresent());
    Assert.assertFalse(
        subclass.method("void", "justAMethod", Collections.emptyList())
            .isPresent());
    Assert.assertTrue(
        subclass.method("void", "justAMethod", Collections.singletonList("int"))
            .isPresent());
    Assert.assertFalse(
        subclass.method("void", "justAMethod", Collections.singletonList("boolean"))
            .isPresent());
    Assert.assertFalse(
        subclass.method("int", "justAMethod", Collections.singletonList("double"))
            .isPresent());
  }

  private static void shaking7HasOnlyPublicFields(DexInspector inspector) {
    inspector.forAllClasses(clazz -> {
      clazz.forAllFields(field -> {
        Assert.assertTrue(field.getField().accessFlags.isPublic());
      });
    });
    ClassSubject subclass = inspector.clazz("shaking7.Subclass");
    Assert.assertTrue(subclass.field("int", "theIntField").isPresent());
    Assert.assertTrue(subclass.field("double", "theDoubleField").isPresent());
    Assert.assertTrue(inspector.clazz("shaking7.Superclass")
        .field("double", "theDoubleField").isPresent());
    Assert.assertTrue(inspector.clazz("shaking7.Liar").field("int", "theDoubleField").isPresent());
  }

  private static void shaking7HasOnlyDoubleFields(DexInspector inspector) {
    inspector.forAllClasses(clazz -> {
      clazz.forAllFields(field -> {
        Assert.assertTrue(field.type().is("double"));
      });
    });
    Assert.assertTrue(
        inspector.clazz("shaking7.Subclass").field("double", "theDoubleField").isPresent());
    Assert.assertTrue(
        inspector.clazz("shaking7.Superclass").field("double", "theDoubleField").isPresent());
    Assert.assertFalse(
        inspector.clazz("shaking7.Liar").field("int", "theDoubleField").isPresent());
  }

  private static void shaking7HasOnlyPublicFieldsNamedTheDoubleField(DexInspector inspector) {
    inspector.forAllClasses(clazz -> {
      clazz.forAllFields(field -> {
        Assert.assertTrue(field.getField().accessFlags.isPublic());
      });
    });
    ClassSubject subclass = inspector.clazz("shaking7.Subclass");
    Assert.assertFalse(subclass.field("int", "theIntField").isPresent());
    Assert.assertTrue(subclass.field("double", "theDoubleField").isPresent());
    Assert.assertTrue(inspector.clazz("shaking7.Superclass")
        .field("double", "theDoubleField").isPresent());
    Assert.assertTrue(inspector.clazz("shaking7.Liar").field("int", "theDoubleField").isPresent());
  }

  private static void shaking7HasOnlyPublicFieldsNamedTheIntField(DexInspector inspector) {
    inspector.forAllClasses(clazz -> {
      clazz.forAllFields(field -> {
        Assert.assertTrue(field.getField().accessFlags.isPublic());
      });
    });
    ClassSubject subclass = inspector.clazz("shaking7.Subclass");
    Assert.assertTrue(subclass.field("int", "theIntField").isPresent());
    Assert.assertFalse(subclass.field("double", "theDoubleField").isPresent());
    Assert.assertFalse(inspector.clazz("shaking7.Superclass")
        .field("double", "theDoubleField").isPresent());
    ClassSubject liar = inspector.clazz("shaking7.Liar");
    Assert.assertFalse(liar.field("int", "theDoubleField").isPresent());
    Assert.assertTrue(liar.field("double", "theIntField").isPresent());
  }

  private static void shaking8ThingClassIsAbstractAndEmpty(DexInspector inspector) {
    ClassSubject clazz = inspector.clazz("shaking8.Thing");
    Assert.assertTrue(clazz.isAbstract());
    clazz.forAllMethods((method) -> Assert.fail());
    clazz = inspector.clazz("shaking8.YetAnotherThing");
    Assert.assertTrue(clazz.isAbstract());
    clazz.forAllMethods((method) -> Assert.fail());
  }

  private static void shaking9OnlySuperMethodsKept(DexInspector inspector) {
    ClassSubject superclass = inspector.clazz("shaking9.Superclass");
    Assert.assertTrue(superclass.isAbstract());
    Assert.assertTrue(superclass.method("void", "aMethod", ImmutableList.of()).isPresent());
    ClassSubject subclass = inspector.clazz("shaking9.Subclass");
    Assert.assertFalse(subclass.method("void", "aMethod", ImmutableList.of()).isPresent());
  }

  private static void shaking11OnlyOneClassKept(DexInspector dexInspector) {
    Assert.assertFalse(dexInspector.clazz("shaking11.Subclass").isPresent());
    Assert.assertTrue(dexInspector.clazz("shaking11.SubclassWithMethod").isPresent());
  }

  private static void shaking11BothMethodsKept(DexInspector dexInspector) {
    Assert.assertFalse(
        dexInspector.clazz("shaking11.Subclass").method("void", "aMethod", Collections.emptyList())
            .isPresent());
    Assert.assertTrue(
        dexInspector.clazz("shaking11.SuperClass")
            .method("void", "aMethod", Collections.emptyList())
            .isPresent());
    Assert.assertTrue(dexInspector.clazz("shaking11.SubclassWithMethod")
        .method("void", "aMethod", Collections.emptyList()).isPresent());
  }

  private static void shaking12OnlyInstantiatedClassesHaveConstructors(DexInspector inspector) {
    ClassSubject animalClass = inspector.clazz("shaking12.AnimalClass");
    Assert.assertTrue(animalClass.isPresent());
    Assert.assertFalse(
        animalClass.method("void", "<init>", Collections.emptyList()).isPresent());
    Assert.assertTrue(inspector.clazz("shaking12.MetaphorClass").isAbstract());
    ClassSubject peopleClass = inspector.clazz("shaking12.PeopleClass");
    Assert.assertTrue((peopleClass.isPresent() && !peopleClass.isAbstract()));
    Assert.assertTrue(
        peopleClass.method("void", "<init>", Collections.emptyList()).isPresent());
    ClassSubject thingClass = inspector.clazz("shaking12.ThingClass");
    Assert.assertTrue((thingClass.isPresent() && !thingClass.isAbstract()));
    Assert.assertTrue(
        thingClass.method("void", "<init>", Collections.emptyList()).isPresent());
  }

  private static void shaking13EnsureFieldWritesCorrect(DexInspector inspector) {
    ClassSubject mainClass = inspector.clazz("shaking13.Shaking");
    MethodSubject testMethod = mainClass.method("void", "fieldTest", Collections.emptyList());
    Assert.assertTrue(testMethod.isPresent());
    Iterator<FieldAccessInstructionSubject> iterator =
        testMethod.iterateInstructions(InstructionSubject::isFieldAccess);
    Assert.assertTrue(iterator.hasNext() && iterator.next().holder().is("shakinglib.LibraryClass"));
    Assert.assertTrue(iterator.hasNext() && iterator.next().holder().is("shakinglib.LibraryClass"));
    Assert.assertFalse(iterator.hasNext());
  }

  private static void shaking14EnsureRightStaticMethodsLive(DexInspector inspector) {
    ClassSubject superclass = inspector.clazz("shaking14.Superclass");
    Assert.assertFalse(superclass.method("int", "aMethod", ImmutableList.of("int")).isPresent());
    Assert.assertFalse(
        superclass.method("double", "anotherMethod", ImmutableList.of("double")).isPresent());
    ClassSubject subclass = inspector.clazz("shaking14.Subclass");
    Assert.assertTrue(subclass.method("int", "aMethod", ImmutableList.of("int")).isPresent());
    Assert.assertTrue(
        subclass.method("double", "anotherMethod", ImmutableList.of("double")).isPresent());
  }

  private static void simpleproto1UnusedFieldIsGone(DexInspector inspector) {
    ClassSubject protoClass = inspector.clazz("simpleproto1.GeneratedSimpleProto$Simple");
    Assert.assertTrue(protoClass.isPresent());
    Assert.assertFalse(protoClass.field("boolean", "other_").isPresent());
  }

  private static void simpleproto2UnusedFieldsAreGone(DexInspector inspector) {
    ClassSubject protoClass = inspector.clazz("simpleproto2.GeneratedSimpleProto$Simple");
    Assert.assertTrue(protoClass.isPresent());
    Assert.assertFalse(protoClass.field("int", "id_").isPresent());
    Assert.assertFalse(protoClass.field("float", "hasMe_").isPresent());
    Assert.assertFalse(protoClass.field("int", "other_").isPresent());
  }

  private static void nestedproto1UnusedFieldsAreGone(DexInspector inspector) {
    ClassSubject protoClass = inspector.clazz("nestedproto1.GeneratedNestedProto$Outer");
    Assert.assertTrue(protoClass.isPresent());
    Assert.assertFalse(protoClass.field("int", "id_").isPresent());
    Assert.assertTrue(
        protoClass.field("nestedproto1.GeneratedNestedProto$NestedOne", "inner_").isPresent());
    Assert.assertFalse(
        protoClass.field("nestedproto1.GeneratedNestedProto$NestedTwo", "inner2_").isPresent());
    ClassSubject nestedOne = inspector.clazz("nestedproto1.GeneratedNestedProto$NestedOne");
    Assert.assertTrue(nestedOne.isPresent());
    Assert.assertTrue(nestedOne.field("java.lang.String", "other_").isPresent());
    Assert.assertFalse(nestedOne.field("int", "id_").isPresent());
    Assert.assertFalse(inspector.clazz("nestedproto1.GeneratedNestedProto$NestedTwo").isPresent());
  }


  private static void nestedproto2UnusedFieldsAreGone(DexInspector inspector) {
    ClassSubject protoClass = inspector.clazz("nestedproto2.GeneratedNestedProto$Outer");
    Assert.assertTrue(protoClass.isPresent());
    Assert.assertTrue(protoClass.field("int", "id_").isPresent());
    Assert.assertFalse(
        protoClass.field("nestedproto2.GeneratedNestedProto$NestedOne", "inner_").isPresent());
    Assert.assertFalse(
        protoClass.field("nestedproto2.GeneratedNestedProto$NestedTwo", "inner2_").isPresent());
    Assert.assertFalse(inspector.clazz("nestedproto2.GeneratedNestedProto$NestedOne").isPresent());
    Assert.assertFalse(inspector.clazz("nestedproto2.GeneratedNestedProto$NestedTwo").isPresent());
  }

  private static void enumprotoUnusedFieldsAreGone(DexInspector inspector) {
    ClassSubject protoClass = inspector.clazz("enumproto.GeneratedEnumProto$Enum");
    Assert.assertTrue(protoClass.isPresent());
    Assert.assertFalse(protoClass.field("int", "id_").isPresent());
    Assert.assertTrue(protoClass.field("int", "enum_").isPresent());
    Assert.assertFalse(protoClass.field("int", "other_").isPresent());
    ClassSubject protoThreeClass = inspector.clazz("enumproto.three.GeneratedEnumProto$EnumThree");
    Assert.assertTrue(protoThreeClass.isPresent());
    Assert.assertFalse(protoThreeClass.field("int", "id_").isPresent());
    Assert.assertTrue(protoThreeClass.field("int", "enum_").isPresent());
    Assert.assertFalse(protoThreeClass.field("int", "other_").isPresent());
  }

  private static void repeatedUnusedFieldsAreGone(DexInspector inspector) {
    ClassSubject protoClass = inspector.clazz("repeatedproto.GeneratedRepeatedProto$Repeated");
    Assert.assertTrue(protoClass.isPresent());
    Assert.assertFalse(protoClass.field("int", "id_").isPresent());
    Assert.assertTrue(
        protoClass.field("com.google.protobuf.Internal$ProtobufList", "repeated_").isPresent());
    Assert.assertFalse(
        protoClass.field("com.google.protobuf.Internal$ProtobufList", "sub_").isPresent());
    Assert.assertFalse(
        protoClass.field("com.google.protobuf.Internal$BooleanList", "other_").isPresent());
  }

  private static void oneofprotoUnusedFieldsAreGone(DexInspector inspector) {
    ClassSubject protoClass = inspector.clazz("oneofproto.GeneratedOneOfProto$Oneof");
    Assert.assertTrue(protoClass.isPresent());
    Assert.assertFalse(protoClass.field("int", "id_").isPresent());
    Assert.assertFalse(protoClass.field("Object", "otherfields_").isPresent());
  }

  private static List<String> names =
      ImmutableList.of("pqr", "vw$", "abc", "def", "stu", "ghi", "jkl", "ea", "xyz_", "mno");

  private static void checkFieldInDictionary(FieldSubject field) {
    if (!names.contains(field.getField().field.name.toSourceString())) {
      throw new AssertionError();
    }
  }

  private static void checkMethodInDictionary(MethodSubject method) {
    String name = method.getMethod().method.name.toSourceString();
    if (!names.contains(name) && !name.equals("<init>") && !name.equals("main")) {
      throw new AssertionError();
    }
  }

  private static void checkClassAndMemberInDictionary(ClassSubject clazz) {
    String name = clazz.getDexClass().type.getName();
    if (!names.contains(name) && !name.equals("Shaking")) {
      throw new AssertionError();
    }

    clazz.forAllMethods(method -> checkMethodInDictionary(method));
    clazz.forAllFields(field -> checkFieldInDictionary(field));
  }

  private static void shaking15testDictionary(DexInspector inspector) {
    inspector.forAllClasses((clazz) -> checkClassAndMemberInDictionary(clazz));
  }

  private static void abstractMethodRemains(DexInspector inspector) {
    ClassSubject programClass = inspector.clazz("shaking17.AbstractProgramClass");
    Assert.assertTrue(programClass.isPresent());
    Assert.assertTrue(
        programClass.method("int", "abstractMethod", Collections.emptyList()).isPresent());
  }

  private static void assumenosideeffects1CheckOutput(String output1, String output2) {
    Assert.assertEquals(StringUtils.lines("noSideEffectVoid", "noSideEffectInt"), output1);
    Assert.assertEquals("", output2);
  }

  private static void assumenosideeffects2CheckOutput(String output1, String output2) {
    Assert.assertEquals(StringUtils.lines("Hello, world!"), output1);
    Assert.assertEquals("", output2);
  }

  private static void assumenosideeffects3CheckOutput(String output1, String output2) {
    Assert.assertEquals(StringUtils.lines("0", "1", "0L", "1L"), output1);
    Assert.assertEquals(StringUtils.lines("1", "0", "1L", "0L"), output2);
  }

  private static void assumenosideeffects4CheckOutput(String output1, String output2) {
    Assert.assertEquals(
        StringUtils.lines("method0", "0", "method1", "1", "method0L", "0L", "method1L", "1L"),
        output1);
    Assert.assertEquals(StringUtils.lines("1", "0", "1L", "0L"), output2);
  }

  private static void assumenosideeffects5CheckOutput(String output1, String output2) {
    Assert.assertEquals(StringUtils.lines("methodTrue", "true", "methodFalse", "false"), output1);
    Assert.assertEquals(StringUtils.lines("false", "true"), output2);
  }

  private static void assumevalues1CheckOutput(String output1, String output2) {
    Assert.assertEquals(StringUtils.lines("3", "3L"), output1);
    Assert.assertEquals(StringUtils.lines("1", "1L"), output2);
  }

  private static void assumevalues2CheckOutput(String output1, String output2) {
    Assert.assertEquals(StringUtils.lines("1", "2", "3", "4", "1L", "2L", "3L", "4L"), output1);
    Assert.assertEquals(StringUtils.lines("2", "3", "2L", "3L"), output2);
  }

  private static void assumevalues3CheckOutput(String output1, String output2) {
    Assert.assertEquals(StringUtils.lines("3", "3L"), output1);
    Assert.assertEquals(StringUtils.lines("1", "1L"), output2);
  }

  private static void assumevalues4CheckOutput(String output1, String output2) {
    Assert.assertEquals(
        StringUtils.lines("method0", "0", "method1", "1", "method0L", "0L", "method1L", "1L"),
        output1);
    Assert.assertEquals(
        StringUtils.lines("method0", "1", "method1", "0", "method0L", "1L", "method1L", "0L"),
        output2);
  }

  private static void assumevalues5CheckOutput(String output1, String output2) {
    Assert.assertEquals(StringUtils.lines("methodTrue", "true", "methodFalse", "false"), output1);
    Assert.assertEquals(StringUtils.lines("methodTrue", "false", "methodFalse", "true"), output2);
  }

  private static void annotationRemovalHasAllInnerClassAnnotations(DexInspector inspector) {
    ClassSubject outer = inspector.clazz("annotationremoval.OuterClass");
    Assert.assertTrue(outer.isPresent());
    Assert.assertFalse(outer.getDexClass().getInnerClasses().isEmpty());
    ClassSubject inner = inspector.clazz("annotationremoval.OuterClass$InnerClass");
    Assert.assertTrue(inner.isPresent());
    Assert.assertTrue(inner.isMemberClass());
    Assert.assertFalse(inner.isAnonymousClass());
    Assert.assertFalse(inner.isLocalClass());
    ClassSubject anonymous = inspector.clazz("annotationremoval.OuterClass$1");
    Assert.assertTrue(anonymous.isPresent());
    Assert.assertTrue(anonymous.isAnonymousClass());
    Assert.assertFalse(anonymous.isMemberClass());
    Assert.assertFalse(anonymous.isLocalClass());
    ClassSubject local = inspector.clazz("annotationremoval.OuterClass$1LocalMagic");
    Assert.assertTrue(local.isPresent());
    Assert.assertTrue(local.isLocalClass());
    Assert.assertFalse(local.isMemberClass());
    Assert.assertFalse(local.isAnonymousClass());
  }

  private static void annotationRemovalHasNoInnerClassAnnotations(DexInspector inspector) {
    ClassSubject outer = inspector.clazz("annotationremoval.OuterClass");
    Assert.assertTrue(outer.isPresent());
    Assert.assertTrue(outer.getDexClass().getInnerClasses().isEmpty());
    ClassSubject inner = inspector.clazz("annotationremoval.OuterClass$InnerClass");
    Assert.assertTrue(inner.isPresent());
    Assert.assertNull(inner.getDexClass().getEnclosingMethod());
    Assert.assertTrue(inner.getDexClass().getInnerClasses().isEmpty());
    ClassSubject anonymous = inspector.clazz("annotationremoval.OuterClass$1");
    Assert.assertTrue(anonymous.isPresent());
    Assert.assertNull(anonymous.getDexClass().getEnclosingMethod());
    Assert.assertTrue(anonymous.getDexClass().getInnerClasses().isEmpty());
    ClassSubject local = inspector.clazz("annotationremoval.OuterClass$1LocalMagic");
    Assert.assertTrue(local.isPresent());
    Assert.assertNull(local.getDexClass().getEnclosingMethod());
    Assert.assertTrue(local.getDexClass().getInnerClasses().isEmpty());
  }

  private static void checkSameStructure(DexInspector ref, DexInspector inspector) {
    ref.forAllClasses(refClazz -> checkSameStructure(refClazz,
        inspector.clazz(refClazz.getDexClass().toSourceString())));
  }

  private static void checkSameStructure(ClassSubject refClazz, ClassSubject clazz) {
    Assert.assertTrue(clazz.isPresent());
    refClazz.forAllFields(refField -> checkSameStructure(refField, clazz));
    refClazz.forAllMethods(refMethod -> checkSameStructure(refMethod, clazz));
  }

  private static void checkSameStructure(FoundMethodSubject refMethod, ClassSubject clazz) {
    MethodSignature signature = refMethod.getOriginalSignature();
    // Don't check for existence of class initializers, as the code optimization can remove them.
    if (!refMethod.isClassInitializer()) {
      Assert.assertTrue("Missing Method: " + clazz.getDexClass().toSourceString() + "."
              + signature.toString(),
          clazz.method(signature).isPresent());
    }
  }

  private static void checkSameStructure(FoundFieldSubject refField, ClassSubject clazz) {
    FieldSignature signature = refField.getOriginalSignature();
    Assert.assertTrue(
        "Missing field: " + signature.type + " " + clazz.getOriginalDescriptor()
            + "." + signature.name,
        clazz.field(signature.type, signature.name).isPresent());
  }

  @Parameters(name = "dex: {0} frontend: {1} keep: {3} minify: {4}")
  public static Collection<Object[]> data() {
    List<String> tests = Arrays
        .asList(
            "examples/shaking1",
            "examples/shaking2",
            "examples/shaking3",
            "examples/shaking4",
            "examples/shaking5",
            "examples/shaking6",
            "examples/shaking7",
            "examples/shaking8",
            "examples/shaking9",
            "examples/shaking10",
            "examples/shaking11",
            "examples/shaking12",
            "examples/shaking13",
            "examples/shaking14",
            "examples/shaking15",
            "examples/shaking16",
            "examples/shaking17",
            "examples/minification",
            "examples/minifygeneric",
            "examples/minifygenericwithinner",
            "examples/assumenosideeffects1",
            // TODO(b/70550443): Enable once fixed.
            //"examples/assumenosideeffects2",
            "examples/assumenosideeffects3",
            "examples/assumenosideeffects4",
            "examples/assumenosideeffects5",
            "examples/assumevalues1",
            "examples/assumevalues2",
            "examples/assumevalues3",
            "examples/assumevalues4",
            "examples/assumevalues5",
            "examples/annotationremoval",
            "examples/memberrebinding2",
            "examples/memberrebinding3",
            "examples/inlining",
            "examples/simpleproto1",
            "examples/simpleproto2",
            "examples/simpleproto3",
            "examples/nestedproto1",
            "examples/nestedproto2",
            "examples/enumproto",
            "examples/repeatedproto",
            "examples/oneofproto",
            "examplesAndroidN/shaking");

    // Keys can be the name of the test or the name of the test followed by a colon and the name
    // of the keep file.
    Map<String, Consumer<DexInspector>> inspections = new HashMap<>();
    inspections.put("examples/shaking1:keep-rules.txt", TreeShakingTest::shaking1HasNoClassUnused);
    inspections.put("examples/shaking1:keep-rules-repackaging.txt",
        TreeShakingTest::shaking1IsCorrectlyRepackaged);
    inspections
        .put("examples/shaking2:keep-rules.txt", TreeShakingTest::shaking2SuperClassIsAbstract);
    inspections.put("examples/shaking3:keep-by-tag.txt", TreeShakingTest::shaking3HasNoClassB);
    inspections
        .put("examples/shaking3:keep-by-tag-default.txt", TreeShakingTest::shaking3HasNoClassB);
    inspections.put("examples/shaking3:keep-by-tag-with-pattern.txt",
        TreeShakingTest::shaking3HasNoClassB);
    inspections.put("examples/shaking3:keep-by-tag-via-interface.txt",
        TreeShakingTest::shaking3HasNoClassB);
    inspections
        .put("examples/shaking3:keep-by-tag-on-method.txt", TreeShakingTest::shaking3HasNoClassB);
    inspections.put("examples/shaking3:keep-no-abstract-classes.txt",
        TreeShakingTest::shaking3HasNoPrivateClass);
    inspections.put("examples/shaking5", TreeShakingTest::shaking5Inspection);
    inspections.put("examples/shaking6:keep-public.txt", TreeShakingTest::hasNoPrivateMethods);
    inspections.put("examples/shaking6:keep-non-public.txt",
        TreeShakingTest::hasNoPublicMethodsButPrivate);
    inspections.put("examples/shaking6:keep-justAMethod-public.txt",
        TreeShakingTest::hasNoPrivateJustAMethod);
    inspections.put("examples/shaking6:keep-justAMethod-OnInt.txt",
        TreeShakingTest::hasOnlyIntJustAMethod);
    inspections.put("examples/shaking7:keep-public-fields.txt",
        TreeShakingTest::shaking7HasOnlyPublicFields);
    inspections.put("examples/shaking7:keep-double-fields.txt",
        TreeShakingTest::shaking7HasOnlyDoubleFields);
    inspections.put("examples/shaking7:keep-public-theDoubleField-fields.txt",
        TreeShakingTest::shaking7HasOnlyPublicFieldsNamedTheDoubleField);
    inspections.put("examples/shaking7:keep-public-theIntField-fields.txt",
        TreeShakingTest::shaking7HasOnlyPublicFieldsNamedTheIntField);
    inspections.put("examples/shaking8:keep-rules.txt",
        TreeShakingTest::shaking8ThingClassIsAbstractAndEmpty);
    inspections
        .put("examples/shaking9:keep-rules.txt", TreeShakingTest::shaking9OnlySuperMethodsKept);
    inspections
        .put("examples/shaking11:keep-rules.txt", TreeShakingTest::shaking11OnlyOneClassKept);
    inspections.put("examples/shaking11:keep-rules-keep-method.txt",
        TreeShakingTest::shaking11BothMethodsKept);
    inspections.put("examples/shaking12:keep-rules.txt",
            TreeShakingTest::shaking12OnlyInstantiatedClassesHaveConstructors);
    inspections.put("examples/shaking13:keep-rules.txt",
            TreeShakingTest::shaking13EnsureFieldWritesCorrect);
    inspections.put("examples/shaking14:keep-rules.txt",
            TreeShakingTest::shaking14EnsureRightStaticMethodsLive);
    inspections.put("examples/shaking15:keep-rules.txt", TreeShakingTest::shaking15testDictionary);
    inspections.put("examples/shaking17:keep-rules.txt", TreeShakingTest::abstractMethodRemains);
    inspections.put("examples/annotationremoval:keep-rules.txt",
            TreeShakingTest::annotationRemovalHasNoInnerClassAnnotations);
    inspections.put("examples/annotationremoval:keep-rules-keep-innerannotation.txt",
            TreeShakingTest::annotationRemovalHasAllInnerClassAnnotations);
    inspections.put("examples/simpleproto1:keep-rules.txt",
        TreeShakingTest::simpleproto1UnusedFieldIsGone);
    inspections.put("examples/simpleproto2:keep-rules.txt",
        TreeShakingTest::simpleproto2UnusedFieldsAreGone);
    inspections.put("examples/nestedproto1:keep-rules.txt",
        TreeShakingTest::nestedproto1UnusedFieldsAreGone);
    inspections.put("examples/nestedproto2:keep-rules.txt",
        TreeShakingTest::nestedproto2UnusedFieldsAreGone);
    inspections
        .put("examples/enumproto:keep-rules.txt", TreeShakingTest::enumprotoUnusedFieldsAreGone);
    inspections
        .put("examples/repeatedproto:keep-rules.txt", TreeShakingTest::repeatedUnusedFieldsAreGone);
    inspections
        .put("examples/oneofproto:keep-rules.txt", TreeShakingTest::oneofprotoUnusedFieldsAreGone);

    // Keys can be the name of the test or the name of the test followed by a colon and the name
    // of the keep file.
    Map<String, Collection<List<String>>> optionalRules = new HashMap<>();
    optionalRules.put("examples/shaking1", ImmutableList.of(
        Collections.singletonList(EMPTY_FLAGS),
        Lists.newArrayList(EMPTY_FLAGS, EMPTY_FLAGS)));
    List<Object[]> testCases = new ArrayList<>();

    Map<String, BiConsumer<String, String>> outputComparators = new HashMap<>();
    outputComparators
        .put("examples/assumenosideeffects1",
            TreeShakingTest::assumenosideeffects1CheckOutput);
    // TODO(b/70550443): Enable once fixed.
    // outputComparators
    //     .put("examples/assumenosideeffects2",
    //         TreeShakingTest::assumenosideeffects2CheckOutput);
    outputComparators
        .put("examples/assumenosideeffects3",
            TreeShakingTest::assumenosideeffects3CheckOutput);
    outputComparators
        .put("examples/assumenosideeffects4",
            TreeShakingTest::assumenosideeffects4CheckOutput);
    outputComparators
        .put("examples/assumenosideeffects5",
            TreeShakingTest::assumenosideeffects5CheckOutput);
    outputComparators
        .put("examples/assumevalues1",
            TreeShakingTest::assumevalues1CheckOutput);
    outputComparators
        .put("examples/assumevalues2",
            TreeShakingTest::assumevalues2CheckOutput);
    outputComparators
        .put("examples/assumevalues3",
            TreeShakingTest::assumevalues3CheckOutput);
    outputComparators
        .put("examples/assumevalues4",
            TreeShakingTest::assumevalues4CheckOutput);
    outputComparators
        .put("examples/assumevalues5",
            TreeShakingTest::assumevalues5CheckOutput);

    Map<String, BiConsumer<DexInspector, DexInspector>> dexComparators = new HashMap<>();
    dexComparators
        .put("examples/shaking1:keep-rules-dont-shrink.txt", TreeShakingTest::checkSameStructure);
    dexComparators
        .put("examples/shaking2:keep-rules-dont-shrink.txt", TreeShakingTest::checkSameStructure);
    dexComparators
        .put("examples/shaking4:keep-rules-dont-shrink.txt", TreeShakingTest::checkSameStructure);

    Set<String> usedInspections = new HashSet<>();
    Set<String> usedOptionalRules = new HashSet<>();
    Set<String> usedOutputComparators = new HashSet<>();
    Set<String> usedDexComparators = new HashSet<>();

    for (String test : tests) {
      String mainClass = deriveMainClass(test);
      File[] keepFiles = new File(ToolHelper.TESTS_DIR + test)
          .listFiles(file -> file.isFile() && file.getName().endsWith(".txt"));
      for (File keepFile : keepFiles) {
        String keepName = keepFile.getName();
        Consumer<DexInspector> inspection =
            getTestOptionalParameter(inspections, usedInspections, test, keepName);
        Collection<List<String>> additionalRules =
            getTestOptionalParameter(optionalRules, usedOptionalRules, test, keepName);

        BiConsumer<String, String> outputComparator =
            getTestOptionalParameter(outputComparators, usedOutputComparators, test, keepName);
        BiConsumer<DexInspector, DexInspector> dexComparator =
            getTestOptionalParameter(dexComparators, usedDexComparators, test, keepName);

        addTestCases(testCases, test, mainClass, keepName,
            Collections.singletonList(keepFile.getPath()), inspection,
            outputComparator, dexComparator);

        if (additionalRules != null) {
          for (List<String> list : additionalRules) {
            List<String> keepList = new ArrayList<>(list.size());
            keepList.add(keepFile.getPath());
            keepList.addAll(list);
            addTestCases(testCases, test, mainClass, keepName, keepList, inspection,
                outputComparator, dexComparator);
          }
        }
      }
    }

    assert usedInspections.size() == inspections.size();
    assert usedOptionalRules.size() == optionalRules.size();
    assert usedOutputComparators.size() == outputComparators.size();

    return testCases;
  }

  private static void addTestCases(List<Object[]> testCases, String test, String mainClass,
      String keepName, List<String> keepList, Consumer<DexInspector> inspection,
      BiConsumer<String, String> outputComparator,
      BiConsumer<DexInspector, DexInspector> dexComparator) {
    if (SKIPPED.contains(test)) {
      return;
    }
    for (MinifyMode mode : MinifyMode.values()) {
      addTestCase(testCases, test, Frontend.JAR, mainClass, keepName, keepList, mode, inspection,
          outputComparator, dexComparator);
      addTestCase(testCases, test, Frontend.DEX, mainClass, keepName, keepList, mode, inspection,
          outputComparator, dexComparator);
    }
  }

  private static void addTestCase(List<Object[]> testCases, String test, Frontend kind,
      String mainClass, String keepName, List<String> keepList, MinifyMode minify,
      Consumer<DexInspector> inspection, BiConsumer<String, String> outputComparator,
      BiConsumer<DexInspector, DexInspector> dexComparator) {
    if (!IGNORED_FLAGS.contains(test + ":" + keepName)
        && !IGNORED.contains(test + ":" + keepName + ":" + kind + ":" + minify)) {
      testCases.add(new Object[]{
          test, kind, mainClass, keepList, minify, inspection, outputComparator, dexComparator});
    }
  }

  static <T> T getTestOptionalParameter(
      Map<String, T> specifications, Set<String> usedSpecifications, String test,
      String keepName) {
    T parameter = specifications.get(test);
    if (parameter == null) {
      parameter = specifications.get(test + ":" + keepName);
      if (parameter != null) {
        usedSpecifications.add(test + ":" + keepName);
      }
    } else {
      usedSpecifications.add(test);
    }
    return parameter;
  }

  private static String deriveMainClass(String testName) {
    String testBaseName = testName.substring(testName.lastIndexOf('/') + 1);
    StringBuilder mainClass = new StringBuilder(testName.length() * 2 + 1);
    mainClass.append(testBaseName);
    mainClass.append('.');
    mainClass.append(Character.toUpperCase(testBaseName.charAt(0)));
    for (int i = 1; i < testBaseName.length(); i++) {
      char next = testBaseName.charAt(i);
      if (!Character.isAlphabetic(next)) {
        break;
      }
      mainClass.append(next);
    }
    return mainClass.toString();
  }

  @Test
  public void treeShakingTest() throws IOException, InterruptedException, ExecutionException {
    if (!ToolHelper.artSupported()) {
      return;
    }
    String out = temp.getRoot().getCanonicalPath();
    Path generated = Paths.get(out, "classes.dex");
    Consumer<ArtCommandBuilder> extraArtArgs = builder -> {
      builder.appendClasspath(ToolHelper.EXAMPLES_BUILD_DIR + "shakinglib/classes.dex");
    };

    if (Files.exists(Paths.get(originalDex))) {
      if (outputComparator != null) {
        String output1 = ToolHelper.runArtNoVerificationErrors(
            Collections.singletonList(originalDex), mainClass, extraArtArgs, null);
        String output2 = ToolHelper.runArtNoVerificationErrors(
            Collections.singletonList(generated.toString()), mainClass, extraArtArgs, null);
        outputComparator.accept(output1, output2);
      } else {
        ToolHelper.checkArtOutputIdentical(Collections.singletonList(originalDex),
            Collections.singletonList(generated.toString()), mainClass,
            extraArtArgs, null);
      }

      if (dexComparator != null) {
        DexInspector ref = new DexInspector(Paths.get(originalDex));
        DexInspector inspector = new DexInspector(generated,
            minify.isMinify() ? temp.getRoot().toPath().resolve(DEFAULT_PROGUARD_MAP_FILE)
                .toString()
                : null);
        dexComparator.accept(ref, inspector);
      }
    } else {
      Assert.assertNull(outputComparator);
      Assert.assertNull(dexComparator);
      ToolHelper.runArtNoVerificationErrors(
          Collections.singletonList(generated.toString()), mainClass, extraArtArgs, null);
    }

    if (inspection != null) {
      DexInspector inspector = new DexInspector(generated,
          minify.isMinify() ? temp.getRoot().toPath().resolve(DEFAULT_PROGUARD_MAP_FILE).toString()
              : null);
      inspection.accept(inspector);
    }
  }
}
