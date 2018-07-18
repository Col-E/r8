// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.D8Command.Builder;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.origin.EmbeddedOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Base64;
import java.util.Set;
import org.junit.Test;

public class Regress37740372 extends SmaliTestBase {

  /*
    Binary class file data for a java.lang.Object stub implementation. Runnong javap -c on the
    bytecode produces the following output:

    Compiled from "Object.java"
    public class java.lang.Object {
      public java.lang.Object();
        Code:
           0: new           #1                  // class java/lang/RuntimeException
           3: dup
           4: ldc           #2                  // String Stub!
           6: invokespecial #3                  // Method java/lang/RuntimeException."<init>":(Ljava/lang/String;)V
           9: athrow

      protected java.lang.Object clone() throws java.lang.CloneNotSupportedException;
        Code:
           0: new           #1                  // class java/lang/RuntimeException
           3: dup
           4: ldc           #2                  // String Stub!
           6: invokespecial #3                  // Method java/lang/RuntimeException."<init>":(Ljava/lang/String;)V
           9: athrow

      public boolean equals(java.lang.Object);
        Code:
           0: new           #1                  // class java/lang/RuntimeException
           3: dup
           4: ldc           #2                  // String Stub!
           6: invokespecial #3                  // Method java/lang/RuntimeException."<init>":(Ljava/lang/String;)V
           9: athrow

      protected void finalize() throws java.lang.Throwable;
        Code:
           0: new           #1                  // class java/lang/RuntimeException
           3: dup
           4: ldc           #2                  // String Stub!
           6: invokespecial #3                  // Method java/lang/RuntimeException."<init>":(Ljava/lang/String;)V
           9: athrow

      public final native java.lang.Class<?> getClass();

      public native int hashCode();

      public final native void notify();

      public final native void notifyAll();

      public java.lang.String toString();
        Code:
           0: new           #1                  // class java/lang/RuntimeException
           3: dup
           4: ldc           #2                  // String Stub!
           6: invokespecial #3                  // Method java/lang/RuntimeException."<init>":(Ljava/lang/String;)V
           9: athrow

      public final void wait() throws java.lang.InterruptedException;
        Code:
           0: new           #1                  // class java/lang/RuntimeException
           3: dup
           4: ldc           #2                  // String Stub!
           6: invokespecial #3                  // Method java/lang/RuntimeException."<init>":(Ljava/lang/String;)V
           9: athrow

      public final void wait(long) throws java.lang.InterruptedException;
        Code:
           0: new           #1                  // class java/lang/RuntimeException
           3: dup
           4: ldc           #2                  // String Stub!
           6: invokespecial #3                  // Method java/lang/RuntimeException."<init>":(Ljava/lang/String;)V
           9: athrow

      public final native void wait(long, int) throws java.lang.InterruptedException;
    }
  */
  String javaLangObjectClassFile =
      "yv66vgAAADEALwcAJwgAKAoAAQApBwAqAQAGPGluaXQ+AQADKClWAQAEQ29kZQEAD0xpbmVOdW1i" +
          "ZXJUYWJsZQEAEkxvY2FsVmFyaWFibGVUYWJsZQEABHRoaXMBABJMamF2YS9sYW5nL09iamVjdDsB" +
          "AAVjbG9uZQEAFCgpTGphdmEvbGFuZy9PYmplY3Q7AQAKRXhjZXB0aW9ucwcAKwEABmVxdWFscwEA" +
          "FShMamF2YS9sYW5nL09iamVjdDspWgEAAW8BAAhmaW5hbGl6ZQcALAEACGdldENsYXNzAQATKClM" +
          "amF2YS9sYW5nL0NsYXNzOwEACVNpZ25hdHVyZQEAFigpTGphdmEvbGFuZy9DbGFzczwqPjsBAAho" +
          "YXNoQ29kZQEAAygpSQEABm5vdGlmeQEACW5vdGlmeUFsbAEACHRvU3RyaW5nAQAUKClMamF2YS9s" +
          "YW5nL1N0cmluZzsBAAR3YWl0BwAtAQAEKEopVgEABm1pbGxpcwEAAUoBAAUoSkkpVgEAClNvdXJj" +
          "ZUZpbGUBAAtPYmplY3QuamF2YQEAGmphdmEvbGFuZy9SdW50aW1lRXhjZXB0aW9uAQAFU3R1YiEM" +
          "AAUALgEAEGphdmEvbGFuZy9PYmplY3QBACRqYXZhL2xhbmcvQ2xvbmVOb3RTdXBwb3J0ZWRFeGNl" +
          "cHRpb24BABNqYXZhL2xhbmcvVGhyb3dhYmxlAQAeamF2YS9sYW5nL0ludGVycnVwdGVkRXhjZXB0" +
          "aW9uAQAVKExqYXZhL2xhbmcvU3RyaW5nOylWACEABAAAAAAAAAAMAAEABQAGAAEABwAAADQAAwAB" +
          "AAAACrsAAVkSArcAA78AAAACAAgAAAAGAAEAAAAEAAkAAAAMAAEAAAAKAAoACwAAAAQADAANAAIA" +
          "BwAAADQAAwABAAAACrsAAVkSArcAA78AAAACAAgAAAAGAAEAAAAFAAkAAAAMAAEAAAAKAAoACwAA" +
          "AA4AAAAEAAEADwABABAAEQABAAcAAAA+AAMAAgAAAAq7AAFZEgK3AAO/AAAAAgAIAAAABgABAAAA" +
          "BgAJAAAAFgACAAAACgAKAAsAAAAAAAoAEgALAAEABAATAAYAAgAHAAAANAADAAEAAAAKuwABWRIC" +
          "twADvwAAAAIACAAAAAYAAQAAAAcACQAAAAwAAQAAAAoACgALAAAADgAAAAQAAQAUAREAFQAWAAEA" +
          "FwAAAAIAGAEBABkAGgAAAREAGwAGAAABEQAcAAYAAAABAB0AHgABAAcAAAA0AAMAAQAAAAq7AAFZ" +
          "EgK3AAO/AAAAAgAIAAAABgABAAAADAAJAAAADAABAAAACgAKAAsAAAARAB8ABgACAAcAAAA0AAMA" +
          "AQAAAAq7AAFZEgK3AAO/AAAAAgAIAAAABgABAAAADQAJAAAADAABAAAACgAKAAsAAAAOAAAABAAB" +
          "ACAAEQAfACEAAgAHAAAAPgADAAMAAAAKuwABWRICtwADvwAAAAIACAAAAAYAAQAAAA4ACQAAABYA" +
          "AgAAAAoACgALAAAAAAAKACIAIwABAA4AAAAEAAEAIAERAB8AJAABAA4AAAAEAAEAIAABACUAAAAC" +
          "ACY=";

  private void assertIsJavaLangObjet(ClassSubject clazz) {
    assertTrue(clazz.getOriginalDescriptor().equals("Ljava/lang/Object;"));
    assertNull(clazz.getDexClass().superType);
  }

  private void checkApplicationOnlyHasJavaLangObject(AndroidApp app) throws Throwable {
    CodeInspector inspector = new CodeInspector(app);
    inspector.forAllClasses(this::assertIsJavaLangObjet);
  }

  static class MemoryConsumer extends DexIndexedConsumer.ForwardingConsumer {
    byte[] data;

    public MemoryConsumer() {
      super(null);
    }

    @Override
    public void accept(
        int fileIndex, byte[] data, Set<String> descriptors, DiagnosticsHandler handler) {
      assertEquals(0, fileIndex);
      this.data = data;
    }
  }

  @Test
  public void test() throws Throwable {
    // Build an application with the java.lang.Object stub from a class file.
    MemoryConsumer consumer = new MemoryConsumer();
    AndroidAppConsumers appConsumer = new AndroidAppConsumers();
    D8.run(
        D8Command.builder()
            .setProgramConsumer(appConsumer.wrapProgramConsumer(consumer))
            .addClassProgramData(
                Base64.getDecoder().decode(javaLangObjectClassFile), EmbeddedOrigin.INSTANCE)
            .build());
    checkApplicationOnlyHasJavaLangObject(appConsumer.build());

    // Build an application with the java.lang.Object stub from a dex file.
    Builder builder = D8Command.builder();
    builder.addDexProgramData(consumer.data, Origin.unknown());
    appConsumer = new AndroidAppConsumers(builder);
    D8.run(builder.build());
    checkApplicationOnlyHasJavaLangObject(appConsumer.build());
  }
}
