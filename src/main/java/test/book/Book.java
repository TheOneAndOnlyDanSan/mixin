package test.book;

public class Book {
    public final String name;
    private final int publishedYear;
    private final double cost;

    public Book(String name, int publishedYear, double cost) {
        this.name = name;
        this.publishedYear = publishedYear;
        this.cost = cost;
    }

    public Book get() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        Book book = (Book) o;

        return book.name.equals(name) && book.cost == cost && book.publishedYear == publishedYear;
    }
}
