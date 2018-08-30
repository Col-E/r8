;; Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
;; for details. All rights reserved. Use of this source code is governed by a
;; BSD-style license that can be found in the LICENSE file.
.class Test
.super java/lang/Object
.method test()Z
.limit stack 10
.limit locals 21
.var 8 is foobar F from L27 to L68
.catch all from L1 to L5 using L10
.catch all from L6 to L9 using L10
.catch all from L10 to L11 using L10
L0:
  aload 0
  getfield com/example/Foo/bar Lcom/example/Baz;
  astore 1
  aload 1
  monitorenter
L1:
  nop
L2:
  aload 0
  getfield com/example/Foo/bar Lcom/example/Baz;
  invokevirtual com.example.Baz.valid()Z
  ifne L6
  aload 0
  getfield com/example/Foo/bar Lcom/example/Baz;
  invokevirtual com.example.Baz.parse()Z
  ifne L6
L3:
  ldc 0
L4:
  istore 4
L5:
  aload 1
  monitorexit
  iload 4
  ireturn
L6:
  nop
L7:
  getstatic kotlin/Unit/INSTANCE Lkotlin/Unit;
L8:
  astore 3
L9:
  aload 1
  monitorexit
  goto L12
L10:
  astore 3
L11:
  aload 1
  monitorexit
  aload 3
  athrow
L12:
  aload 0
  getfield com/example/Foo/bar Lcom/example/Baz;
  invokevirtual com.example.Baz.getLong()J
  lstore 1
L13:
  aload 0
  getfield com/example/Foo/bar Lcom/example/Baz;
  invokevirtual com.example.Baz.getLength()I
  istore 3
L14:
  aload 0
  iload 3
  ldc 4
  imul
  newarray float
  putfield com/example/Foo/rects [F
L15:
  iload 3
  ifne L17
L16:
  ldc 1
  ireturn
L17:
  ldc 127
  istore 4
L18:
  ldc 0
  istore 5
L19:
  ldc 0
  istore 6
  iload 3
  istore 7
L20:
  iload 6
  iload 7
  if_icmpge L25
L21:
  aload 0
  getfield com/example/Foo/bar Lcom/example/Baz;
  iload 6
  invokevirtual com.example.Baz.getInt(I)I
  istore 8
  iload 4
  iload 8
  invokestatic java.lang.Math.min(II)I
  istore 4
L22:
  aload 0
  getfield com/example/Foo/bar Lcom/example/Baz;
  iload 6
  invokevirtual com.example.Baz.getInt(I)I
  istore 8
  iload 5
  iload 8
  invokestatic java.lang.Math.max(II)I
  istore 5
L23:
  iinc 6 1
L24:
  goto L20
L25:
  aload 0
  getfield com/example/Foo/height F
  ldc 2
  i2f
  aload 0
  getfield com/example/Foo/margin F
  fmul
  fsub
  fstore 6
L26:
  iload 5
  iload 4
  isub
  ldc 1
  iadd
  istore 7
L27:
  aload 0
  getfield com/example/Foo/rMinGap F
  aload 0
  getfield com/example/Foo/rMaxGap F
  fcmpg
  ifge L28
  ldc 1
  goto L29
L28:
  ldc 0
L29:
  istore 11
  iload 11
  ifne L30
  ldc "Failed requirement."
  astore 12
  new java/lang/IllegalArgumentException
  dup
  aload 12
  invokevirtual java.lang.Object.toString()Ljava/lang/String;
  invokespecial java.lang.IllegalArgumentException.<init>(Ljava/lang/String;)V
  checkcast java/lang/Throwable
  athrow
L30:
  aload 0
  getfield com/example/Foo/rMinHeight F
  aload 0
  getfield com/example/Foo/rMaxHeight F
  fcmpg
  ifge L31
  ldc 1
  goto L32
L31:
  ldc 0
L32:
  istore 11
  iload 11
  ifne L33
  ldc "Failed requirement."
  astore 12
  new java/lang/IllegalArgumentException
  dup
  aload 12
  invokevirtual java.lang.Object.toString()Ljava/lang/String;
  invokespecial java.lang.IllegalArgumentException.<init>(Ljava/lang/String;)V
  checkcast java/lang/Throwable
  athrow
L33:
  iload 7
  ldc 1
  if_icmpgt L37
L34:
  aload 0
  getfield com/example/Foo/rMaxHeight F
  fstore 9
L35:
  fload 9
  aload 0
  getfield com/example/Foo/rMaxGap F
  fadd
  fstore 8
L36:
  aload 0
  getfield com/example/Foo/margin F
  fload 6
  fload 9
  fsub
  ldc 2
  i2f
  fdiv
  fadd
  fstore 10
  goto L54
L37:
  aload 0
  getfield com/example/Foo/rMinHeight F
  iload 7
  i2f
  fmul
  aload 0
  getfield com/example/Foo/rMinGap F
  iload 7
  ldc 1
  isub
  i2f
  fmul
  fadd
  fload 6
  fcmpl
  ifle L41
L38:
  aload 0
  getfield com/example/Foo/rMinHeight F
  fstore 9
L39:
  fload 6
  fload 9
  fsub
  iload 7
  ldc 1
  isub
  i2f
  fdiv
  fstore 8
L40:
  aload 0
  getfield com/example/Foo/margin F
  fstore 10
  goto L54
L41:
  aload 0
  getfield com/example/Foo/rMaxHeight F
  iload 7
  i2f
  fmul
  aload 0
  getfield com/example/Foo/rMinGap F
  iload 7
  ldc 1
  isub
  i2f
  fmul
  fadd
  fload 6
  fcmpl
  ifle L45
L42:
  fload 6
  aload 0
  getfield com/example/Foo/rMinGap F
  iload 7
  ldc 1
  isub
  i2f
  fmul
  fsub
  iload 7
  i2f
  fdiv
  fstore 9
L43:
  fload 9
  aload 0
  getfield com/example/Foo/rMinGap F
  fadd
  fstore 8
L44:
  aload 0
  getfield com/example/Foo/margin F
  fstore 10
  goto L54
L45:
  aload 0
  getfield com/example/Foo/rMaxHeight F
  iload 7
  i2f
  fmul
  aload 0
  getfield com/example/Foo/rMaxGap F
  iload 7
  ldc 1
  isub
  i2f
  fmul
  fadd
  fload 6
  fcmpl
  ifle L51
L46:
  aload 0
  getfield com/example/Foo/rMaxHeight F
  fstore 9
L47:
  fload 6
  aload 0
  getfield com/example/Foo/rMaxHeight F
  iload 7
  i2f
  fmul
  fsub
  iload 7
  ldc 1
  isub
  i2f
  fdiv
  fstore 11
L48:
  fload 9
  fload 11
  fadd
  fstore 8
L49:
  aload 0
  getfield com/example/Foo/margin F
  fstore 10
L50:
  goto L54
L51:
  aload 0
  getfield com/example/Foo/rMaxHeight F
  fstore 9
L52:
  fload 9
  aload 0
  getfield com/example/Foo/rMaxGap F
  fadd
  fstore 8
L53:
  aload 0
  getfield com/example/Foo/margin F
  fload 6
  fload 9
  iload 7
  i2f
  fmul
  aload 0
  getfield com/example/Foo/rMaxGap F
  iload 7
  ldc 1
  isub
  i2f
  fmul
  fadd
  fsub
  ldc 2
  i2f
  fdiv
  fadd
  fstore 10
L54:
  aload 0
  getfield com/example/Foo/converter Lcom/example/FooBar;
  invokevirtual com.example.FooBar.getFloat()F
  fstore 11
L55:
  ldc 0
  istore 12
  iload 3
  istore 13
L56:
  iload 12
  iload 13
  if_icmpge L67
L57:
  iload 12
  ldc 4
  imul
  istore 14
L58:
  aload 0
  getfield com/example/Foo/rects [F
  iload 14
  ldc 0
  iadd
  fload 11
  aload 0
  aload 0
  getfield com/example/Foo/bar Lcom/example/Baz;
  iload 12
  invokevirtual com.example.Baz.getLong(I)J
  lload 1
  aload 0
  getfield com/example/Foo/bpm I
  invokespecial com.example.FooBar.getDouble(JJI)D
  d2f
  fmul
  fastore
L59:
  aload 0
  getfield com/example/Foo/rects [F
L60:
  iload 14
  ldc 2
  iadd
L61:
  aload 0
  getfield com/example/Foo/rects [F
  iload 14
  ldc 0
  iadd
  faload
  ldc 1.0
  fstore 15
  fload 11
  aload 0
  aload 0
  getfield com/example/Foo/bar Lcom/example/Baz;
  iload 12
  invokevirtual com.example.Baz.getLong(I)J
  lload 1
  aload 0
  getfield com/example/Foo/bpm I
  invokespecial com.example.FooBar.getDouble(JJI)D
  d2f
  fmul
  fstore 16
  fstore 19
  istore 18
  astore 17
  fload 15
  fload 16
  invokestatic java.lang.Math.max(FF)F
  fstore 20
  aload 17
  iload 18
  fload 19
  fload 20
L62:
  fadd
  fastore
L63:
  aload 0
  getfield com/example/Foo/rects [F
  iload 14
  ldc 1
  iadd
  fload 10
  iload 5
  aload 0
  getfield com/example/Foo/bar Lcom/example/Baz;
  iload 12
  invokevirtual com.example.Baz.getInt(I)I
  isub
  i2f
  fload 8
  fmul
  fadd
  fastore
L64:
  aload 0
  getfield com/example/Foo/rects [F
  iload 14
  ldc 3
  iadd
  aload 0
  getfield com/example/Foo/rects [F
  iload 14
  ldc 1
  iadd
  faload
  fload 9
  fadd
  fastore
L65:
  iinc 12 1
L66:
  goto L56
L67:
  ldc 1
  ireturn
L68:
.end method