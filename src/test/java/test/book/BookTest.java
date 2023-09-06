package test.book;

import jdk.jfr.Label;
import mixin.ClassInjector;
import org.junit.jupiter.api.Test;
import reflection.FieldReflection;
import reflection.MethodReflection;

import static org.junit.jupiter.api.Assertions.*;

public class BookTest {

    @Test
    public void test() {
        fieldAnnotationsWithout();
        getWithoutMixin();
        methodAnnotationsWithout();
        shadowPrivateFieldWithout();
        shadowPrivateStaticFieldWithout();

        ClassInjector.addClasses(BookMixin.class);

        fieldAnnotationsWith();
        getWithMixin();
        methodAnnotationsWith();
        fieldSameAnnotationsWith();
        shadowPrivateFieldWith();
        fieldWithoutAnnotationsWith();
        shadowPrivateStaticFieldWith();
    }



    public void shadowPrivateStaticFieldWithout() {
        assertEquals(BookMixin.getStaticTestField(null), 0);
    }

    public void shadowPrivateStaticFieldWith() {
        assertEquals(BookMixin.getStaticTestField(null), 1.1);
        BookMixin.setStaticTestField(null, 1.2);
        assertEquals(BookMixin.getStaticTestField(null), 1.2);
    }

    public void shadowPrivateFieldWithout() {
        Book book = new Book("x", 1999, 100.1);

        assertEquals(BookMixin.getPublishedYear(book), 0);
    }

    public void shadowPrivateFieldWith() {
        Book book = new Book("x", 1999, 100.1);

        assertEquals(BookMixin.getPublishedYear(book), 1999);
        BookMixin.setPublishedYear(book, 2000);
        assertEquals(BookMixin.getPublishedYear(book), 2000);
    }

    public void fieldSameAnnotationsWith() {
        assertEquals(FieldReflection.getField(Book.class, "cost").getDeclaredAnnotation(Label.class).value(), "LabelCostAnnotation");
        assertEquals(FieldReflection.getField(Book.class, "cost").getDeclaredAnnotations().length, 1);
    }

    public void methodAnnotationsWithout() {
        assertEquals(MethodReflection.getMethod(Book.class, "get").getDeclaredAnnotation(Label.class).value(), "LabelBookAnnotation");
    }

    public void methodAnnotationsWith() {
        assertEquals(MethodReflection.getMethod(Book.class, "get").getDeclaredAnnotation(Label.class).value(), "LabelBookAnnotationMixin");
        assertEquals(MethodReflection.getMethod(Book.class, "get").getDeclaredAnnotations().length, 1);
    }

    public void fieldAnnotationsWithout() {
        assertEquals(FieldReflection.getField(Book.class, "name").getDeclaredAnnotation(Label.class).value(), "LabelNameAnnotation");
    }

    public void fieldAnnotationsWith() {
        assertEquals(FieldReflection.getField(Book.class, "name").getDeclaredAnnotation(Label.class).value(), "LabelNameAnnotationMixin");
        assertEquals(FieldReflection.getField(Book.class, "name").getDeclaredAnnotations().length, 1);
    }

    public void fieldWithoutAnnotationsWith() {
        assertEquals(FieldReflection.getField(Book.class, "publishedYear").getDeclaredAnnotations().length, 0);
    }

    public void getWithoutMixin() {
        Book book = new Book("x", 1999, 100.1);

        assertEquals(book.get(), book);
    }

    public void getWithMixin() {
        Book book = new Book("x", 1999, 100.1);

        assertEquals(book.get(), BookMixin.book);
    }
}