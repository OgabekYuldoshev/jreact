package app;

import java.util.ArrayList;
import java.util.List;

/** Row data, generated the same way as in contenders/harness.js so every contender renders identical rows. */
final class Data {
    private Data() {}

    record Item(int id, String label) {}

    private static final String[] ADJECTIVES = {"pretty", "large", "big", "small", "tall", "short", "long", "handsome",
            "plain", "quaint", "clean", "elegant", "easy", "angry", "crazy", "helpful", "mushy", "odd", "unsightly",
            "adorable", "important", "inexpensive", "cheap", "expensive", "fancy"};
    private static final String[] COLOURS = {"red", "yellow", "blue", "green", "pink", "brown", "purple", "brown",
            "white", "black", "orange"};
    private static final String[] NOUNS = {"table", "chair", "house", "bbq", "desk", "car", "pony", "cookie", "sandwich",
            "burger", "pizza", "mouse", "keyboard"};

    private static int nextId = 1;
    private static int seed = 12345;

    private static int random(int bound) {
        seed = seed * 1103515245 + 12345;
        return ((seed >>> 16) & 0x7fff) % bound;
    }

    static List<Item> build(int count) {
        List<Item> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(new Item(nextId++, ADJECTIVES[random(ADJECTIVES.length)] + " " + COLOURS[random(COLOURS.length)]
                    + " " + NOUNS[random(NOUNS.length)]));
        }
        return items;
    }
}
