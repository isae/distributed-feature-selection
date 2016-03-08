package ru.ifmo.ctddev.isaev;

import java.util.Collections;
import java.util.List;


/**
 * @author iisaev
 */
public class Feature {
    private final String name;

    private final List<Integer> values;

    public Feature(String name, List<Integer> values) {
        this.name = name;
        this.values = Collections.unmodifiableList(values);
    }

    public Feature(List<Integer> values) {
        this("", values);
    }

    public List<Integer> getValues() {
        return values;
    }

    public String getName() {
        return name;
    }
}
