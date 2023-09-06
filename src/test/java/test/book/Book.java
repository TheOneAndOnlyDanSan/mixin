package test.book;

import jdk.jfr.Label;

public class Book {

    @Label("LabelNameAnnotation")
    public final String name;
    private final int publishedYear;
    @Label("LabelCostAnnotation")
    private final double cost;

    public Book(String name, int publishedYear, double cost) {
        this.name = name;
        this.publishedYear = publishedYear;
        this.cost = cost;
    }

    @Label("LabelBookAnnotation")
    public Book get() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        Book book = (Book) o;

        return book.name.equals(name) && book.cost == cost && book.publishedYear == publishedYear;
    }
}