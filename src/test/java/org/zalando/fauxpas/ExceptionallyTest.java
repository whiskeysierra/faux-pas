package org.zalando.fauxpas;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.NoRouteToHostException;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.zalando.fauxpas.FauxPas.partially;

class ExceptionallyTest {

    @Test
    void shouldReturnResult() {
        final CompletableFuture<String> original = new CompletableFuture<>();
        final CompletableFuture<String> unit = original.exceptionally(partially(throwable -> fail("Unexpected")));

        original.complete("result");

        assertThat(unit).isCompletedWithValue("result");
    }

    @Test
    void shouldCascade() {
        final CompletableFuture<String> original = new CompletableFuture<>();
        final CompletableFuture<String> unit = original.exceptionally(partially(e -> {
            throw new IllegalStateException();
        }));

        original.completeExceptionally(new IllegalArgumentException());

        final CompletionException thrown = assertThrows(CompletionException.class, unit::join);
        assertThat(thrown).getCause().isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldUseFallbackWhenExplicitlyCompletedExceptionally() {
        final CompletableFuture<String> original = new CompletableFuture<>();
        final CompletableFuture<String> unit = original.exceptionally(
                partially(fallbackIf(UnsupportedOperationException.class::isInstance)));

        original.completeExceptionally(new UnsupportedOperationException(new IOException()));

        assertThat(unit).isCompletedWithValue("fallback");
    }

    @Test
    void shouldUseFallbackWhenImplicitlyCompletedExceptionally() {
        final CompletableFuture<String> original = new CompletableFuture<>();
        final CompletableFuture<String> unit = original
                .thenApply(failWith(new CompletionException(new UnsupportedOperationException(new IOException()))))
                .exceptionally(partially(fallbackIf(UnsupportedOperationException.class::isInstance)));

        original.complete("unused");

        assertThat(unit).isCompletedWithValue("fallback");
    }

    @Test
    void shouldUseFallbackWhenImplicitlyCompletedExceptionallyWithNullCause() {
        final CompletableFuture<String> original = new CompletableFuture<>();
        final CompletableFuture<String> unit = original
                .thenApply(failWith(new CompletionException(null)))
                .exceptionally(partially(fallbackIf(UnsupportedOperationException.class::isInstance)));

        original.complete("unused");

        final CompletionException thrown = assertThrows(CompletionException.class, unit::join);
        assertThat(thrown).hasNoCause();
    }

    @Test
    void shouldNotRethrowOriginalCompletionExceptionWhenImplicitlyCompletedExceptionally() {
        final RuntimeException exception = new CompletionException(new AssertionError());
        final CompletionException thrown = shouldRethrowOriginalWhenImplicitlyCompletedExceptionally(exception, e -> {
            throw new CompletionException(e);
        });
        assertThat(thrown)
                .isNotSameAs(exception)
                .getCause().isSameAs(exception.getCause());
    }

    @Test
    void shouldRethrowOriginalRuntimeWhenImplicitlyCompletedExceptionally() {
        final RuntimeException exception = new IllegalStateException();
        final CompletionException thrown = shouldRethrowOriginalWhenImplicitlyCompletedExceptionally(exception, rethrow());
        assertThat(thrown).getCause().isSameAs(exception);
    }

    @Test
    void shouldRethrowOriginalThrowableWhenImplicitlyCompletedExceptionally() {
        final Exception exception = new IOException();
        final CompletionException thrown = shouldRethrowOriginalWhenImplicitlyCompletedExceptionally(
                new CompletionException(exception), rethrow());
        assertThat(thrown).getCause().isSameAs(exception);
    }

    private CompletionException shouldRethrowOriginalWhenImplicitlyCompletedExceptionally(
            final RuntimeException exception, final ThrowingFunction<Throwable, String, Throwable> function) {
        final CompletableFuture<String> original = new CompletableFuture<>();
        final CompletableFuture<String> unit = original
                .thenApply(failWith(exception))
                .exceptionally(partially(function));

        original.complete("unused");

        return assertThrows(CompletionException.class, unit::join);
    }

    @Test
    void shouldRethrowPackedCompletionExceptionWhenImplicitlyCompletedExceptionally() {
        final Exception exception = new CompletionException(new UnsupportedOperationException());
        final CompletionException thrown = shouldRethrowPackedWhenImplicitlyCompletedExceptionally(exception);
        assertThat(thrown).isSameAs(exception);
    }

    @Test
    void shouldRethrowPackedRuntimeExceptionWhenImplicitlyCompletedExceptionally() {
        final Exception exception = new UnsupportedOperationException();
        final CompletionException thrown = shouldRethrowPackedWhenImplicitlyCompletedExceptionally(exception);
        assertThat(thrown).getCause().isSameAs(exception);
    }

    @Test
    void shouldRethrowPackedThrowableWhenImplicitlyCompletedExceptionally() {
        final Exception exception = new IOException();
        final CompletionException thrown = shouldRethrowPackedWhenImplicitlyCompletedExceptionally(exception);
        assertThat(thrown).getCause().isSameAs(exception);
    }

    private CompletionException shouldRethrowPackedWhenImplicitlyCompletedExceptionally(final Exception exception) {
        final CompletableFuture<String> original = new CompletableFuture<>();
        final CompletableFuture<String> unit = original
                .thenApply(failWith(new NoSuchElementException()))
                .exceptionally(partially(e -> {
                    throw exception;
                }));

        original.complete("unused");

        return assertThrows(CompletionException.class, unit::join);
    }

    @Test
    void shouldRethrowPackedRuntimeExceptionWhenExplicitlyCompletedExceptionally() {
        shouldRethrowPackedWhenExplicitlyCompletedExceptionally(new IllegalStateException());
    }

    @Test
    void shouldRethrowPackedThrowableWhenExplicitlyCompletedExceptionally() {
        shouldRethrowPackedWhenExplicitlyCompletedExceptionally(new NoRouteToHostException());
    }

    private void shouldRethrowPackedWhenExplicitlyCompletedExceptionally(final Exception exception) {
        final CompletableFuture<String> original = new CompletableFuture<>();
        final CompletableFuture<String> unit = original.exceptionally(partially(rethrow()));

        original.completeExceptionally(exception);

        final CompletionException thrown = assertThrows(CompletionException.class, unit::join);
        assertThat(thrown).getCause().isSameAs(exception);
    }

    @Test
    void shouldHandleIfInstanceOf() {
        final CompletableFuture<String> original = new CompletableFuture<>();
        final CompletableFuture<String> unit = original.exceptionally(partially(
                IllegalStateException.class, e -> "foo"));

        final IllegalStateException exception = new IllegalStateException();
        original.completeExceptionally(exception);

        assertThat(unit).isCompletedWithValue("foo");
    }

    @Test
    void shouldThrowIfNotInstanceOf() {
        final CompletableFuture<String> original = new CompletableFuture<>();
        final CompletableFuture<String> unit = original.exceptionally(partially(
                IllegalArgumentException.class, e -> "foo"));

        final IllegalStateException exception = new IllegalStateException();
        original.completeExceptionally(exception);

        final CompletionException thrown = assertThrows(CompletionException.class, unit::join);
        assertThat(thrown).getCause().isSameAs(exception);
    }

    private Function<String, String> failWith(final RuntimeException e) {
        return result -> {
            throw e;
        };
    }

    private ThrowingFunction<Throwable, String, Throwable> fallbackIf(final Predicate<? super Throwable> predicate) {
        return throwable -> {
            if (predicate.test(throwable)) {
                return "fallback";
            }

            throw throwable;
        };
    }

    private ThrowingFunction<Throwable, String, Throwable> rethrow() {
        return e -> {
            throw e;
        };
    }

}
