package com.android.tools.r8.utils.structural;

import javax.annotation.Nonnull;
import java.util.function.IntFunction;

/**
 * Outline of a type that can create a copy of itself and its contained data.
 */
public interface Copyable<T extends Copyable<T>> {
	@SuppressWarnings("all")
	static <T extends Copyable<T>> T[] copyArray(T[] input, IntFunction<T[]> arrayProvider) {
		T[] copy = arrayProvider.apply(input.length);
		for (int i = 0; i < input.length; i++) {
			Copyable<T> k = input[i];
			copy[i] = k == null ? null : k.copy();
		}
		return copy;
	}

	/**
	 * @return Copy of self that is equal in value.
	 * May be self if the contents of this type do not have references needing to be updated.
	 */
	@Nonnull
	T copy();
}
