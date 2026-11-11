package app;

import java.util.List;

/** Question and answer pairs of the FAQ section. */
final class Faqs {
    private Faqs() {}

    record Entry(String question, String answer) {}

    static final List<Entry> ALL = List.of(
        new Entry("Can I try Flowly before paying?", "Yes. Every plan starts with a 14 day free trial, no credit card needed."),
        new Entry("What happens when I outgrow a plan?", "You can upgrade at any time. We prorate the difference, so you only pay for what you use."),
        new Entry("Do you offer discounts for non-profits?", "We give 50% off to registered non-profits and open source projects. Write to us and we will set it up."),
        new Entry("Where is my data stored?", "In EU and US regions of your choice, encrypted at rest and in transit.")
    );
}
