// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.forceproguardcompatibility.defaultctor;

import static com.android.tools.r8.shaking.forceproguardcompatibility.defaultctor.ExternalizableDataClass.TYPE_1;
import static com.android.tools.r8.shaking.forceproguardcompatibility.defaultctor.ExternalizableDataClass.TYPE_2;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatibilityTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

final class ExternalizableDataClass implements Externalizable {
  static final byte TYPE_1 = 1;
  static final byte TYPE_2 = 2;

  private byte byteField;
  private Object objectField;

  // Default constructor for deserialization
  public ExternalizableDataClass() {
  }

  // Constructor for serialization
  public ExternalizableDataClass(byte byteField, Object objectField) {
    this.byteField = byteField;
    this.objectField = objectField;
  }

  @Override
  public void writeExternal(ObjectOutput objectOutput) throws IOException {
    writeInternal(byteField, objectField, objectOutput);
  }

  private static void writeInternal(byte b, Object obj, DataOutput out) throws IOException {
    out.writeByte(b);
    switch (b) {
      case TYPE_1:
        ((Delegate1) obj).delegateWrite(out);
        break;
      case TYPE_2:
        ((Delegate2) obj).delegateWrite(out);
        break;
      default:
        throw new InvalidClassException("Unknown type: " + b);
    }
  }

  @Override
  public void readExternal(ObjectInput objectInput) throws IOException {
    byteField = objectInput.readByte();
    objectField = readInternal(byteField, objectInput);
  }

  private static Object readInternal(byte type, DataInput in) throws IOException {
    switch (type) {
      case TYPE_1: return Delegate1.delegateRead(in);
      case TYPE_2: return Delegate2.delegateRead(in);
      default:
        throw new InvalidClassException("Unknown type: " + type);
    }
  }

  private Object readResolve() {
    return objectField;
  }

  @Override
  public String toString() {
    return "{ type: " + byteField + ", obj: " + objectField.toString() + " }";
  }
}

final class Delegate1 implements Serializable {
  private int intField;

  private Object writeReplace() {
    return new ExternalizableDataClass(TYPE_1, this);
  }

  static Delegate1 of(int intField) {
    Delegate1 instance = new Delegate1();
    instance.intField = intField;
    return instance;
  }

  void delegateWrite(DataOutput out) throws IOException {
    out.writeInt(intField);
  }

  static Object delegateRead(DataInput in) throws IOException {
    int i = in.readInt();
    return Delegate1.of(i);
  }

  private Object readResolve() throws ObjectStreamException {
    throw new InvalidObjectException("Deserialization via serialization delegate");
  }

  @Override
  public String toString() {
    return "(" + intField + ")";
  }
}

final class Delegate2 implements Serializable {
  private String stringField;

  private Object writeReplace() {
    return new ExternalizableDataClass(TYPE_2, this);
  }

  static Delegate2 of(String stringField) {
    Delegate2 instance = new Delegate2();
    instance.stringField = stringField;
    return instance;
  }

  void delegateWrite(DataOutput out) throws IOException {
    out.writeUTF(stringField);
  }

  static Object delegateRead(DataInput in) throws IOException {
    String s = in.readUTF();
    return Delegate2.of(s);
  }

  private Object readResolve() throws ObjectStreamException {
    throw new InvalidObjectException("Deserialization via serialization delegate");
  }

  @Override
  public String toString() {
    return "<" + stringField + ">";
  }
}

class ExternalizableTestMain {
  public static void main(String[] args) throws Exception {
    Delegate2 data2 = Delegate2.of("MessageToSerialize");
    // "Before: <MessageToSerialize>"
    System.out.println("Before: " + data2.toString());

    // Serialization
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ObjectOutputStream objectOutputStream = new ObjectOutputStream(out);
    objectOutputStream.writeObject(data2);
    objectOutputStream.close();

    byte[] byteArray = out.toByteArray();

    // Deserialization
    ByteArrayInputStream in = new ByteArrayInputStream(byteArray);
    ObjectInputStream objectInputStream = new ObjectInputStream(in);
    Object copy = objectInputStream.readObject();
    assert copy instanceof Delegate2;
    // "After: <MessageToSerialize>"
    System.out.println("After: " + copy.toString());
  }
}

class NonSerializableSuperClass {
  protected String tag;

  // Default constructor for deserialization
  public NonSerializableSuperClass() {
    this.tag = null;
  }

  public NonSerializableSuperClass(String tag) {
    this.tag = tag;
  }

  @Override
  public String toString() {
    return tag == null ? "NULL" : tag;
  }
}

class SerializableDataClass extends NonSerializableSuperClass implements Serializable {
  private String extraTag;

  public SerializableDataClass() {
    super();
    this.extraTag = null;
  }

  public SerializableDataClass(String tag, String extraTag) {
    super(tag);
    this.extraTag = extraTag;
  }

  @Override
  public String toString() {
    return super.toString() + ", " + (extraTag == null ? "NULL" : extraTag);
  }
}

class SerializableTestMain {
  public static void main(String[] args) throws Exception {
    SerializableDataClass data = new SerializableDataClass("TagToSerialize", "ExtraToSerialize");
    // "Before: TagToSerialize, ExtraToSerialize"
    System.out.println("Before: " + data.toString());

    // Serialization
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ObjectOutputStream objectOutputStream = new ObjectOutputStream(out);
    objectOutputStream.writeObject(data);
    objectOutputStream.close();

    byte[] byteArray = out.toByteArray();

    // Deserialization
    ByteArrayInputStream in = new ByteArrayInputStream(byteArray);
    ObjectInputStream objectInputStream = new ObjectInputStream(in);
    Object copy = objectInputStream.readObject();
    assert copy instanceof SerializableDataClass;
    // "After: NULL, ExtraToSerialize"
    System.out.println("After: " + copy.toString());
  }
}

@RunWith(Parameterized.class)
public class ExternalizableTest extends ProguardCompatibilityTestBase {
  private final static List<Class> CLASSES_FOR_EXTERNALIZABLE = ImmutableList.of(
      ExternalizableDataClass.class, Delegate1.class, Delegate2.class, ExternalizableTestMain.class
  );

  private final static List<Class> CLASSES_FOR_SERIALIZABLE = ImmutableList.of(
      NonSerializableSuperClass.class, SerializableDataClass.class, SerializableTestMain.class
  );

  private final Shrinker shrinker;

  @Parameterized.Parameters(name = "Shrinker: {0}")
  public static Collection<Object> data() {
    return ImmutableList.of(
        Shrinker.PROGUARD6_THEN_D8, Shrinker.PROGUARD6, Shrinker.R8, Shrinker.R8_CF);
  }

  public ExternalizableTest(Shrinker shrinker) {
    this.shrinker = shrinker;
  }

  @Test
  public void testExternalizable() throws Exception {
    // TODO(b/116735204): R8 should keep default ctor() of classes that implement Externalizable
    if (isR8(shrinker)) {
      return;
    }

    String javaOutput = runOnJava(ExternalizableTestMain.class);

    List<String> config = ImmutableList.of(
        keepMainProguardConfiguration(ExternalizableTestMain.class),
        // https://www.guardsquare.com/en/products/proguard/manual/examples#serializable
        "-keepclassmembers class * implements java.io.Serializable {",
        //"  private static final java.io.ObjectStreamField[] serialPersistentFields;",
        "  private void writeObject(java.io.ObjectOutputStream);",
        "  private void readObject(java.io.ObjectInputStream);",
        "  java.lang.Object writeReplace();",
        "  java.lang.Object readResolve();",
        "}");

    AndroidApp processedApp = runShrinker(shrinker, CLASSES_FOR_EXTERNALIZABLE, config);

    // TODO(b/117302947): Need to update ART binary.
    if (generatesCf(shrinker)) {
      String output = runOnVM(
          processedApp, ExternalizableTestMain.class.getCanonicalName(), toBackend(shrinker));
      assertEquals(javaOutput.trim(), output.trim());
    }

    CodeInspector codeInspector = new CodeInspector(processedApp, proguardMap);
    ClassSubject classSubject = codeInspector.clazz(ExternalizableDataClass.class);
    assertThat(classSubject, isPresent());
    MethodSubject init = classSubject.init(ImmutableList.of());
    assertThat(init, isPresent());
  }

  @Test
  public void testSerializable() throws Exception {
    // TODO(b/116735204): R8 should keep default ctor() of first non-serializable superclass of
    // serializable class.
    if (isR8(shrinker)) {
      return;
    }

    String javaOutput = runOnJava(SerializableTestMain.class);

    List<String> config = ImmutableList.of(
        keepMainProguardConfiguration(SerializableTestMain.class),
        // https://www.guardsquare.com/en/products/proguard/manual/examples#serializable
        "-keepclassmembers class * implements java.io.Serializable {",
        //"  private static final java.io.ObjectStreamField[] serialPersistentFields;",
        "  private void writeObject(java.io.ObjectOutputStream);",
        "  private void readObject(java.io.ObjectInputStream);",
        "  java.lang.Object writeReplace();",
        "  java.lang.Object readResolve();",
        "}");

    AndroidApp processedApp = runShrinker(shrinker, CLASSES_FOR_SERIALIZABLE, config);
    // TODO(b/117302947): Need to update ART binary.
    if (generatesCf(shrinker)) {
      String output = runOnVM(
          processedApp, SerializableTestMain.class.getCanonicalName(), toBackend(shrinker));
      assertEquals(javaOutput.trim(), output.trim());
    }

    CodeInspector codeInspector = new CodeInspector(processedApp, proguardMap);
    ClassSubject classSubject = codeInspector.clazz(NonSerializableSuperClass.class);
    assertThat(classSubject, isPresent());
    MethodSubject init = classSubject.init(ImmutableList.of());
    assertThat(init, isPresent());
  }
}
