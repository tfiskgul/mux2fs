/*
MIT License

Copyright (c) 2017 Carl-Frederik Hallberg

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package se.tfiskgul.mux2fs;

import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import com.google.common.base.Throwables;

@Immutable
public interface ExceptionTranslator<T, E extends Throwable> {

	public static <T, E extends Throwable> ExceptionTranslator<T, E> of(E e) {
		return new NonTranslated<T, E>(e);
	}

	ExceptionTranslator<T, E> translate(final Class<? extends E> t, final Function<? super E, ? extends T> fn);

	T get();

	@Immutable
	public static class NonTranslated<T, E extends Throwable> implements ExceptionTranslator<T, E> {

		private final E exception;

		public NonTranslated(E e) {
			this.exception = e;
		}

		@Override
		public ExceptionTranslator<T, E> translate(final Class<? extends E> t, final Function<? super E, ? extends T> fn) {
			if (t.isAssignableFrom(exception.getClass())) {
				return new Translated<T, E>(fn.apply(exception));
			}
			return this;
		}

		@Override
		public T get() {
			Throwables.throwIfUnchecked(exception);
			throw new RuntimeException(exception);
		}
	}

	@Immutable
	public static class Translated<T, E extends Throwable> implements ExceptionTranslator<T, E> {

		private final T value;

		public Translated(T value) {
			this.value = value;
		}

		@Override
		public ExceptionTranslator<T, E> translate(Class<? extends E> t, Function<? super E, ? extends T> fn) {
			return this;
		}

		@Override
		public T get() {
			return value;
		}
	}
}
