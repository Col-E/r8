// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;

@RunWith(Parameterized.class)
public class SyntheticMarkerCfTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDefaultCfRuntime()
        .withApiLevel(AndroidApiLevel.B)
        .enableApiLevelsForCf()
        .build();
  }

  public SyntheticMarkerCfTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  /**
   * Mirror of the initial D8 synthetic marker format.
   *
   * <p>The legacy marker just had the synthetic kind id as payload.
   */
  private static class SyntheticMarkerV1 extends Attribute {
    static final SyntheticMarkerV1 PROTO = new SyntheticMarkerV1((short) 0);

    final short kindId;

    public SyntheticMarkerV1(short kindId) {
      super("com.android.tools.r8.SynthesizedClass");
      this.kindId = kindId;
    }

    @Override
    protected Attribute read(
        ClassReader classReader,
        int offset,
        int length,
        char[] charBuffer,
        int codeAttributeOffset,
        Label[] labels) {
      short id = classReader.readShort(offset);
      return new SyntheticMarkerV1(id);
    }

    @Override
    protected ByteVector write(
        ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
      ByteVector byteVector = new ByteVector();
      byteVector.putShort(kindId);
      return byteVector;
    }
  }

  /**
   * Format of the current synthetic marker.
   *
   * <p>The marker is distinguished by a new attribute type name.
   *
   * <p>The payload is the kind id, version hash length and then version hash bytes.
   */
  private static class SyntheticMarkerV2 extends Attribute {
    static final SyntheticMarkerV2 PROTO = new SyntheticMarkerV2((short) 0, null);

    final short kindId;
    final byte[] versionHash;

    public SyntheticMarkerV2(short kindId, byte[] versionHash) {
      super("com.android.tools.r8.SynthesizedClassV2");
      this.versionHash = versionHash;
      this.kindId = kindId;
    }

    @Override
    protected Attribute read(
        ClassReader classReader,
        int offset,
        int length,
        char[] charBuffer,
        int codeAttributeOffset,
        Label[] labels) {
      short kindId = classReader.readShort(offset);
      offset += 2;
      short versionLength = classReader.readShort(offset);
      offset += 2;
      byte[] versionBytes = new byte[versionLength];
      for (int i = 0; i < versionLength; i++) {
        versionBytes[i] = (byte) classReader.readByte(offset++);
      }
      return new SyntheticMarkerV2(kindId, versionBytes);
    }

    @Override
    protected ByteVector write(
        ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
      ByteVector byteVector = new ByteVector();
      byteVector.putShort(kindId);
      byteVector.putShort(versionHash.length);
      byteVector.putByteArray(versionHash, 0, versionHash.length);
      return byteVector;
    }
  }

  private static List<Attribute> readAttributes(byte[] bytes) {
    List<Attribute> attributes = new ArrayList<>();
    ClassReader reader = new ClassReader(bytes);
    reader.accept(
        new ClassVisitor(InternalOptions.ASM_VERSION) {
          @Override
          public void visit(
              int version,
              int access,
              String name,
              String signature,
              String superName,
              String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
          }

          @Override
          public void visitAttribute(Attribute attribute) {
            attributes.add(attribute);
          }
        },
        new Attribute[] {SyntheticMarkerV1.PROTO, SyntheticMarkerV2.PROTO},
        ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
    return attributes;
  }

  private byte[] getTestClassWithMarker(Attribute marker) throws IOException {
    return transformer(TestClass.class)
        .addClassTransformer(
            new ClassTransformer() {
              @Override
              public void visit(
                  int version,
                  int access,
                  String name,
                  String signature,
                  String superName,
                  String[] interfaces) {
                super.visit(version, access, name, signature, superName, interfaces);
                super.visitAttribute(marker);
              }
            })
        .transform();
  }

  /** Test that reads the correct marker from a compilation unit and fails if then manipulated. */
  @Test
  public void testInvalidMarkerFailsCompilation() throws Exception {
    Box<SyntheticMarkerV2> currentCompilerMarker = new Box<>();
    testForD8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .setMinApi(parameters)
        .setIntermediate(true)
        .setProgramConsumer(
            new ClassFileConsumer() {
              private final List<Attribute> attributes = new ArrayList<>();

              @Override
              public void accept(ByteDataView data, String descriptor, DiagnosticsHandler handler) {
                attributes.addAll(readAttributes(data.copyByteData()));
              }

              @Override
              public void finished(DiagnosticsHandler handler) {
                assertEquals(1, attributes.size());
                assertEquals(SyntheticMarkerV2.PROTO.type, attributes.get(0).type);
                currentCompilerMarker.set((SyntheticMarkerV2) attributes.get(0));
              }
            })
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);

    // Test that if a "current" marker with invalid content is given to the compiler it will
    // cause a failure. This test ensures that we can witness the markers being ignored in the
    // tests below.
    D8TestBuilder builder =
        testForD8(parameters.getBackend())
            .setMinApi(parameters)
            .addProgramClassFileData(
                getTestClassWithMarker(
                    new SyntheticMarkerV2(
                        Short.MAX_VALUE, currentCompilerMarker.get().versionHash)));
    assertThrows(CompilationFailedException.class, builder::compile);
  }

  @Test
  public void testIgnoreV1Markers() throws Exception {
    // Test that inputs with a legacy marker will be ignored.
    // We do so by injecting an old marker and put in a non-valid ID which would cause the compiler
    // to fail if it was read.
    testForD8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClassFileData(getTestClassWithMarker(new SyntheticMarkerV1(Short.MAX_VALUE)))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testIgnorePreviousV2Markers() throws Exception {
    // Test that inputs with a V2 marker from a previous compiler version are ignored.
    // We do so by injecting an old marker and put in a non-valid ID which would cause the compiler
    // to fail if it was read.
    testForD8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClassFileData(
            getTestClassWithMarker(new SyntheticMarkerV2(Short.MAX_VALUE, new byte[0])))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  public static class TestClass {

    public static void run(Runnable r) {
      r.run();
    }

    public static void main(String[] args) {
      run(() -> System.out.println("Hello, world"));
    }
  }
}
