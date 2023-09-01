package test.books;

import mixin.MixinTransformer;
import test.book.Book;

import java.util.ArrayList;
import java.util.List;

public class Books {

    public List<Book> books = new ArrayList<>();

    public Books() {
        books.add(new Book("myBook", 2000, 100));
        books.add(new Book("myBook", 2001, 101));
        books.add(new Book("myBook2", 2001, 100));
    }

    public List<Book> find(String name) {
        return books.stream().filter(book -> book.name.equals(name)).toList();
    }
}
