/*
 * Copyright (c) 2013, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *  
 *   or (per the licensee's choosing)
 *  
 * under the terms of the GNU Lesser General Public License version 3.0
 * as published by the Free Software Foundation.
 */
package co.paralleluniverse.common.benchmark;

import java.util.concurrent.ConcurrentMap;
import jsr166e.ConcurrentHashMapV8;
import org.HdrHistogram.AbstractHistogram;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramData;

/**
 *
 * @author pron
 */
public class MultiThreadedHistogram {
    private final AbstractHistogram mainHistogram;
    private final ConcurrentMap<Thread, AbstractHistogram> hs;
    private final long highestTrackableValue;
    private final int numberOfSignificantValueDigits;

    /**
     * Construct a Histogram given the Highest value to be tracked and a number of significant decimal digits
     *
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     * integer that is >= 2.
     * @param numberOfSignificantValueDigits The number of significant decimal digits to which the histogram will
     * maintain value resolution and separation. Must be a non-negative
     * integer between 0 and 5.
     */
    public MultiThreadedHistogram(long highestTrackableValue, int numberOfSignificantValueDigits) {
        this.highestTrackableValue = highestTrackableValue;
        this.numberOfSignificantValueDigits = numberOfSignificantValueDigits;
        this.mainHistogram = newHistogram();
        this.hs = new ConcurrentHashMapV8<>();
    }

    public void combine() {
        mainHistogram.reset();
        for (AbstractHistogram h : hs.values())
            mainHistogram.add(h);
    }

    private AbstractHistogram get() {
        Thread thread = Thread.currentThread();
        AbstractHistogram h = hs.get(thread);
        if (h == null) {
            h = newHistogram();
            hs.put(thread, h);
        }
        return h;
    }

    private AbstractHistogram newHistogram() {
        return new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
    }

    /**
     * Record a value in the histogram.
     * <p>
     * To compensate for the loss of sampled values when a recorded value is larger than the expected
     * interval between value samples, Histogram will auto-generate an additional series of decreasingly-smaller
     * (down to the expectedIntervalBetweenValueSamples) value records. In addition to the default, "corrected"
     * histogram representation containing potential auto-generated value records, the Histogram keeps track of
     * "raw" histogram data containing no such corrections. This data set is available via the
     * <b><code>getRawData</code></b> method.
     * <p>
     * See notes in the description of the Histogram calls for an illustration of why this corrective behavior is
     * important.
     *
     * @param value The value to record
     * @param expectedIntervalBetweenValueSamples If expectedIntervalBetweenValueSamples is larger than 0, add
     * auto-generated value records as appropriate if value is larger
     * than expectedIntervalBetweenValueSamples
     * @throws ArrayIndexOutOfBoundsException
     */
    public void recordValue(long value, long expectedIntervalBetweenValueSamples) throws ArrayIndexOutOfBoundsException {
        get().recordValue(value, expectedIntervalBetweenValueSamples);
    }

    /**
     * Record a value in the histogram
     *
     * @param value The value to be recorded
     * @throws ArrayIndexOutOfBoundsException
     */
    public void recordValue(long value) throws ArrayIndexOutOfBoundsException {
        get().recordValue(value);
    }

    /**
     * Reset the contents and stats of this histogram
     */
    public void reset() {
        for (AbstractHistogram h : hs.values())
            h.reset();
        mainHistogram.reset();
    }

    /**
     * Provide access to the histogram's data set.
     *
     * @return a {@link HistogramData} that can be used to query stats and iterate through the default (corrected)
     * data set.
     */
    public HistogramData getHistogramData() {
        combine();
        return mainHistogram.getHistogramData();
    }

    /**
     * Determine if this histogram had any of it's value counts overflow.
     * Since counts are kept in fixed integer form with potentially limited range (e.g. int and short), a
     * specific value range count could potentially overflow, leading to an inaccurate and misleading histogram
     * representation. This method accurately determines whether or not an overflow condition has happened in an
     * IntHistogram or ShortHistogram.
     *
     * @return True if this histogram has had a count value overflow.
     */
    public boolean hasOverflowed() {
        for (AbstractHistogram h : hs.values()) {
            if (h.hasOverflowed())
                return true;
        }
        return false;
    }

    /**
     * get the configured numberOfSignificantValueDigits
     *
     * @return numberOfSignificantValueDigits
     */
    public int getNumberOfSignificantValueDigits() {
        return numberOfSignificantValueDigits;
    }

    /**
     * get the configured highestTrackableValue
     *
     * @return highestTrackableValue
     */
    public long getHighestTrackableValue() {
        return highestTrackableValue;
    }

    /**
     * Get the size (in value units) of the range of values that are equivalent to the given value within the
     * histogram's resolution. Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value The given value
     * @return The lowest value that is equivalent to the given value within the histogram's resolution.
     */
    public long sizeOfEquivalentValueRange(long value) {
        return mainHistogram.sizeOfEquivalentValueRange(value);
    }

    /**
     * Get the lowest value that is equivalent to the given value within the histogram's resolution.
     * Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value The given value
     * @return The lowest value that is equivalent to the given value within the histogram's resolution.
     */
    public long lowestEquivalentValue(long value) {
        return mainHistogram.lowestEquivalentValue(value);
    }

    /**
     * Get the highest value that is equivalent to the given value within the histogram's resolution.
     * Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value The given value
     * @return The highest value that is equivalent to the given value within the histogram's resolution.
     */
    public long highestEquivalentValue(long value) {
        return mainHistogram.highestEquivalentValue(value);
    }

    /**
     * Get a value that lies in the middle (rounded up) of the range of values equivalent the given value.
     * Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value The given value
     * @return The value lies in the middle (rounded up) of the range of values equivalent the given value.
     */
    public long medianEquivalentValue(long value) {
        return mainHistogram.medianEquivalentValue(value);
    }

    /**
     * Get the next value that is not equivalent to the given value within the histogram's resolution.
     * Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value The given value
     * @return The next value that is not equivalent to the given value within the histogram's resolution.
     */
    public long nextNonEquivalentValue(long value) {
        return mainHistogram.nextNonEquivalentValue(value);
    }

    /**
     * Determine if two values are equivalent with the histogram's resolution.
     * Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value1 first value to compare
     * @param value2 second value to compare
     * @return True if values are equivalent with the histogram's resolution.
     */
    public boolean valuesAreEquivalent(long value1, long value2) {
        return (lowestEquivalentValue(value1) == lowestEquivalentValue(value2));
    }
}
