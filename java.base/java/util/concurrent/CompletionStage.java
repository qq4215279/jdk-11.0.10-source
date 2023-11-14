/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 *
 * @date 2023/10/9 14:35
 */
public interface CompletionStage<T> {

    /** 
     *
     * @date 2023/10/9 15:08 
     * @param fn 
     * @return java.util.concurrent.CompletionStage<U>
     */
    public <U> CompletionStage<U> thenApply(Function<? super T, ? extends U> fn);

    public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn);

    public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor);

    /** 
     *
     * @date 2023/10/9 14:46 
     * @param action 
     * @return java.util.concurrent.CompletionStage<java.lang.Void>
     */
    public CompletionStage<Void> thenAccept(Consumer<? super T> action);

    public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action);

    public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor);

    /** 
     *
     * @date 2023/10/9 14:46 
     * @param action 
     * @return java.util.concurrent.CompletionStage<java.lang.Void>
     */
    public CompletionStage<Void> thenRun(Runnable action);

    public CompletionStage<Void> thenRunAsync(Runnable action);

    public CompletionStage<Void> thenRunAsync(Runnable action, Executor executor);

    /** 
     *
     * @date 2023/10/9 14:46 
     * @param other
     * @param fn 
     * @return java.util.concurrent.CompletionStage<V>
     */
    public <U, V> CompletionStage<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn);

    public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn);

    public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn, Executor executor);

    public <U> CompletionStage<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action);

    public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action);

    public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action, Executor executor);

    public CompletionStage<Void> runAfterBoth(CompletionStage<?> other, Runnable action);

    public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action);

    public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor);

    public <U> CompletionStage<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn);

    public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn);

    public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn, Executor executor);

    public CompletionStage<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action);

    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action);

    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action, Executor executor);

    public CompletionStage<Void> runAfterEither(CompletionStage<?> other, Runnable action);

    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action);

    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor);

    public <U> CompletionStage<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn);

    public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn);

    public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor);

    public <U> CompletionStage<U> handle(BiFunction<? super T, Throwable, ? extends U> fn);

    public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn);

    public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor);

    public CompletionStage<T> whenComplete(BiConsumer<? super T, ? super Throwable> action);

    public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action);

    public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor);

    public CompletionStage<T> exceptionally(Function<Throwable, ? extends T> fn);

    public CompletableFuture<T> toCompletableFuture();

}
