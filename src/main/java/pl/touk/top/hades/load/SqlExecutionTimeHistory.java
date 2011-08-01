/*
 * Copyright 2011 TouK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pl.touk.top.hades.load;

import java.util.LinkedList;

/**
 * A class that maintains a history (a FIFO list) of last <code>N</code> (<code>N</code> is configurable) times of an sql
 * statement execution. The history can be updated with new execution time in {@link #updateAverage(long)} (this method
 * returns also the new {@link Average} after the update). The way the average is calculated is configured in
 * constructor {@link #SqlExecutionTimeHistory(int, boolean, boolean)}.
 * <p>
 * An execution time of <code>Long.MAX_VALUE</code> is treated specialy by this class. It is called
 * <i>infinity</i> and it means that the execution time could not actually be measured (because of an exception
 * for example).
 * <p>
 * This class also uses the notion of <i>recovery</i>. A <i>recovery</i> is a moment in the history when
 * the newest execution time before this moment is finite and the next execution time is <i>infinity</i>.
 * A moment in the history is said to be <i>after recovery</i> when at least one <i>infinity</i> is present
 * in the history before this moment but the newest execution time before this moment is finite.
 *
 * @author <a href="mailto:msk@touk.pl">Michal Sokolowski</a>
 */
class SqlExecutionTimeHistory {

    private static final long infinity = Long.MAX_VALUE;

    private long total = 0;
    private int itemsCountIncludedInAverage;

    private long totalFromLastRecovery = 0;
    private int itemsCountFromLastRecovery;

    private boolean infinitiesNotIncludedInAverageAfterRecovery;
    private boolean recoveryErasesHistoryIfInfinitiesNotIncludedInAverageAfterRecovery;

    private final LinkedList<Long> items = new LinkedList<Long>();

    private int infinitiesCount = 0;

    /**
     * Constructs and configures the history.
     *
     * @param itemsCountIncludedInAverage maximal number of elements kept in the history (maximal history size)
     * @param infinitiesNotIncludedInAverageAfterRecovery whether to include <i>infinities</i> in the average after recovery
     * @param recoveryErasesHistoryIfInfinitiesNotIncludedInAverageAfterRecovery whether to include in the average finite values before the last recovery
     */
    public SqlExecutionTimeHistory(int itemsCountIncludedInAverage,
                                   boolean infinitiesNotIncludedInAverageAfterRecovery,
                                   boolean recoveryErasesHistoryIfInfinitiesNotIncludedInAverageAfterRecovery) {
        if (itemsCountIncludedInAverage < 1) {
            throw new IllegalArgumentException("itemsCountIncludedInAverage should be greater than or equal to 1");
        }
        this.itemsCountIncludedInAverage = itemsCountIncludedInAverage;
        this.infinitiesNotIncludedInAverageAfterRecovery = infinitiesNotIncludedInAverageAfterRecovery;
        this.recoveryErasesHistoryIfInfinitiesNotIncludedInAverageAfterRecovery = recoveryErasesHistoryIfInfinitiesNotIncludedInAverageAfterRecovery;
    }

    /**
     * Adds the given execution time to the history. If maximal history size is exceeded, removes the
     * last (oldest) executin time from it. Returns the new, updated {@link Average}. If the specified execution time
     * is <i>infinity</i> then the returned average is also infinity, i.e. an average constructed like this:
     * <p>
     * <code>new {@link Average#Average(long, int, long) Average(Long.MAX_VALUE, &lt;current history size&gt;, Long.MAX_VALUE)}</code>).
     *
     * @param executionTime new execution time that should be added to the history
     * @return new, updated average
     */
    public Average updateAverage(long executionTime) {
        removeFirstItem();
        addLastItem(executionTime);
        return getAverage();
    }

    private Average getAverage() {
        if (infinitiesCount == 0) {
            return new Average(total / items.size(), items.size(), items.getLast());
        } else {
            if (itemsCountFromLastRecovery > 0 && infinitiesNotIncludedInAverageAfterRecovery) {
                if (recoveryErasesHistoryIfInfinitiesNotIncludedInAverageAfterRecovery) {
                    return new Average(totalFromLastRecovery / itemsCountFromLastRecovery, itemsCountFromLastRecovery, items.getLast());
                } else {
                    return new Average(total / (items.size()-infinitiesCount), items.size()-infinitiesCount, items.getLast());
                }
            } else {
                return new Average(Long.MAX_VALUE, items.size(), items.getLast());
            }
        }
    }

    private void removeFirstItem() {
        if (items.size() == itemsCountIncludedInAverage) {
            long removedItem = items.removeFirst();
            if (removedItem < infinity) {
                total -= removedItem;
                if (infinitiesCount == 0) {
                    totalFromLastRecovery -= removedItem;
                    itemsCountFromLastRecovery--;
                }
            } else {
                infinitiesCount--;
            }
        }
    }

    private void addLastItem(long item) {
        items.addLast(item);
        if (item < infinity) {
            total += item;
            totalFromLastRecovery += item;
            itemsCountFromLastRecovery++;
        } else {
            infinitiesCount++;
            totalFromLastRecovery = 0;
            itemsCountFromLastRecovery = 0;
        }

    }
}
