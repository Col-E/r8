package com.android.tools.r8.utils;

import java.nio.ByteOrder;

public class EndianUtils {
	public static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

	public static short reverseShort(short s) {
		if (LITTLE_ENDIAN) return Short.reverseBytes(s);
		return s;
	}

	public static int reverseInt(int i) {
		if (LITTLE_ENDIAN) return Integer.reverseBytes(i);
		return i;
	}

	public static long reverseLong(long l) {
		if (LITTLE_ENDIAN) return Long.reverseBytes(l);
		return l;
	}
}
