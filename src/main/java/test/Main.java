package test;

import mixin.ClassInjector;

public class Main {
    public static void main(String[] args) {
        Print printer = new Print();
        ClassInjector.addClasses(PrintMixin.class);
        printer.print();
    }
}
