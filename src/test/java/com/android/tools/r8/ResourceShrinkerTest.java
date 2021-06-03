// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for resource shrinker analyzer. This is checking that dex files are processed correctly.
 */
public class ResourceShrinkerTest extends TestBase {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static class TrackAll implements ResourceShrinker.ReferenceChecker {
    Set<Integer> refIntegers = Sets.newHashSet();
    Set<String> refStrings = Sets.newHashSet();
    List<List<String>> refFields = Lists.newArrayList();
    List<List<String>> refMethods = Lists.newArrayList();
    List<MethodReference> methodsVisited = Lists.newArrayList();
    List<ClassReference> classesVisited = Lists.newArrayList();

    @Override
    public boolean shouldProcess(String internalName) {
      return !internalName.equals(ResourceClassToSkip.class.getName());
    }

    @Override
    public void referencedInt(int value) {
      refIntegers.add(value);
    }

    @Override
    public void referencedString(String value) {
      refStrings.add(value);
    }

    @Override
    public void referencedStaticField(String internalName, String fieldName) {
      refFields.add(Lists.newArrayList(internalName, fieldName));
    }

    @Override
    public void referencedMethod(String internalName, String methodName, String methodDescriptor) {
      if (Objects.equals(internalName, "java/lang/Object")
          && Objects.equals(methodName, "<init>")) {
        return;
      }
      refMethods.add(Lists.newArrayList(internalName, methodName, methodDescriptor));
    }

    @Override
    public void startMethodVisit(MethodReference methodReference) {
      methodsVisited.add(methodReference);
    }

    @Override
    public void endMethodVisit(MethodReference methodReference) {
      assertEquals(methodsVisited.get(methodsVisited.size() - 1), methodReference);
    }

    @Override
    public void startClassVisit(ClassReference classReference) {
      classesVisited.add(classReference);
    }

    @Override
    public void endClassVisit(ClassReference classReference) {
      assertEquals(classesVisited.get(classesVisited.size() - 1), classReference);
    }
  }

  private static class EmptyClass {
  }

  @Test
  public void testEmptyClass() throws CompilationFailedException, IOException, ExecutionException {
    TrackAll analysis = runAnalysis(EmptyClass.class);

    assertThat(analysis.refIntegers, is(Sets.newHashSet()));
    assertThat(analysis.refStrings, is(Sets.newHashSet()));
    assertThat(analysis.refFields, is(Lists.newArrayList()));
    assertThat(analysis.refMethods, is(Lists.newArrayList()));
  }

  private static class ConstInCode {
    public void foo() {
      int i = 10;
      System.out.print(i);
      System.out.print(11);
      String s = "my_layout";
      System.out.print("another_layout");
    }
  }

  @Test
  public void testConstsAndFieldAndMethods()
      throws CompilationFailedException, IOException, ExecutionException {
    TrackAll analysis = runAnalysis(ConstInCode.class);

    assertThat(analysis.refIntegers, is(Sets.newHashSet(10, 11)));
    assertThat(analysis.refStrings, is(Sets.newHashSet("my_layout", "another_layout")));

    assertEquals(3, analysis.refFields.size());
    assertThat(analysis.refFields.get(0), is(Lists.newArrayList("java/lang/System", "out")));
    assertThat(analysis.refFields.get(1), is(Lists.newArrayList("java/lang/System", "out")));
    assertThat(analysis.refFields.get(2), is(Lists.newArrayList("java/lang/System", "out")));

    assertEquals(3, analysis.refMethods.size());
    assertThat(
        analysis.refMethods.get(0), is(Lists.newArrayList("java/io/PrintStream", "print", "(I)V")));
    assertThat(
        analysis.refMethods.get(1), is(Lists.newArrayList("java/io/PrintStream", "print", "(I)V")));
    assertThat(
        analysis.refMethods.get(2),
        is(Lists.newArrayList("java/io/PrintStream", "print", "(Ljava/lang/String;)V")));
  }

  @SuppressWarnings("unused")
  private static class StaticFields {
    static final String sStringValue = "staticValue";
    static final int sIntValue = 10;
    static final int[] sIntArrayValue = {11, 12, 13};
    static final String[] sStringArrayValue = {"a", "b", "c"};
  }

  @Test
  public void testStaticValues()
      throws CompilationFailedException, IOException, ExecutionException {
    TrackAll analysis = runAnalysis(StaticFields.class);

    assertThat(analysis.refIntegers, hasItems(10, 11, 12, 13));
    assertThat(analysis.refStrings, hasItems("staticValue", "a", "b", "c"));
    assertThat(analysis.refFields, is(Lists.newArrayList()));
    assertThat(analysis.refMethods, is(Lists.newArrayList()));
  }

  @SuppressWarnings("unused")
  private static class ClassesAndMethodsVisited {
    final int value = getValue();

    static final int staticValue;

    static {
      staticValue = 42;
    }

    int getValue() {
      return true ? 0 : 1;
    }
  }

  @Test
  public void testNumberOfMethodsAndClassesVisited()
      throws CompilationFailedException, IOException, ExecutionException {
    TrackAll analysis = runAnalysis(ClassesAndMethodsVisited.class);

    List<String> methodNames =
        analysis.methodsVisited.stream()
            .map(MethodReference::getMethodName)
            .collect(Collectors.toList());
    List<String> classNames =
        analysis.classesVisited.stream()
            .map(ClassReference::getBinaryName)
            .collect(Collectors.toList());
    assertThat(methodNames, hasItems("<init>", "<clinit>", "getValue"));
    assertThat(
        classNames, hasItems("com/android/tools/r8/ResourceShrinkerTest$ClassesAndMethodsVisited"));
  }

  @Retention(RetentionPolicy.RUNTIME)
  private @interface IntAnnotation {
    int value() default 10;
  }

  @Retention(RetentionPolicy.RUNTIME)
  private @interface OuterAnnotation {
    IntAnnotation inner() default @IntAnnotation(11);
  }

  @IntAnnotation(42)
  private static class Annotated {
    @OuterAnnotation
    Object defaultAnnotated = new Object();
    @IntAnnotation(12)
    Object withValueAnnotated = new Object();
    @IntAnnotation(13)
    static Object staticValueAnnotated = new Object();

    @IntAnnotation(14)
    public void annotatedPublic() {
    }

    @IntAnnotation(15)
    public void annotatedPrivate() {
    }
  }

  @Test
  public void testAnnotations() throws CompilationFailedException, IOException, ExecutionException {
    TrackAll analysis = runAnalysis(IntAnnotation.class, OuterAnnotation.class, Annotated.class);

    assertThat(analysis.refIntegers, hasItems(10, 11, 12, 13, 14, 15, 42));
    assertThat(analysis.refStrings, is(Sets.newHashSet()));
    assertThat(analysis.refFields, is(Lists.newArrayList()));
    assertThat(analysis.refMethods, is(Lists.newArrayList()));
  }

  private static class ResourceClassToSkip {
    int[] i = {100, 101, 102};
  }

  private static class ToProcess {
    int[] i = {10, 11, 12};
    String[] s = {"10", "11", "12"};
  }

  @Test
  public void testWithSkippingSome()
      throws ExecutionException, CompilationFailedException, IOException {
    TrackAll analysis = runAnalysis(ResourceClassToSkip.class, ToProcess.class);

    assertThat(analysis.refIntegers, hasItems(10, 11, 12));
    assertThat(analysis.refStrings, is(Sets.newHashSet("10", "11", "12")));
    assertThat(analysis.refFields, is(Lists.newArrayList()));
    assertThat(analysis.refMethods, is(Lists.newArrayList()));
  }

  @Test
  public void testPayloadBeforeFillArrayData() throws Exception {
    SmaliBuilder builder = new SmaliBuilder("Test");
    builder.addMainMethod(
        2,
        "goto :start",
        "",
        ":array_data",
        ".array-data 4",
        "  4 5 6",
        ".end array-data",
        "",
        ":start",
        "const/4 v1, 3",
        "new-array v0, v1, [I",
        "fill-array-data v0, :array_data",
        "return-object v0"
    );
    AndroidApp app =
        AndroidApp.builder().addDexProgramData(builder.compile(), Origin.unknown()).build();
    TrackAll analysis = runOnApp(app);

    assertThat(analysis.refIntegers, hasItems(4, 5, 6));
    assertThat(analysis.refStrings, is(Sets.newHashSet()));
    assertThat(analysis.refFields, is(Lists.newArrayList()));
    assertThat(analysis.refMethods, is(Lists.newArrayList()));
  }

  private TrackAll runAnalysis(Class<?>... classes)
      throws IOException, ExecutionException, CompilationFailedException {
    AndroidApp app = readClasses(classes);
    return runOnApp(app);
  }

  private TrackAll runOnApp(AndroidApp app)
      throws IOException, ExecutionException, CompilationFailedException {
    AndroidApp outputApp = compileWithD8(app);
    Path outputDex = tmp.newFolder().toPath().resolve("classes.dex");
    outputApp.writeToDirectory(outputDex.getParent(), OutputMode.DexIndexed);

    ProgramResourceProvider provider =
        () -> Lists.newArrayList(ProgramResource.fromFile(ProgramResource.Kind.DEX, outputDex));
    ResourceShrinker.Command command =
        new ResourceShrinker.Builder().addProgramResourceProvider(provider).build();

    TrackAll analysis = new TrackAll();
    ResourceShrinker.run(command, analysis);
    return analysis;
  }
}