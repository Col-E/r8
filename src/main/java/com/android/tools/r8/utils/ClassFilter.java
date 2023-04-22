package com.android.tools.r8.utils;

import com.android.tools.r8.graph.ProgramClass;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.Collection;

public interface ClassFilter {
	ClassFilter PASS_ALL = new ClassFilter() {
		@Override
		public boolean test(@NotNull ProgramClass cls) {
			return true;
		}

		@Override
		public String toString() {
			return "Pass: All";
		}
	};
	ClassFilter PASS_NONE = new ClassFilter() {
		@Override
		public boolean test(@NotNull ProgramClass cls) {
			return false;
		}

		@Override
		public String toString() {
			return "Pass: None";
		}
	};

	/**
	 * @param internalName
	 * 		Internal class name.
	 *
	 * @return Filter passing only the given class.
	 */
	@Nonnull
	static ClassFilter forType(@Nonnull String internalName) {
		return new ClassFilter() {
			@Override
			public boolean test(@NotNull ProgramClass cls) {
				return internalName.equals(cls.getType().getTypeName());
			}

			@Override
			public String toString() {
				return "Pass: " + internalName;
			}
		};
	}

	/**
	 * @param internalNames
	 * 		Collection of internal class names.
	 *
	 * @return Filter passing only the given names.
	 */
	@Nonnull
	static ClassFilter forTypes(@Nonnull Collection<String> internalNames) {
		return new ClassFilter() {
			@Override
			public boolean test(@NotNull ProgramClass cls) {
				return internalNames.contains(cls.getType().getTypeName());
			}

			@Override
			public String toString() {
				return "Pass: Collection[" + internalNames.size() + "]";
			}
		};
	}

	/**
	 * @return Inversion of the current filter.
	 */
	@Nonnull
	default ClassFilter invert() {
		return cls -> !test(cls);
	}

	boolean test(@Nonnull ProgramClass cls);
}
